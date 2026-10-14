package com.wonderful.launcher.ui

import android.app.Application
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.compose.ui.graphics.TransformOrigin
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wonderful.launcher.data.AppInfo
import com.wonderful.launcher.data.AppRepository
import com.wonderful.launcher.data.Folder
import com.wonderful.launcher.data.LauncherData
import com.wonderful.launcher.data.LauncherSettings
import com.wonderful.launcher.data.LauncherStore
import com.wonderful.launcher.data.RecentEntry
import com.wonderful.launcher.data.RecentSource
import com.wonderful.launcher.data.SortOrder
import com.wonderful.launcher.data.SystemActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

enum class Panel { NONE, SEARCH, SETTINGS, RECENT, FOLDERS }

data class RecentItem(val app: AppInfo, val time: Long)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()
    val store = LauncherStore(app, viewModelScope)
    val repo = AppRepository(app, viewModelScope)

    val data: StateFlow<LauncherData> = store.data
    val apps: StateFlow<List<AppInfo>> = repo.apps
    val icons = repo.icons

    // ── 画面状態 ──
    private val _panel = MutableStateFlow(Panel.NONE)
    val panel = _panel.asStateFlow()

    /** パネルが飛び出す起点（押したボタンの位置） */
    var panelOrigin: TransformOrigin = TransformOrigin.Center
        private set

    private val _menuApp = MutableStateFlow<AppInfo?>(null)
    val menuApp = _menuApp.asStateFlow()

    private val _recent = MutableStateFlow<List<RecentItem>>(emptyList())
    val recent = _recent.asStateFlow()

    /** HOME ボタンが押された回数。スフィアのリセットに使う */
    private val _homePresses = MutableStateFlow(0)
    val homePresses = _homePresses.asStateFlow()

    /** スフィアに並ぶアプリ（並び順反映済み） */
    val sphereApps: StateFlow<List<AppInfo>> = combine(apps, data) { all, d ->
        val byKey = all.associateBy { it.key }
        val list = d.sphereApps.mapNotNull { byKey[it] }.filter { it.key !in d.hiddenApps }
        sortApps(list, d).take(d.settings.sphereMaxApps.coerceAtLeast(1))
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dockApps: StateFlow<List<AppInfo>> = combine(apps, data) { all, d ->
        val byKey = all.associateBy { it.key }
        d.dockApps.mapNotNull { byKey[it] }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // アイコン形状が変わったら描き直す
        data.map { it.settings.iconShape }.distinctUntilChanged()
            .onEach { repo.setShape(it) }
            .launchIn(viewModelScope)

        // 初回起動時はスフィアとドックに初期アプリを入れる
        apps.onEach { list ->
            if (list.isNotEmpty() && !data.value.sphereInitialized) initDefaults(list)
        }.launchIn(viewModelScope)
    }

    private fun sortApps(list: List<AppInfo>, d: LauncherData): List<AppInfo> = when (d.settings.sortOrder) {
        SortOrder.NAME -> list.sortedBy { it.label.lowercase() }
        SortOrder.RECENT_INSTALL -> list.sortedByDescending { it.installTime }
        SortOrder.MOST_LAUNCHED -> list.sortedByDescending { d.launchCounts[it.key] ?: 0 }
        SortOrder.SHUFFLE -> list.sortedBy { it.key }.shuffled(Random(d.settings.shuffleSeed))
    }

    private fun initDefaults(list: List<AppInfo>) {
        val pm = ctx.packageManager
        fun pkgFor(intent: Intent): String? =
            pm.resolveActivity(intent, 0)?.activityInfo?.packageName?.takeIf { it != "android" }
        val dockPkgs = listOfNotNull(
            pkgFor(Intent(Intent.ACTION_DIAL)),
            pkgFor(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            pkgFor(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
            pkgFor(Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
        ).distinct()
        val dock = dockPkgs.mapNotNull { p -> list.firstOrNull { it.packageName == p && !it.isWorkProfile }?.key }
        val max = data.value.settings.sphereMaxApps
        store.update {
            it.copy(
                sphereApps = list.take(max).map { a -> a.key },
                sphereInitialized = true,
                dockApps = if (it.dockApps.isEmpty()) dock else it.dockApps,
            )
        }
    }

    // ── パネル操作 ──

    fun openPanel(p: Panel, origin: TransformOrigin = TransformOrigin.Center) {
        panelOrigin = origin
        if (p == Panel.RECENT) refreshRecent()
        _panel.value = p
    }

    fun closePanel() {
        _panel.value = Panel.NONE
    }

    fun showMenu(app: AppInfo?) {
        _menuApp.value = app
    }

    fun onHomePressed(alreadyHome: Boolean) {
        _menuApp.value = null
        when {
            _panel.value != Panel.NONE -> _panel.value = Panel.NONE
            alreadyHome -> _homePresses.value++ // ホームでもう一度押したら球体をスピン
        }
    }

    // ── 起動 ──

    fun launch(app: AppInfo, bounds: Rect? = null, opts: Bundle? = null) {
        if (SystemActions.launch(ctx, app, bounds, opts)) {
            store.update { d ->
                val recent = (listOf(RecentEntry(app.key, System.currentTimeMillis())) +
                    d.recent.filter { it.key != app.key }).take(50)
                d.copy(recent = recent, launchCounts = d.launchCounts + (app.key to ((d.launchCounts[app.key] ?: 0) + 1)))
            }
        }
    }

    fun refreshRecent() {
        viewModelScope.launch(Dispatchers.IO) {
            val d = data.value
            val count = d.settings.recentCount
            val items = if (d.settings.recentSource == RecentSource.SYSTEM && SystemActions.hasUsageAccess(ctx)) {
                SystemActions.systemRecentPackages(ctx, count * 2).mapNotNull { (pkg, t) ->
                    repo.findByPackage(pkg)?.let { RecentItem(it, t) }
                }
            } else {
                d.recent.mapNotNull { e -> repo.findApp(e.key)?.let { RecentItem(it, e.time) } }
            }
            _recent.value = items.filter { it.app.key !in d.hiddenApps }.take(count)
        }
    }

    fun clearRecent() {
        store.update { it.copy(recent = emptyList()) }
        _recent.value = emptyList()
    }

    fun removeRecent(key: String) {
        store.update { d -> d.copy(recent = d.recent.filter { it.key != key }) }
        _recent.value = _recent.value.filter { it.app.key != key }
    }

    // ── スフィア ──

    fun isInSphere(key: String) = key in data.value.sphereApps

    fun toggleSphere(key: String) = store.update { d ->
        if (key in d.sphereApps) d.copy(sphereApps = d.sphereApps - key)
        else d.copy(sphereApps = d.sphereApps + key)
    }

    fun setSphereApps(keys: List<String>) = store.update { it.copy(sphereApps = keys.distinct()) }

    fun addAllToSphere() = store.update { d ->
        d.copy(sphereApps = apps.value.filter { it.key !in d.hiddenApps }.take(d.settings.sphereMaxApps).map { it.key })
    }

    fun clearSphere() = store.update { it.copy(sphereApps = emptyList()) }

    fun reshuffle() = store.updateSettings { it.copy(shuffleSeed = Random.nextInt()) }

    // ── ドック ──

    fun toggleDock(key: String) = store.update { d ->
        if (key in d.dockApps) d.copy(dockApps = d.dockApps - key)
        else d.copy(dockApps = (d.dockApps + key).takeLast(DOCK_MAX))
    }

    fun moveDock(key: String, delta: Int) = store.update { d ->
        val list = d.dockApps.toMutableList()
        val i = list.indexOf(key)
        val j = (i + delta).coerceIn(0, list.lastIndex)
        if (i < 0 || i == j) return@update d
        list.removeAt(i); list.add(j, key)
        d.copy(dockApps = list)
    }

    // ── 非表示 ──

    fun toggleHidden(key: String) = store.update { d ->
        if (key in d.hiddenApps) d.copy(hiddenApps = d.hiddenApps - key)
        else d.copy(hiddenApps = d.hiddenApps + key)
    }

    // ── フォルダ ──

    fun createFolder(name: String, colorIndex: Int, apps: List<String> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        store.update { it.copy(folders = it.folders + Folder(id, name.ifBlank { "NEW FOLDER" }, colorIndex, apps)) }
        return id
    }

    fun updateFolder(id: String, transform: (Folder) -> Folder) = store.update { d ->
        d.copy(folders = d.folders.map { if (it.id == id) transform(it) else it })
    }

    fun deleteFolder(id: String) = store.update { d -> d.copy(folders = d.folders.filter { it.id != id }) }

    fun moveFolder(id: String, delta: Int) = store.update { d ->
        val list = d.folders.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = (i + delta).coerceIn(0, list.lastIndex)
        if (i < 0 || i == j) return@update d
        list.add(j, list.removeAt(i))
        d.copy(folders = list)
    }

    fun addToFolder(folderId: String, key: String) = updateFolder(folderId) {
        if (key in it.apps) it else it.copy(apps = it.apps + key)
    }

    fun removeFromFolder(folderId: String, key: String) = updateFolder(folderId) { it.copy(apps = it.apps - key) }

    // ── 設定 ──

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) = store.updateSettings(transform)

    fun resetSettings() = store.resetSettings()

    fun exportJson() = store.exportJson()

    fun importJson(text: String) = store.importJson(text)

    fun flush() {
        viewModelScope.launch(Dispatchers.IO) { store.flush() }
    }

    override fun onCleared() {
        repo.dispose()
        super.onCleared()
    }

    companion object {
        const val DOCK_MAX = 6
    }
}
