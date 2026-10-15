// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (c) 2026 72no6495

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

    fun exportJson(): String = BackupCodec.encode(_data.value)

    /**
     * バックアップから復元。失敗したら false。
     * どの古いバージョンのバックアップでも [Migration] を通して今の形式にそろえる。
     */
    fun importJson(text: String): Boolean = runCatching {
        _data.value = decode(text)
    }.isSuccess

    fun resetSettings() = _data.update { it.copy(settings = LauncherSettings()) }

    private fun load(): LauncherData {
        if (!file.exists()) return fresh()
        return runCatching { decode(file.readText()) }.getOrElse {
            // 読めなかったファイルは消さずに退避しておく（データを失わないため）
            runCatching { file.copyTo(File(file.parentFile, file.name + ".broken"), overwrite = true) }
            fresh()
        }
    }

    private fun save(d: LauncherData) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(BackupCodec.encode(d))
            tmp.renameTo(file)
        }
    }

    companion object {
        /** 新規インストール時の状態 */
        fun fresh() = LauncherData(dataVersion = Migration.CURRENT_VERSION)

        fun decode(text: String): LauncherData = BackupCodec.decode(text)
    }
}
