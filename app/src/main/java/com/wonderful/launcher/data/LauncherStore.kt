package com.wonderful.launcher.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 設定・フォルダ・履歴などを 1 つの JSON ファイルに保存する。
 * 変更は即座に StateFlow へ反映し、ディスク書き込みはまとめて遅延実行する。
 */
@OptIn(FlowPreview::class)
class LauncherStore(context: Context, scope: CoroutineScope) {

    private val file = File(context.filesDir, "launcher_state.json")
    private val _data = MutableStateFlow(load())
    val data: StateFlow<LauncherData> = _data.asStateFlow()

    init {
        _data.drop(1)
            .debounce(400)
            .onEach { save(it) }
            .launchIn(CoroutineScope(scope.coroutineContext + Dispatchers.IO))
    }

    fun update(transform: (LauncherData) -> LauncherData) = _data.update(transform)

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) =
        _data.update { it.copy(settings = transform(it.settings)) }

    /** 画面を離れる時などに即時保存する（IO スレッドから呼ぶ） */
    fun flush() = save(_data.value)

    fun exportJson(): String = json.encodeToString(LauncherData.serializer(), _data.value)

    /** バックアップから復元。失敗したら false */
    fun importJson(text: String): Boolean = runCatching {
        val imported = json.decodeFromString(LauncherData.serializer(), text)
        _data.value = imported
    }.isSuccess

    fun resetSettings() = _data.update { it.copy(settings = LauncherSettings()) }

    private fun load(): LauncherData = runCatching {
        if (file.exists()) json.decodeFromString(LauncherData.serializer(), file.readText())
        else LauncherData()
    }.getOrElse { LauncherData() }

    private fun save(d: LauncherData) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(LauncherData.serializer(), d))
            tmp.renameTo(file)
        }
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
            prettyPrint = false
        }
    }
}
