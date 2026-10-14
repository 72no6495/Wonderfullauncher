package com.wonderful.launcher

import android.content.Intent
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
import com.wonderful.launcher.ui.HomeScreen
import com.wonderful.launcher.ui.LauncherViewModel

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    /** すでにホーム画面が見えている状態か（他アプリから戻った時と区別する） */
    private var atHome = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val data by vm.data.collectAsStateWithLifecycle()
            val s = data.settings
            LaunchedEffect(s.hideStatusBar, s.hideNavBar, s.orientation) { applySystemUi(s) }
            HomeScreen(vm)
        }
    }

    /** ホーム画面表示中に HOME ボタンが押されたとき */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            vm.onHomePressed(alreadyHome = atHome)
        }
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
        vm.flush()
        super.onStop()
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
