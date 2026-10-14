package com.wonderful.launcher.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File

/** 端末の状態のスナップショット（ウィジェット表示用） */
data class SysStats(
    val memTotal: Long = 0,
    val memAvail: Long = 0,
    val lowMemory: Boolean = false,
    /** コアごとの (現在, 最大) 周波数 kHz。読めない端末では空 */
    val cpuFreqs: List<Pair<Long, Long>> = emptyList(),
    val batteryTemp: Float? = null,
    val cpuTemp: Float? = null,
    /** PowerManager の発熱状態（API 29+） */
    val thermalStatus: Int? = null,
    val storageTotal: Long = 0,
    val storageFree: Long = 0,
    val rxPerSec: Long = 0,
    val txPerSec: Long = 0,
    val memHistory: List<Float> = emptyList(),
    val cpuHistory: List<Float> = emptyList(),
    val tempHistory: List<Float> = emptyList(),
    val netHistory: List<Float> = emptyList(),
) {
    val memUsedRatio: Float get() = if (memTotal > 0) 1f - memAvail.toFloat() / memTotal else 0f

    /** CPU 負荷の目安（周波数の高さ）。読めなければ null */
    val cpuLoad: Float?
        get() = cpuFreqs.filter { it.second > 0 }.takeIf { it.isNotEmpty() }
            ?.map { it.first.toFloat() / it.second }?.average()?.toFloat()

    val storageUsedRatio: Float get() = if (storageTotal > 0) 1f - storageFree.toFloat() / storageTotal else 0f
}

/**
 * 端末の状態を 1 秒ごとに読む。表示しているウィジェットがある間だけ動く（共有）。
 *
 * ※ Android 8 以降、一般のアプリは CPU 使用率（/proc/stat）を読めないため、
 *   CPU は各コアの動作周波数から負荷の目安を出している。
 */
object SystemMonitor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var shared: StateFlow<SysStats>? = null

    fun stats(context: Context): StateFlow<SysStats> {
        shared?.let { return it }
        synchronized(this) {
            shared?.let { return it }
            val app = context.applicationContext
            val f = flow {
                var prev = SysStats()
                var lastRx = TrafficStats.getTotalRxBytes()
                var lastTx = TrafficStats.getTotalTxBytes()
                var lastTime = SystemClock.elapsedRealtime()
                while (true) {
                    val now = SystemClock.elapsedRealtime()
                    val dt = ((now - lastTime) / 1000f).coerceAtLeast(0.2f)
                    val rx = TrafficStats.getTotalRxBytes()
                    val tx = TrafficStats.getTotalTxBytes()
                    val rxRate = if (rx >= 0 && lastRx >= 0) ((rx - lastRx) / dt).toLong().coerceAtLeast(0) else 0
                    val txRate = if (tx >= 0 && lastTx >= 0) ((tx - lastTx) / dt).toLong().coerceAtLeast(0) else 0
                    lastRx = rx; lastTx = tx; lastTime = now
                    val s = sample(app, prev, rxRate, txRate)
                    prev = s
                    emit(s)
                    delay(1000)
                }
            }.stateIn(scope, SharingStarted.WhileSubscribed(3000), SysStats())
            shared = f
            return f
        }
    }

    private fun sample(context: Context, prev: SysStats, rxRate: Long, txRate: Long): SysStats {
        val am = context.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        runCatching { am.getMemoryInfo(mi) }
        val freqs = readCpuFreqs()
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bTemp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { context.getSystemService(PowerManager::class.java).currentThermalStatus }.getOrNull()
        } else null
        val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val cpuTemp = readCpuTemp()
        val base = SysStats(
            memTotal = mi.totalMem,
            memAvail = mi.availMem,
            lowMemory = mi.lowMemory,
            cpuFreqs = freqs,
            batteryTemp = bTemp,
            cpuTemp = cpuTemp,
            thermalStatus = thermal,
            storageTotal = stat?.totalBytes ?: 0,
            storageFree = stat?.availableBytes ?: 0,
            rxPerSec = rxRate,
            txPerSec = txRate,
        )
        return base.copy(
            memHistory = (prev.memHistory + base.memUsedRatio).takeLast(HISTORY),
            cpuHistory = (prev.cpuHistory + (base.cpuLoad ?: 0f)).takeLast(HISTORY),
            tempHistory = (prev.tempHistory + (cpuTemp ?: bTemp ?: 0f)).takeLast(HISTORY),
            netHistory = (prev.netHistory + (rxRate + txRate).toFloat()).takeLast(HISTORY),
        )
    }

    private fun readLong(path: String): Long? =
        runCatching { File(path).readText().trim().toLong() }.getOrNull()

    private fun readCpuFreqs(): List<Pair<Long, Long>> {
        val cores = Runtime.getRuntime().availableProcessors()
        return (0 until cores).mapNotNull { i ->
            val base = "/sys/devices/system/cpu/cpu$i/cpufreq/"
            val cur = readLong(base + "scaling_cur_freq") ?: return@mapNotNull null
            val max = readLong(base + "cpuinfo_max_freq") ?: readLong(base + "scaling_max_freq") ?: return@mapNotNull null
            cur to max
        }
    }

    /**
     * CPU の温度センサーのファイル。最初に 1 回だけ探して覚えておく
     * （以前は毎秒すべてのセンサーを読み直していた）。
     */
    private val cpuTempFiles: List<File> by lazy {
        runCatching {
            val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") } ?: return@runCatching emptyList()
            zones.filter { z ->
                val type = runCatching { File(z, "type").readText().trim().lowercase() }.getOrNull() ?: return@filter false
                (type.contains("cpu") || type.contains("tsens") || type.contains("soc") || type.contains("ap")) &&
                    readLong(File(z, "temp").path) != null
            }.map { File(it, "temp") }.take(8)
        }.getOrDefault(emptyList())
    }

    /** CPU の温度（読める端末だけ）。℃ */
    private fun readCpuTemp(): Float? = cpuTempFiles.mapNotNull { f ->
        val raw = readLong(f.path) ?: return@mapNotNull null
        val c = if (raw > 1000) raw / 1000f else raw.toFloat()
        c.takeIf { it in 5f..130f }
    }.maxOrNull()

    fun thermalLabel(status: Int?): String = when (status) {
        null -> "-"
        PowerManager.THERMAL_STATUS_NONE -> "正常"
        PowerManager.THERMAL_STATUS_LIGHT -> "やや温かい"
        PowerManager.THERMAL_STATUS_MODERATE -> "温かい"
        PowerManager.THERMAL_STATUS_SEVERE -> "熱い"
        PowerManager.THERMAL_STATUS_CRITICAL -> "とても熱い"
        PowerManager.THERMAL_STATUS_EMERGENCY, PowerManager.THERMAL_STATUS_SHUTDOWN -> "危険"
        else -> "?"
    }

    /**
     * メモリ解放。裏で動いているアプリの処理を終わらせ、このランチャー自身のメモリも整理する。
     * 戻り値は増えた空きメモリ（バイト）。Android の仕組み上、解放できる量は端末の状態次第。
     */
    suspend fun boost(context: Context, packages: List<String>): Long = withContext(Dispatchers.IO) {
        val am = context.getSystemService(ActivityManager::class.java)
        val before = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        packages.distinct().filter { it != context.packageName }.forEach { pkg ->
            runCatching { am.killBackgroundProcesses(pkg) }
        }
        System.gc()
        delay(1200) // 反映を少し待つ
        val after = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        after - before
    }

    fun formatBytes(b: Long): String = when {
        b >= 1L shl 30 -> "%.1fGB".format(b / (1L shl 30).toFloat())
        b >= 1L shl 20 -> "%.0fMB".format(b / (1L shl 20).toFloat())
        b >= 1L shl 10 -> "%.0fKB".format(b / (1L shl 10).toFloat())
        else -> "${b}B"
    }

    private const val HISTORY = 60
}
