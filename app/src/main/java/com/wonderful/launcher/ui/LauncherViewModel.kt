package com.wonderful.launcher.ui

import android.app.Application
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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
import com.wonderful.launcher.data.GestureAction
import com.wonderful.launcher.data.Migration
import com.wonderful.launcher.data.WIDGET_COLUMNS
import com.wonderful.launcher.data.WidgetItem
import com.wonderful.launcher.data.WidgetKind
import com.wonderful.launcher.data.WidgetManager
import com.wonderful.launcher.data.findFreeSpot
import com.wonderful.launcher.data.WidgetTarget
import com.wonderful.launcher.data.allWidgets
import com.wonderful.launcher.data.mapAllWidgets
import com.wonderful.launcher.system.BadgeListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.random.Random

enum class Panel { NONE, SEARCH, SETTINGS, RECENT, FOLDERS, WIDGETS }

data class RecentItem(val app: AppInfo, val time: Long)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()
    val store = LauncherStore(app, viewModelScope)
    val repo = AppRepository(app, viewModelScope)
    val widgets = WidgetManager(app)

    /** パッケージ名 → 通知件数（通知へのアクセスが許可されている時だけ入る） */
    val badges: StateFlow<Map<String, Int>> = BadgeListenerService.counts

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
    private val _homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homePresses: SharedFlow<Unit> = _homePresses.asSharedFlow()

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
        // 保存データに無いウィジェット ID を解放
        widgets.releaseOrphans(data.value.allWidgets().mapTo(HashSet()) { it.appWidgetId })

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

    private var initializing = false

    private fun initDefaults(list: List<AppInfo>) {
        if (initializing) return
        initializing = true
        viewModelScope.launch {
            val dock = withContext(Dispatchers.Default) {
                defaultHandlerPkgs(dockOnly = true).mapNotNull { p ->
                    list.firstOrNull { it.packageName == p && !it.isWorkProfile }?.key
                }
            }
            val sphere = withContext(Dispatchers.Default) { recommendedSphere(list) }
            store.update {
                if (it.sphereInitialized) it
                else it.copy(
                    sphereApps = sphere,
                    sphereInitialized = true,
                    dockApps = if (it.dockApps.isEmpty()) dock else it.dockApps,
                )
            }
            initializing = false
        }
    }

    /** 電話・メッセージ・ブラウザ・カメラなど、端末の「いつものアプリ」のパッケージ名 */
    private fun defaultHandlerPkgs(dockOnly: Boolean): List<String> {
        val pm = ctx.packageManager
        fun pkgFor(intent: Intent): String? = runCatching {
            pm.resolveActivity(intent, 0)?.activityInfo?.packageName?.takeIf { it != "android" }
        }.getOrNull()
        val dock = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        val more = if (dockOnly) emptyList() else listOf(
            Intent(android.provider.Settings.ACTION_SETTINGS),
            Intent(Intent.ACTION_VIEW).setType("image/*"),
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")),
            Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS),
            Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time")),
            Intent(Intent.ACTION_VIEW, Uri.parse("content://contacts/people")),
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.android.chrome")),
            Intent(Intent.ACTION_VIEW).setType("audio/*"),
        )
        return (dock + more).mapNotNull(::pkgFor).filter { it != ctx.packageName }.distinct()
    }

    /**
     * 最初に球体へ入れるアプリ。全部入れると多すぎるので、
     * 「いつものアプリ」＋自分で入れたアプリ（新しい順）から最大 [RECOMMENDED_SPHERE] 個に絞る。
     */
    private fun recommendedSphere(list: List<AppInfo>): List<String> {
        val pm = ctx.packageManager
        val main = list.filter { !it.isWorkProfile && it.packageName != ctx.packageName }
        val picked = LinkedHashSet<String>()
        defaultHandlerPkgs(dockOnly = false).forEach { p ->
            main.firstOrNull { it.packageName == p }?.let { picked += it.key }
        }
        fun userInstalled(a: AppInfo): Boolean = runCatching {
            val f = pm.getApplicationInfo(a.packageName, 0).flags
            f and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 ||
                f and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        }.getOrDefault(false)
        val (mine, system) = main.filter { it.key !in picked }.partition(::userInstalled)
        mine.sortedByDescending { it.installTime }.forEach { if (picked.size < RECOMMENDED_SPHERE) picked += it.key }
        system.forEach { if (picked.size < RECOMMENDED_SPHERE) picked += it.key }
        return picked.take(RECOMMENDED_SPHERE)
    }

    /** 設定画面の「おすすめだけにする」 */
    fun resetSphereToRecommended() {
        viewModelScope.launch {
            val list = apps.value
            val keys = withContext(Dispatchers.Default) { recommendedSphere(list) }
            store.update { d -> d.copy(sphereApps = keys.filter { it !in d.hiddenApps }) }
        }
    }

    /** 設定画面からの「充電アニメーションを試す」 */
    private val _chargePreview = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val chargePreview: SharedFlow<Unit> = _chargePreview.asSharedFlow()
    fun previewCharge() { _chargePreview.tryEmit(Unit) }

    // ── 設定画面の表示位置（閉じても次に開いた時に同じ所から） ──
    var settingsTab by mutableIntStateOf(0)
    var settingsTabRowScroll = 0
    val settingsScroll = HashMap<Int, Int>()

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
            alreadyHome -> _homePresses.tryEmit(Unit) // ホーム画面でさらにホームボタン → ジェスチャー
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

    // ── ウィジェット ──

    /** ウィジェットを空いている場所に置く */
    fun addWidget(kind: WidgetKind, w: Int, h: Int, appWidgetId: Int = -1, provider: String = "") {
        store.update { d ->
            val ww = w.coerceIn(1, WIDGET_COLUMNS)
            val hh = h.coerceIn(1, 24)
            val (x, y) = findFreeSpot(d.widgets, ww, hh)
            val item = WidgetItem(UUID.randomUUID().toString(), kind, appWidgetId, provider, x, y, ww, hh)
            d.copy(widgets = d.widgets + item)
        }
    }

    /**
     * ウィジェットを指定の場所に置く（ウィジェット画面・時計の位置・ジェスチャー・作り直し）。
     * 時計の位置とジェスチャーは 1 つだけなので、前のものは片付ける。
     */
    fun placeWidget(target: WidgetTarget, kind: WidgetKind, w: Int, h: Int, appWidgetId: Int = -1, provider: String = "") {
        val item = WidgetItem(UUID.randomUUID().toString(), kind, appWidgetId, provider, 0, 0, w.coerceIn(1, WIDGET_COLUMNS), h.coerceIn(1, 24))
        when (target) {
            WidgetTarget.Board -> addWidget(kind, w, h, appWidgetId, provider)
            WidgetTarget.Top -> {
                data.value.topWidget?.let { if (it.appWidgetId != appWidgetId) widgets.deleteId(it.appWidgetId) }
                store.update { it.copy(topWidget = item) }
            }
            is WidgetTarget.Gesture -> {
                data.value.gestureWidgets[target.slot]?.let { if (it.appWidgetId != appWidgetId) widgets.deleteId(it.appWidgetId) }
                store.update { it.copy(gestureWidgets = it.gestureWidgets + (target.slot to item)) }
            }
            is WidgetTarget.Replace -> replaceWidgetId(target.itemId, appWidgetId, provider)
        }
    }

    /** 別端末から復元したウィジェットなどを作り直した時に ID を差し替える */
    fun replaceWidgetId(itemId: String, appWidgetId: Int, provider: String) {
        val old = data.value.allWidgets().firstOrNull { it.id == itemId }?.appWidgetId
        if (old != null && old != appWidgetId) widgets.deleteId(old)
        updateWidget(itemId) { it.copy(appWidgetId = appWidgetId, provider = provider) }
    }

    /** どこに置いたウィジェットでも ID で更新できる */
    fun updateWidget(id: String, transform: (WidgetItem) -> WidgetItem) = store.update { d ->
        d.mapAllWidgets { if (it.id == id) transform(it) else it }
    }

    fun removeTopWidget() {
        data.value.topWidget?.let { widgets.deleteId(it.appWidgetId) }
        store.update { it.copy(topWidget = null) }
    }

    fun removeGestureWidget(slot: String) {
        data.value.gestureWidgets[slot]?.let { widgets.deleteId(it.appWidgetId) }
        store.update { it.copy(gestureWidgets = it.gestureWidgets - slot) }
    }

    fun removeWidget(id: String) {
        data.value.widgets.firstOrNull { it.id == id }?.let { widgets.deleteId(it.appWidgetId) }
        store.update { d -> d.copy(widgets = d.widgets.filter { it.id != id }) }
    }

    fun clearWidgets() {
        // ウィジェット画面のものだけ（時計の位置・ジェスチャー用は残す）
        data.value.widgets.forEach { widgets.deleteId(it.appWidgetId) }
        store.update { it.copy(widgets = emptyList()) }
    }

    // ── 設定 ──

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) = store.updateSettings { old ->
        val new = transform(old)
        // どの操作でも設定画面を開けなくなる変更は、長押しを「設定」にして防ぐ
        if (Migration.settingsReachable(new)) new else new.copy(longPressAction = GestureAction.SETTINGS)
    }

    fun resetSettings() = store.resetSettings()

    fun exportJson() = store.exportJson()

    /**
     * バックアップを読み込む。
     * Android ウィジェットの ID は端末ごとに違うので、この端末で有効なものだけ残し、
     * それ以外は「再設定」が必要な状態にする。
     */
    fun importJson(text: String): Boolean {
        val before = data.value.allWidgets().mapNotNull { it.appWidgetId.takeIf { id -> id >= 0 } }.toSet()
        if (!store.importJson(text)) return false
        store.update { d ->
            d.mapAllWidgets { w ->
                if (w.kind != WidgetKind.SYSTEM || w.appWidgetId < 0) return@mapAllWidgets w
                val info = widgets.info(w.appWidgetId)
                val valid = w.appWidgetId in before && info?.provider?.flattenToString() == w.provider
                if (valid) w else w.copy(appWidgetId = -1)
            }
        }
        val keep = data.value.allWidgets().mapTo(HashSet()) { it.appWidgetId }
        before.filter { it !in keep }.forEach { widgets.deleteId(it) }
        return true
    }

    fun flush() {
        viewModelScope.launch(Dispatchers.IO) { store.flush() }
    }

    override fun onCleared() {
        repo.dispose()
        super.onCleared()
    }

    companion object {
        const val DOCK_MAX = 6
        /** 初回起動時に球体へ入れるアプリの数 */
        const val RECOMMENDED_SPHERE = 24
    }
}
