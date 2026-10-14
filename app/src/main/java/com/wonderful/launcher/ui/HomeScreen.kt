package com.wonderful.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wonderful.launcher.data.AppInfo
import com.wonderful.launcher.data.CornerAction
import com.wonderful.launcher.data.GestureAction
import com.wonderful.launcher.data.LauncherSettings
import com.wonderful.launcher.data.PanelAnim
import com.wonderful.launcher.data.SortOrder
import com.wonderful.launcher.data.SystemActions
import com.wonderful.launcher.ui.components.CornerButton
import com.wonderful.launcher.ui.components.Dock
import com.wonderful.launcher.ui.components.EmptyMessage
import com.wonderful.launcher.ui.components.RetroClock
import com.wonderful.launcher.ui.components.Scrim
import com.wonderful.launcher.ui.panels.AppMenu
import com.wonderful.launcher.ui.panels.FolderActions
import com.wonderful.launcher.ui.panels.FolderPanel
import com.wonderful.launcher.ui.panels.RecentPanel
import com.wonderful.launcher.ui.panels.SearchPanel
import com.wonderful.launcher.ui.settings.SettingsPanel
import com.wonderful.launcher.ui.sphere.SphereView
import com.wonderful.launcher.ui.theme.LocalPalette
import com.wonderful.launcher.ui.theme.RetroTheme
import com.wonderful.launcher.ui.wallpaper.CrtOverlay
import com.wonderful.launcher.ui.wallpaper.WallpaperLayer
import kotlin.math.min
import kotlin.random.Random

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val icons by vm.icons.collectAsStateWithLifecycle()
    val sphereApps by vm.sphereApps.collectAsStateWithLifecycle()
    val dockApps by vm.dockApps.collectAsStateWithLifecycle()
    val panel by vm.panel.collectAsStateWithLifecycle()
    val menuApp by vm.menuApp.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val homePresses by vm.homePresses.collectAsStateWithLifecycle()
    val s = data.settings
    val context = LocalContext.current

    RetroTheme(s) {
        val p = LocalPalette.current
        var resetRequests by remember { mutableIntStateOf(0) }
        // 最後に開いていたアプリメニューの対象（閉じるアニメーション中も表示するため）
        val lastMenuApp = remember { arrayOfNulls<AppInfo>(1) }
        if (menuApp != null) lastMenuApp[0] = menuApp

        fun runGesture(a: GestureAction) {
            when (a) {
                GestureAction.NONE -> Unit
                GestureAction.SEARCH -> vm.openPanel(Panel.SEARCH)
                GestureAction.SETTINGS -> vm.openPanel(Panel.SETTINGS)
                GestureAction.RECENT -> vm.openPanel(Panel.RECENT)
                GestureAction.FOLDERS -> vm.openPanel(Panel.FOLDERS)
                GestureAction.NOTIFICATIONS -> SystemActions.expandNotifications(context)
                GestureAction.RESET_SPHERE -> resetRequests++
                GestureAction.SHUFFLE_SPHERE -> vm.updateSettings {
                    it.copy(sortOrder = SortOrder.SHUFFLE, shuffleSeed = Random.nextInt())
                }
            }
        }

        val launchFromPanel: (AppInfo) -> Unit = { app ->
            vm.launch(app)
            vm.closePanel()
        }

        BackHandler(enabled = panel != Panel.NONE || menuApp != null) {
            if (menuApp != null) vm.showMenu(null) else vm.closePanel()
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val rootW = constraints.maxWidth.toFloat()
            val rootH = constraints.maxHeight.toFloat()

            WallpaperLayer(s, p, paused = panel != Panel.NONE)

            // ── ホーム ──
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                if (s.showClock) {
                    RetroClock(
                        onClick = { if (s.clockTapOpensAlarm) SystemActions.openAlarms(context) },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    SphereView(
                        apps = sphereApps,
                        icons = icons,
                        settings = s,
                        palette = p,
                        // 設定画面は半透明なので、後ろの球体で変更をその場で確認できるよう止めない
                        paused = panel != Panel.NONE && panel != Panel.SETTINGS,
                        homePresses = homePresses,
                        resetRequests = resetRequests,
                        onLaunch = { app, rect, opts -> vm.launch(app, rect, opts) },
                        onLongPressApp = { vm.showMenu(it) },
                        onLongPressEmpty = { runGesture(s.longPressAction) },
                        onDoubleTapEmpty = if (s.doubleTapAction != GestureAction.NONE) {
                            { runGesture(s.doubleTapAction) }
                        } else null,
                        onSizeCommitted = { v -> vm.updateSettings { it.copy(sphereSize = v) } },
                    )
                    if (sphereApps.isEmpty() && apps.isNotEmpty()) {
                        EmptyMessage(
                            "スフィアが空っぽです\n検索 →「球体を編集」でアプリを追加",
                            Modifier.align(Alignment.Center),
                        )
                    }
                    CornerButtons(s, rootW, rootH) { action, origin ->
                        when (action) {
                            CornerAction.SEARCH -> vm.openPanel(Panel.SEARCH, origin)
                            CornerAction.SETTINGS -> vm.openPanel(Panel.SETTINGS, origin)
                            CornerAction.RECENT -> vm.openPanel(Panel.RECENT, origin)
                            CornerAction.FOLDERS -> vm.openPanel(Panel.FOLDERS, origin)
                            CornerAction.NONE -> Unit
                        }
                    }
                }
                if (s.showDock && dockApps.isNotEmpty()) {
                    Dock(dockApps, icons, onLaunch = { vm.launch(it) }, onLongPress = { vm.showMenu(it) })
                    Spacer(Modifier.height(10.dp))
                }
            }

            // ── パネル ──
            AnimatedVisibility(panel != Panel.NONE || menuApp != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(250))) {
                Box(Modifier.fillMaxSize()) { Scrim { if (menuApp != null) vm.showMenu(null) else vm.closePanel() } }
            }

            val origin = vm.panelOrigin
            PanelHost(panel == Panel.SEARCH, s, origin) {
                SearchPanel(
                    data, apps, icons,
                    onLaunch = launchFromPanel,
                    onMenu = { vm.showMenu(it) },
                    onToggleSphere = { vm.toggleSphere(it) },
                    onClose = { vm.closePanel() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PanelHost(panel == Panel.RECENT, s, origin) {
                RecentPanel(
                    s, recent, icons,
                    onLaunch = launchFromPanel,
                    onMenu = { vm.showMenu(it) },
                    onRemove = { vm.removeRecent(it) },
                    onClear = { vm.clearRecent() },
                    onClose = { vm.closePanel() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PanelHost(panel == Panel.FOLDERS, s, origin) {
                FolderPanel(
                    data, apps, icons,
                    actions = remember(vm) {
                        FolderActions(
                            create = { n, c, a -> vm.createFolder(n, c, a) },
                            rename = { id, n, c -> vm.updateFolder(id) { it.copy(name = n, colorIndex = c) } },
                            delete = { vm.deleteFolder(it) },
                            setApps = { id, a -> vm.updateFolder(id) { it.copy(apps = a) } },
                            removeApp = { id, k -> vm.removeFromFolder(id, k) },
                            move = { id, d -> vm.moveFolder(id, d) },
                        )
                    },
                    onLaunch = launchFromPanel,
                    onMenu = { vm.showMenu(it) },
                    onClose = { vm.closePanel() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PanelHost(panel == Panel.SETTINGS, s, origin) {
                SettingsPanel(
                    data, apps, icons, vm,
                    onResetSphere = { resetRequests++ },
                    onClose = { vm.closePanel() },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // ── アプリメニュー ──
            AnimatedVisibility(
                visible = menuApp != null,
                enter = scaleIn(spring(dampingRatio = 0.65f, stiffness = 500f), initialScale = 0.6f) + fadeIn(tween(150)),
                exit = scaleOut(tween(160), targetScale = 0.8f) + fadeOut(tween(160)),
                modifier = Modifier.align(Alignment.Center),
            ) {
                lastMenuApp[0]?.let { app ->
                    AppMenu(
                        app, icons[app.key], data, vm,
                        onDismiss = { vm.showMenu(null) },
                        modifier = Modifier.padding(24.dp).widthIn(max = 420.dp).systemBarsPadding(),
                    )
                }
            }

            CrtOverlay(s)
        }
    }
}

/** パネルを設定に応じたアニメーションで出し入れする */
@Composable
private fun PanelHost(
    visible: Boolean,
    s: LauncherSettings,
    origin: TransformOrigin,
    content: @Composable () -> Unit,
) {
    val dur = (380 / s.animSpeed).toInt()
    val (enter, exit) = panelTransitions(s.panelAnim, origin, dur)
    AnimatedVisibility(visible, enter = enter, exit = exit) {
        Box(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .glitch(this, s.panelAnim == PanelAnim.GLITCH),
        ) { content() }
    }
}

private fun panelTransitions(anim: PanelAnim, origin: TransformOrigin, dur: Int): Pair<EnterTransition, ExitTransition> =
    when (anim) {
        PanelAnim.ZOOM ->
            (scaleIn(spring(dampingRatio = 0.72f, stiffness = 320f), initialScale = 0.05f, transformOrigin = origin) + fadeIn(tween(dur / 2))) to
                (scaleOut(tween(dur * 2 / 3), targetScale = 0.05f, transformOrigin = origin) + fadeOut(tween(dur * 2 / 3)))
        PanelAnim.SLIDE ->
            (slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it } + fadeIn(tween(dur / 2))) to
                (slideOutVertically(tween(dur * 2 / 3)) { it } + fadeOut(tween(dur * 2 / 3)))
        PanelAnim.GLITCH ->
            (fadeIn(tween(dur)) + scaleIn(tween(dur), initialScale = 1.15f)) to
                (fadeOut(tween(dur / 2)) + scaleOut(tween(dur / 2), targetScale = 1.1f))
        PanelAnim.FADE -> fadeIn(tween(dur)) to fadeOut(tween(dur))
    }

/** グリッチ演出：出入りの途中で横にガタガタずれる */
@Composable
private fun Modifier.glitch(scope: AnimatedVisibilityScope, enabled: Boolean): Modifier {
    if (!enabled) return this
    val progress by scope.transition.animateFloat(
        transitionSpec = { tween(420) },
        label = "glitch",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    return graphicsLayer {
        if (progress < 1f) {
            val k = 1f - progress
            val r = Random((progress * 30).toInt())
            translationX = (r.nextFloat() - 0.5f) * 80f * k
            translationY = (r.nextFloat() - 0.5f) * 20f * k
            scaleY = 1f + (r.nextFloat() - 0.5f) * 0.2f * k
        }
    }
}

/**
 * スフィアの左上/右上/左下/右下にボタンを置く。
 * 球体の大きさに合わせて位置が変わり、はみ出す場合は内側に寄せる。
 */
@Composable
private fun CornerButtons(
    s: LauncherSettings,
    rootW: Float,
    rootH: Float,
    onClick: (CornerAction, TransformOrigin) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val radius = min(w, h) / 2f * 0.78f * s.sphereSize
        val half = with(density) { (s.cornerSizeDp.dp / 2 + 14.dp).toPx() }
        val dx = min(radius * s.cornerSpread, w / 2f - half - with(density) { 6.dp.toPx() })
        val dy = min(radius * s.cornerSpread, h / 2f - half - with(density) { 10.dp.toPx() })
        val corners = listOf(
            Triple(s.cornerTopLeft, -1f, -1f),
            Triple(s.cornerTopRight, 1f, -1f),
            Triple(s.cornerBottomLeft, -1f, 1f),
            Triple(s.cornerBottomRight, 1f, 1f),
        )
        corners.forEachIndexed { i, (action, sx, sy) ->
            val pos = remember { FloatArray(2) }
            CornerButton(
                action = action,
                phase = i * 0.27f,
                onClick = {
                    val origin = if (rootW > 0 && rootH > 0) {
                        TransformOrigin((pos[0] / rootW).coerceIn(0f, 1f), (pos[1] / rootH).coerceIn(0f, 1f))
                    } else TransformOrigin.Center
                    onClick(action, origin)
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(
                        x = with(density) { (dx * sx).toDp() },
                        y = with(density) { (dy * sy).toDp() },
                    )
                    .onGloballyPositioned {
                        val p = it.positionInRoot()
                        pos[0] = p.x + it.size.width / 2f
                        pos[1] = p.y + it.size.height / 2f
                    },
            )
        }
    }
}
