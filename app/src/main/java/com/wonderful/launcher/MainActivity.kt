package com.wonderful.launcher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.widget.Toast
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wonderful.launcher.data.LauncherSettings
import com.wonderful.launcher.data.OrientationMode
import com.wonderful.launcher.data.WidgetKind
import com.wonderful.launcher.data.WidgetTarget
import com.wonderful.launcher.ui.HomeScreen
import com.wonderful.launcher.ui.LauncherViewModel

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    /** すでにホーム画面が見えている状態か（他アプリから戻った時と区別する） */
    private var atHome = false

    /** 追加途中の Android ウィジェット（許可画面・設定画面から戻るまで保持） */
    private data class PendingWidget(
        val appWidgetId: Int,
        val info: AppWidgetProviderInfo,
        val w: Int,
        val h: Int,
        val target: WidgetTarget,
    )
    private var pendingWidget: PendingWidget? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val data by vm.data.collectAsStateWithLifecycle()
            val s = data.settings
            LaunchedEffect(s.hideStatusBar, s.hideNavBar, s.orientation) { applySystemUi(s) }
            HomeScreen(vm, onAddSystemWidget = ::startAddWidget)
        }
    }

    /** ホーム画面表示中に HOME ボタンが押されたとき */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            vm.onHomePressed(alreadyHome = atHome)
        }
    }

    override fun onStart() {
        super.onStart()
        vm.widgets.startListening()
    }

    override fun onResume() {
        super.onResume()
        atHome = true
        // 他のアプリでアンインストールなどが行われた可能性があるので更新
        vm.repo.refresh()
        vm.refreshRecent()
    }

    override fun onStop() {
        atHome = false
        vm.widgets.stopListening()
        vm.flush()
        super.onStop()
    }

    // ── ウィジェット追加の流れ ──
    // 1. ID を確保 → 2. 未許可なら「ウィジェットの作成を許可」画面 → 3. 必要ならウィジェット自身の設定画面 → 4. 配置

    private fun startAddWidget(info: AppWidgetProviderInfo, w: Int, h: Int, target: WidgetTarget) {
        val id = vm.widgets.allocateId()
        pendingWidget = PendingWidget(id, info, w, h, target)
        if (vm.widgets.bindIfAllowed(id, info)) {
            configureOrFinish()
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            runCatching {
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQ_BIND)
            }.onFailure { cancelWidget("ウィジェットを追加できませんでした") }
        }
    }

    private fun configureOrFinish() {
        val pending = pendingWidget ?: return
        if (pending.info.configure != null) {
            runCatching {
                vm.widgets.host.startAppWidgetConfigureActivityForResult(this, pending.appWidgetId, 0, REQ_CONFIG, null)
            }.onFailure { finishWidget() } // 設定画面を開けなくても置いておく
        } else {
            finishWidget()
        }
    }

    private fun finishWidget() {
        val p = pendingWidget ?: return
        pendingWidget = null
        vm.placeWidget(p.target, WidgetKind.SYSTEM, p.w, p.h, p.appWidgetId, p.info.provider.flattenToString())
    }

    private fun cancelWidget(message: String?) {
        pendingWidget?.let { vm.widgets.deleteId(it.appWidgetId) }
        pendingWidget = null
        if (message != null) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @Deprecated("ウィジェットの許可・設定画面の結果を受け取るため")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_BIND -> if (resultCode == Activity.RESULT_OK) configureOrFinish() else cancelWidget(null)
            REQ_CONFIG -> if (resultCode == Activity.RESULT_OK) finishWidget() else cancelWidget(null)
        }
    }

    companion object {
        private const val REQ_BIND = 4101
        private const val REQ_CONFIG = 4102
    }

    private fun applySystemUi(s: LauncherSettings) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (s.hideStatusBar) controller.hide(WindowInsetsCompat.Type.statusBars())
        else controller.show(WindowInsetsCompat.Type.statusBars())
        if (s.hideNavBar) controller.hide(WindowInsetsCompat.Type.navigationBars())
        else controller.show(WindowInsetsCompat.Type.navigationBars())
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        requestedOrientation = when (s.orientation) {
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
