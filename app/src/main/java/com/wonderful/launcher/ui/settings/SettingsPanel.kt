package com.wonderful.launcher.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wonderful.launcher.data.AppInfo
import com.wonderful.launcher.data.ClockStyle
import com.wonderful.launcher.data.CornerAction
import com.wonderful.launcher.data.FontStyle
import com.wonderful.launcher.data.GestureAction
import com.wonderful.launcher.data.IconShape
import com.wonderful.launcher.data.LabelMode
import com.wonderful.launcher.data.LauncherData
import com.wonderful.launcher.data.LauncherSettings
import com.wonderful.launcher.data.OrientationMode
import com.wonderful.launcher.data.PalettePreset
import com.wonderful.launcher.data.PanelAnim
import com.wonderful.launcher.data.RecentSource
import com.wonderful.launcher.data.SortOrder
import com.wonderful.launcher.data.SphereLayout
import com.wonderful.launcher.data.SystemActions
import com.wonderful.launcher.data.WallpaperType
import com.wonderful.launcher.ui.LauncherViewModel
import com.wonderful.launcher.ui.Panel
import com.wonderful.launcher.data.GestureSlot
import com.wonderful.launcher.data.TapEffect
import com.wonderful.launcher.data.ChargeStyle
import com.wonderful.launcher.data.TrailStyle
import android.os.Build
import com.wonderful.launcher.data.Migration
import com.wonderful.launcher.data.SpecialPermission
import com.wonderful.launcher.data.actionFor
import com.wonderful.launcher.data.withAction
import com.wonderful.launcher.ui.components.Glyph
import com.wonderful.launcher.ui.components.RetroGlyph
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.wonderful.launcher.ui.components.AppIcon
import com.wonderful.launcher.ui.components.NeonText
import com.wonderful.launcher.ui.components.RetroButton
import com.wonderful.launcher.ui.components.RetroChip
import com.wonderful.launcher.ui.components.RetroLabel
import com.wonderful.launcher.ui.components.RetroTextField
import com.wonderful.launcher.ui.components.RetroWindow
import com.wonderful.launcher.ui.components.SectionHeader
import com.wonderful.launcher.ui.panels.normalizeForSearch
import com.wonderful.launcher.ui.panels.matchScore
import com.wonderful.launcher.ui.theme.LocalPalette
import com.wonderful.launcher.ui.theme.paletteOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Tab(val label: String) {
    SPHERE("球体"),
    BUTTONS("ボタン"),
    LOOK("見た目"),
    EFFECTS("エフェクト"),
    CHARGE("充電"),
    PERFORMANCE("軽量化"),
    WALLPAPER("壁紙"),
    CLOCK("時計"),
    SEARCH("検索"),
    RECENT("履歴"),
    FOLDER("フォルダ/ドック"),
    WIDGETS("ウィジェット"),
    GESTURE("ジェスチャー"),
    PERMISSIONS("権限"),
    APPS("アプリ管理"),
    SYSTEM("システム"),
    BACKUP("バックアップ"),
    ABOUT("情報"),
}

@Composable
fun SettingsPanel(
    data: LauncherData,
    apps: List<AppInfo>,
    icons: Map<String, ImageBitmap>,
    vm: LauncherViewModel,
    onResetSphere: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 最後に開いていたタブを覚えておく（パネルを閉じても ViewModel に残る）
    val tab = Tab.entries.getOrElse(vm.settingsTab) { Tab.SPHERE }
    val tabRowScroll = rememberScrollState(vm.settingsTabRowScroll)
    DisposableEffect(Unit) { onDispose { vm.settingsTabRowScroll = tabRowScroll.value } }
    val p = LocalPalette.current
    val s = data.settings
    val update: ((LauncherSettings) -> LauncherSettings) -> Unit = { vm.updateSettings(it) }
    // LAUNCH_APP で起動するアプリを選んでいるジェスチャー
    var chooseFor by remember { mutableStateOf<GestureSlot?>(null) }

    Box(modifier) {
    RetroWindow("CONFIG.SYS", onClose, Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(tabRowScroll)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Tab.entries.forEach { t -> RetroChip(t.label, t == tab, { vm.settingsTab = t.ordinal }) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(p.pink, p.cyan))))
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(280)) { it * dir / 3 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(240)) { -it * dir / 3 } + fadeOut(tween(180)))
            },
            label = "tab",
            modifier = Modifier.fillMaxSize(),
        ) { t ->
            when (t) {
                Tab.APPS -> AppsTab(data, apps, icons, vm)
                else -> ScrollTab(t.ordinal, vm) {
                    when (t) {
                        Tab.SPHERE -> SphereTab(s, update, vm, onResetSphere)
                        Tab.BUTTONS -> ButtonsTab(s, update)
                        Tab.LOOK -> LookTab(s, update)
                        Tab.EFFECTS -> EffectsTab(s, update)
                        Tab.CHARGE -> ChargeTab(s, update, vm)
                        Tab.PERFORMANCE -> PerformanceTab(s, update)
                        Tab.WALLPAPER -> WallpaperTab(s, update)
                        Tab.CLOCK -> ClockTab(s, update)
                        Tab.SEARCH -> SearchTab(s, update)
                        Tab.RECENT -> RecentTab(s, update, vm)
                        Tab.FOLDER -> FolderDockTab(s, update, data, apps, icons, vm)
                        Tab.WIDGETS -> WidgetsTab(s, update, data, vm)
                        Tab.GESTURE -> GestureTab(s, update, apps) { chooseFor = it }
                        Tab.PERMISSIONS -> PermissionsTab(s, update)
                        Tab.SYSTEM -> SystemTab(s, update, vm)
                        Tab.BACKUP -> BackupTab(vm)
                        Tab.ABOUT -> AboutTab(data, apps)
                        Tab.APPS -> Unit
                    }
                }
            }
        }
    }
    AnimatedVisibility(
        visible = chooseFor != null,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.9f),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.9f),
    ) {
        val slot = chooseFor
        AppChooser(
            title = (slot?.label ?: "") + " で起動",
            apps = apps,
            icons = icons,
            onPick = { app ->
                if (slot != null) update {
                    it.withAction(slot, GestureAction.LAUNCH_APP)
                        .copy(gestureApps = it.gestureApps + (slot.name to app.key))
                }
                chooseFor = null
            },
            onClose = { chooseFor = null },
        )
    }
    }
}

@Composable
private fun AppChooser(
    title: String,
    apps: List<AppInfo>,
    icons: Map<String, ImageBitmap>,
    onPick: (AppInfo) -> Unit,
    onClose: () -> Unit,
) {
    val p = LocalPalette.current
    var query by remember { mutableStateOf("") }
    val q = normalizeForSearch(query.trim())
    val list = remember(apps, q) { apps.filter { matchScore(it, q, true) > 0 } }
    RetroWindow("SELECT_APP.EXE", onClose, Modifier.fillMaxSize().padding(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            RetroLabel(title, color = p.cyan, size = 12.sp)
            Spacer(Modifier.height(8.dp))
            RetroTextField(query, { query = it }, "アプリを検索…", Modifier.fillMaxWidth())
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            items(list, key = { it.key }) { app ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(app) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(icons[app.key], app.label, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    RetroLabel(app.label, size = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ScrollTab(key: Int, vm: LauncherViewModel, content: @Composable ColumnScope.() -> Unit) {
    // タブごとのスクロール位置も覚えておく
    val scroll = rememberScrollState(vm.settingsScroll[key] ?: 0)
    DisposableEffect(key) { onDispose { vm.settingsScroll[key] = scroll.value } }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp)
            .padding(bottom = 40.dp),
        content = content,
    )
}

private typealias Update = ((LauncherSettings) -> LauncherSettings) -> Unit

private fun pct(v: Float) = "${(v * 100).toInt()}%"

// ─────────────────────────────────────────────

@Composable
private fun SphereTab(s: LauncherSettings, u: Update, vm: LauncherViewModel, onResetSphere: () -> Unit) {
    SectionHeader("形とサイズ")
    ChoiceSetting("並べ方", SphereLayout.entries, s.sphereLayout, { it.label }) { v -> u { it.copy(sphereLayout = v) } }
    SliderSetting("球体の大きさ", s.sphereSize, 0.4f..1.5f, "ホーム画面で2本指ピンチでも変更できます", format = ::pct) { v -> u { it.copy(sphereSize = v) } }
    SliderSetting("アイコンの大きさ", s.iconSizeDp, 32f..88f, format = { "${it.toInt()}dp" }) { v -> u { it.copy(iconSizeDp = v) } }
    SliderSetting("最大アプリ数", s.sphereMaxApps.toFloat(), 8f..200f, format = { "${it.toInt()}" }) { v -> u { it.copy(sphereMaxApps = v.toInt()) } }
    ChoiceSetting("並び順", SortOrder.entries, s.sortOrder, { it.label }) { v -> u { it.copy(sortOrder = v) } }
    if (s.sortOrder == SortOrder.SHUFFLE) ActionSetting("並びをシャッフルし直す", "SHUFFLE") { vm.reshuffle() }

    SectionHeader("立体感")
    SliderSetting("遠近感（パース）", s.perspective, 0f..1f, format = ::pct) { v -> u { it.copy(perspective = v) } }
    SliderSetting("奥のアイコンを薄く", s.depthFade, 0f..0.95f, format = ::pct) { v -> u { it.copy(depthFade = v) } }
    SliderSetting("奥のアイコンを小さく", s.depthScale, 0f..0.9f, format = ::pct) { v -> u { it.copy(depthScale = v) } }
    SwitchSetting("ワイヤーフレーム", s.wireframe, "球体の経線・緯線をネオンで描く") { v -> u { it.copy(wireframe = v) } }
    if (s.wireframe) SliderSetting("ワイヤーの濃さ", s.wireframeOpacity, 0.05f..1f, format = ::pct) { v -> u { it.copy(wireframeOpacity = v) } }

    SectionHeader("回転")
    SliderSetting("スワイプ感度", s.dragSensitivity, 0.2f..3f, format = { "x%.1f".format(it) }) { v -> u { it.copy(dragSensitivity = v) } }
    SliderSetting("慣性（指を離した後の回転）", s.inertia, 0f..1f, format = ::pct) { v -> u { it.copy(inertia = v) } }
    SwitchSetting("自動回転", s.autoRotate) { v -> u { it.copy(autoRotate = v) } }
    if (s.autoRotate) {
        SliderSetting("自動回転の速さ", s.autoRotateSpeed, 0.02f..1.5f, format = { "%.2f".format(it) }) { v -> u { it.copy(autoRotateSpeed = v) } }
        SliderSetting("回転軸の傾き", s.autoRotateTilt, -60f..60f, format = { "${it.toInt()}°" }) { v -> u { it.copy(autoRotateTilt = v) } }
        SliderSetting("再開までの時間", s.autoRotateResumeSec, 0f..10f, format = { "%.1f秒".format(it) }) { v -> u { it.copy(autoRotateResumeSec = v) } }
        SwitchSetting("逆回転", s.reverseAutoRotate) { v -> u { it.copy(reverseAutoRotate = v) } }
    }
    ActionSetting("回転をリセット", "RESET", color = LocalPalette.current.pink) { onResetSphere() }

    SectionHeader("アイコン")
    ChoiceSetting("アイコンの形", IconShape.entries, s.iconShape, { it.label }) { v -> u { it.copy(iconShape = v) } }
    SwitchSetting("ネオングロー", s.iconGlow, "手前のアイコンを光らせる") { v -> u { it.copy(iconGlow = v) } }
    if (s.iconGlow) SliderSetting("グローの強さ", s.glowIntensity, 0.05f..1f, format = ::pct) { v -> u { it.copy(glowIntensity = v) } }
    SwitchSetting("アイコンの輪郭リング", s.iconRing) { v -> u { it.copy(iconRing = v) } }
    SwitchSetting("一番手前を強調", s.focusFrontIcon, "黄色いリング＋名前を表示") { v -> u { it.copy(focusFrontIcon = v) } }
    ChoiceSetting("アプリ名", LabelMode.entries, s.labelMode, { it.label }) { v -> u { it.copy(labelMode = v) } }
    if (s.labelMode != LabelMode.NONE) SliderSetting("文字の大きさ", s.labelSizeSp, 8f..18f, format = { "${it.toInt()}sp" }) { v -> u { it.copy(labelSizeSp = v) } }

    SectionHeader("操作とアニメーション")
    SwitchSetting("ピンチで拡大縮小", s.pinchToZoom) { v -> u { it.copy(pinchToZoom = v) } }
    ChoiceSetting("タップした時の動き", TapEffect.entries, s.tapEffect, { it.label }) { v -> u { it.copy(tapEffect = v) } }
    SwitchSetting("タップで光の粒（パーティクル）", s.tapParticles, "OFF にするとアプリをタップしても粒が出なくなります") { v -> u { it.copy(tapParticles = v) } }
    SwitchSetting("起動時ズームアニメ", s.launchZoom, "アイコンからアプリが広がる") { v -> u { it.copy(launchZoom = v) } }
    SwitchSetting("出現アニメーション", s.entranceAnim, "アプリ追加時に中心から飛び出す") { v -> u { it.copy(entranceAnim = v) } }
    SwitchSetting("振動フィードバック", s.haptics) { v -> u { it.copy(haptics = v) } }
}

@Composable
private fun ButtonsTab(s: LauncherSettings, u: Update) {
    SectionHeader("ボタンの割り当て")
    ChoiceSetting("左上", CornerAction.entries, s.cornerTopLeft, { it.label }) { v -> u { it.copy(cornerTopLeft = v) } }
    ChoiceSetting("右上", CornerAction.entries, s.cornerTopRight, { it.label }) { v -> u { it.copy(cornerTopRight = v) } }
    ChoiceSetting("左下", CornerAction.entries, s.cornerBottomLeft, { it.label }) { v -> u { it.copy(cornerBottomLeft = v) } }
    ChoiceSetting("右下", CornerAction.entries, s.cornerBottomRight, { it.label }) { v -> u { it.copy(cornerBottomRight = v) } }
    if (listOf(s.cornerTopLeft, s.cornerTopRight, s.cornerBottomLeft, s.cornerBottomRight).none { it == CornerAction.SETTINGS }) {
        RetroLabel(
            "ⓘ 設定はジェスチャー（初期設定: 何もない所を長押し）から開けます",
            color = LocalPalette.current.yellow, size = 11.sp, maxLines = 3,
        )
    }
    SectionHeader("見た目")
    SliderSetting("ボタンの大きさ", s.cornerSizeDp, 36f..80f, format = { "${it.toInt()}dp" }) { v -> u { it.copy(cornerSizeDp = v) } }
    SliderSetting("不透明度", s.cornerOpacity, 0.2f..1f, format = ::pct) { v -> u { it.copy(cornerOpacity = v) } }
    SliderSetting("球体からの距離", s.cornerSpread, 0.6f..1.4f, format = ::pct) { v -> u { it.copy(cornerSpread = v) } }
    SwitchSetting("ラベル表示", s.cornerLabels) { v -> u { it.copy(cornerLabels = v) } }
    SwitchSetting("ふわふわ浮遊", s.cornerFloat) { v -> u { it.copy(cornerFloat = v) } }
    if (s.cornerFloat) {
        SliderSetting("浮遊の幅", s.cornerFloatAmount, 0.05f..1f, format = { "%.0fdp".format(it * 8f) }) { v -> u { it.copy(cornerFloatAmount = v) } }
        SliderSetting("浮遊の速さ", s.cornerFloatSpeed, 0.2f..3f, format = { "x%.1f".format(it) }) { v -> u { it.copy(cornerFloatSpeed = v) } }
    }
    SwitchSetting("グロー（発光）", s.cornerGlow) { v -> u { it.copy(cornerGlow = v) } }
}

@Composable
private fun LookTab(s: LauncherSettings, u: Update) {
    SectionHeader("カラーテーマ")
    PalettePreset.entries.forEach { preset ->
        val pal = paletteOf(preset)
        val selected = preset == s.palette
        val shape = RoundedCornerShape(10.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(shape)
                .background(Brush.horizontalGradient(listOf(pal.skyTop, pal.skyMid, pal.skyBottom)))
                .border(if (selected) 3.dp else 1.dp, if (selected) pal.yellow else pal.cyan.copy(alpha = 0.5f), shape)
                .clickable { u { it.copy(palette = preset) } }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeonText(preset.label, size = 15.sp, color = Color.White, glow = pal.pink, modifier = Modifier.weight(1f))
            listOf(pal.pink, pal.cyan, pal.purple, pal.yellow, pal.mint).forEach {
                Box(Modifier.padding(start = 4.dp).size(16.dp).clip(RoundedCornerShape(4.dp)).background(it))
            }
            if (selected) RetroLabel("  ◀", color = pal.yellow)
        }
    }
    SectionHeader("文字")
    ChoiceSetting("フォント", FontStyle.entries, s.fontStyle, { it.label }) { v -> u { it.copy(fontStyle = v) } }
    SwitchSetting("色収差エフェクト", s.chromaticText, "文字を赤青にずらしてVHS風に") { v -> u { it.copy(chromaticText = v) } }

    SectionHeader("CRTエフェクト")
    SwitchSetting("走査線", s.scanlines) { v -> u { it.copy(scanlines = v) } }
    if (s.scanlines) SliderSetting("走査線の濃さ", s.scanlineIntensity, 0.02f..0.5f, format = ::pct) { v -> u { it.copy(scanlineIntensity = v) } }
    SwitchSetting("ビネット（周辺減光）", s.vignette) { v -> u { it.copy(vignette = v) } }

    SectionHeader("ウィンドウとアニメーション")
    ChoiceSetting("パネルの開き方", PanelAnim.entries, s.panelAnim, { it.label }) { v -> u { it.copy(panelAnim = v) } }
    SliderSetting("アニメーション速度", s.animSpeed, 0.25f..3f, format = { "x%.2f".format(it) }) { v -> u { it.copy(animSpeed = v) } }
    SliderSetting("パネルの不透明度", s.panelOpacity, 0.4f..1f, format = ::pct) { v -> u { it.copy(panelOpacity = v) } }
    SwitchSetting("Win95風ウィンドウ枠", s.windowChrome, "タイトルバーに _ □ ✕ ボタン") { v -> u { it.copy(windowChrome = v) } }
}

@Composable
private fun ChargeTab(s: LauncherSettings, u: Update, vm: LauncherViewModel) {
    val p = LocalPalette.current
    SectionHeader("充電アニメーション")
    SwitchSetting("充電を始めた時にアニメーション", s.chargeAnim, "ホーム画面を表示している時に電源をつなぐと再生") { v -> u { it.copy(chargeAnim = v) } }
    ChoiceSetting("スタイル", ChargeStyle.entries, s.chargeStyle, { it.label }) { v -> u { it.copy(chargeStyle = v) } }
    SliderSetting("表示する時間", s.chargeDurationSec, 1f..10f, "画面をタップすると途中で閉じます", format = { "%.1f秒".format(it) }) { v -> u { it.copy(chargeDurationSec = v) } }
    SwitchSetting("電源を外した時も表示", s.chargeOnUnplug) { v -> u { it.copy(chargeOnUnplug = v) } }
    SwitchSetting("振動", s.chargeHaptic) { v -> u { it.copy(chargeHaptic = v) } }
    ActionSetting("今すぐ試す", "PREVIEW", color = p.mint) { vm.previewCharge() }
}

@Composable
private fun PerformanceTab(s: LauncherSettings, u: Update) {
    val p = LocalPalette.current
    SectionHeader("軽量モード")
    SwitchSetting(
        "軽量モード", s.liteMode,
        "ぼかし・残像・光の粒・虹色グロー・浮遊・画面エフェクト・ボタンの浮遊を止め、30fps に抑えます。OFF に戻すと元の設定に戻ります",
    ) { v -> u { it.copy(liteMode = v) } }

    SectionHeader("フレームレート")
    ChoiceSetting(
        "最大フレームレート", listOf(0, 60, 45, 30, 20, 15), s.fpsLimit,
        { if (it == 0) "制限なし" else "${it}fps" },
        "下げるほど電池と発熱が楽になります（球体・壁紙・エフェクトのアニメーション）",
    ) { v -> u { it.copy(fpsLimit = v) } }

    SectionHeader("放置したら止める")
    SliderSetting(
        "操作しない時に止めるまで", s.idleSleepSec, 0f..300f,
        "動く壁紙・自動回転・画面エフェクトを止めます。触ればすぐ再開",
        format = { if (it < 1f) "止めない" else "${it.toInt()}秒" },
    ) { v -> u { it.copy(idleSleepSec = if (v < 1f) 0f else v) } }

    SectionHeader("重くなりやすい設定")
    RetroLabel("ボタンで個別に軽くできます", size = 11.sp, color = p.text.copy(alpha = 0.6f))
    ActionSetting("動く壁紙 → グラデーションに", "軽く", "今: ${s.wallpaperType.label}", color = p.cyan) {
        u { it.copy(wallpaperType = WallpaperType.GRADIENT, gradientAnimate = false) }
    }
    ActionSetting("奥のアイコンのぼかし", if (s.dofBlur > 0f) "OFF" else "OFF済", color = p.cyan) { u { it.copy(dofBlur = 0f) } }
    ActionSetting("ワイヤーフレーム", if (s.wireframe) "OFF" else "OFF済", color = p.cyan) { u { it.copy(wireframe = false) } }
    ActionSetting("ネオングロー", if (s.iconGlow) "OFF" else "OFF済", color = p.cyan) { u { it.copy(iconGlow = false) } }
    ActionSetting("走査線・ビネット", if (s.scanlines || s.vignette) "OFF" else "OFF済", color = p.cyan) { u { it.copy(scanlines = false, vignette = false) } }
    SliderSetting("球体のアプリ数の上限", s.sphereMaxApps.toFloat(), 8f..200f, "少ないほど軽い", format = { "${it.toInt()}" }) { v -> u { it.copy(sphereMaxApps = v.toInt()) } }
}

@Composable
private fun EffectsTab(s: LauncherSettings, u: Update) {
    val p = LocalPalette.current
    val blurOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    SectionHeader("ゴム（ビヨンビヨン）")
    SliderSetting(
        "ゴムの柔らかさ", s.rubber, 0f..1f,
        "アイコンがバネのように遅れてついてきて、伸び縮みします。ボタンの弾みにも影響",
        format = { if (it < 0.01f) "OFF" else pct(it) },
    ) { v -> u { it.copy(rubber = v) } }
    if (s.rubber > 0.01f) {
        SliderSetting("弾み", s.rubberBounce, 0f..1f, "大きいほど長くビヨンビヨン揺れる", format = ::pct) { v -> u { it.copy(rubberBounce = v) } }
        SliderSetting("伸び縮み", s.rubberStretch, 0f..1f, "動く向きに伸びる強さ", format = { if (it < 0.01f) "OFF" else pct(it) }) { v -> u { it.copy(rubberStretch = v) } }
        SliderSetting("遅れの最大", s.rubberMaxLag, 0.1f..3f, "アイコン何個ぶんまで遅れてついてくるか", format = { "%.1f個".format(it) }) { v -> u { it.copy(rubberMaxLag = v) } }
        SwitchSetting("速く回した時は揺れを抑える", s.rubberSpeedLimit, "素早いスワイプでもアイコンがぐちゃっとならない") { v -> u { it.copy(rubberSpeedLimit = v) } }
        SwitchSetting("ボタンやアイコンも弾ませる", s.rubberButtons, "四隅のボタン・ドック・一覧のアイコン") { v -> u { it.copy(rubberButtons = v) } }
    }
    ChoiceSetting("タップした時の動き", TapEffect.entries, s.tapEffect, { it.label }) { v -> u { it.copy(tapEffect = v) } }
    SwitchSetting("タップで光の粒（パーティクル）", s.tapParticles, "OFF にするとアプリをタップしても粒が出なくなります") { v -> u { it.copy(tapParticles = v) } }

    SectionHeader("ぼかし")
    if (!blurOk) RetroLabel("※ ぼかしは Android 12 以降で使えます", size = 11.sp, color = p.yellow)
    SliderSetting(
        "奥のアイコンをぼかす", s.dofBlur, 0f..1f, "カメラのピントのような奥行き（被写界深度）",
        format = { if (it < 0.01f) "OFF" else pct(it) },
    ) { v -> u { it.copy(dofBlur = v) } }
    SwitchSetting("パネル表示中に背景をぼかす", s.panelBlur) { v -> u { it.copy(panelBlur = v) } }
    SliderSetting("壁紙のぼかし", s.wallpaperBlur, 0f..1f, format = ::pct) { v -> u { it.copy(wallpaperBlur = v) } }

    SectionHeader("グロー（発光）")
    SwitchSetting("ネオングロー", s.iconGlow) { v -> u { it.copy(iconGlow = v) } }
    if (s.iconGlow) {
        SliderSetting("光の強さ", s.glowIntensity, 0.05f..1f, format = ::pct) { v -> u { it.copy(glowIntensity = v) } }
        SliderSetting("光の大きさ", s.glowSize, 0.3f..3f, format = { "x%.1f".format(it) }) { v -> u { it.copy(glowSize = v) } }
        SwitchSetting("奥のアイコンも光らせる", s.glowAll) { v -> u { it.copy(glowAll = v) } }
        SwitchSetting("ゆっくり明滅", s.glowPulse) { v -> u { it.copy(glowPulse = v) } }
        SwitchSetting("虹色グロー", s.rainbowGlow, "アイコンの光が虹色に変化し続ける") { v -> u { it.copy(rainbowGlow = v) } }
    }

    SectionHeader("残像（トレイル）")
    SliderSetting("残像の長さ", s.trailLength, 0f..1f, "回すと軌跡が残る", format = { if (it < 0.01f) "OFF" else pct(it) }) { v -> u { it.copy(trailLength = v) } }
    if (s.trailLength > 0.01f) {
        ChoiceSetting("残像の見た目", TrailStyle.entries, s.trailStyle, { it.label }) { v -> u { it.copy(trailStyle = v) } }
        SliderSetting("残像の間隔", s.trailSpacing, 0f..1f, "大きいほど残像が離れて長く伸びる", format = ::pct) { v -> u { it.copy(trailSpacing = v) } }
    }

    SectionHeader("アイコンの動き")
    SliderSetting("ふわふわ浮遊", s.iconFloat, 0f..1f, format = { if (it < 0.01f) "OFF" else pct(it) }) { v -> u { it.copy(iconFloat = v) } }

    SectionHeader("画面全体")
    SliderSetting("フィルムグレイン", s.filmGrain, 0f..1f, "古い映像のようなザラザラ", format = { if (it < 0.01f) "OFF" else pct(it) }) { v -> u { it.copy(filmGrain = v) } }
    SwitchSetting("VHSトラッキング", s.vhsTracking, "ビデオテープのノイズの帯が流れる") { v -> u { it.copy(vhsTracking = v) } }
    SwitchSetting("ランダムグリッチ", s.glitchOverlay, "ときどき画面にノイズが走る") { v -> u { it.copy(glitchOverlay = v) } }
    SwitchSetting("走査線", s.scanlines) { v -> u { it.copy(scanlines = v) } }
    SwitchSetting("ビネット（周辺減光）", s.vignette) { v -> u { it.copy(vignette = v) } }

    SectionHeader("プリセット")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RetroButton("控えめ", { u { it.copy(rubber = 0.15f, tapEffect = TapEffect.BOUNCE, tapParticles = false, iconFloat = 0f, trailLength = 0f, rainbowGlow = false, filmGrain = 0f, vhsTracking = false, glitchOverlay = false) } }, color = p.cyan)
        RetroButton("ぷるぷる", { u { it.copy(rubber = 0.85f, tapEffect = TapEffect.JELLY, tapParticles = true, iconFloat = 0.4f) } }, color = p.pink)
        RetroButton("VHS", { u { it.copy(filmGrain = 0.6f, vhsTracking = true, glitchOverlay = true, scanlines = true, chromaticText = true) } }, color = p.yellow)
        RetroButton("ネオン軌跡", { u { it.copy(iconGlow = true, glowIntensity = 0.9f, glowSize = 1.4f, trailLength = 0.8f, trailStyle = TrailStyle.NEON, rainbowGlow = true) } }, color = p.purple)
        RetroButton("全部盛り", { u { it.copy(rubber = 0.9f, rubberBounce = 0.8f, tapEffect = TapEffect.JELLY, tapParticles = true, iconFloat = 0.6f, trailLength = 0.7f, rainbowGlow = true, glowPulse = true, dofBlur = 0.6f, filmGrain = 0.4f, vhsTracking = true, glitchOverlay = true) } }, color = p.mint)
    }
}

@Composable
private fun WallpaperTab(s: LauncherSettings, u: Update) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = LocalPalette.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { SystemActions.importWallpaper(context, uri) }
            if (ok) u { it.copy(wallpaperType = WallpaperType.IMAGE, wallpaperImageVersion = System.currentTimeMillis()) }
            else Toast.makeText(context, "画像を読み込めませんでした", Toast.LENGTH_SHORT).show()
        }
    }
    SectionHeader("壁紙の種類")
    ChoiceSetting("種類", WallpaperType.entries, s.wallpaperType, { it.label }) { v -> u { it.copy(wallpaperType = v) } }
    ActionSetting("画像を選ぶ", "PICK", "ギャラリーから壁紙を選択", color = p.mint) {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    if (s.wallpaperType == WallpaperType.IMAGE) {
        ActionSetting("OSの壁紙にも設定", "APPLY", "ロック画面などにも同じ画像を使う") {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { SystemActions.applyAsSystemWallpaper(context) }
                Toast.makeText(context, if (ok) "設定しました" else "設定できませんでした", Toast.LENGTH_SHORT).show()
            }
        }
    }
    SectionHeader("調整")
    SliderSetting("暗くする", s.wallpaperDim, 0f..0.9f, format = ::pct) { v -> u { it.copy(wallpaperDim = v) } }
    SliderSetting("ぼかし", s.wallpaperBlur, 0f..1f, "Android 12 以降", format = ::pct) { v -> u { it.copy(wallpaperBlur = v) } }

    if (s.wallpaperType.animated) {
        SectionHeader("アニメーション")
        SliderSetting("背景の動く速さ", s.gridSpeed, 0.1f..4f, format = { "x%.1f".format(it) }) { v -> u { it.copy(gridSpeed = v) } }
    }
    WallpaperDetailSettings(s, u)
    if (s.wallpaperType == WallpaperType.SOLID) {
        SectionHeader("単色")
        ColorSetting("背景色", s.solidColor) { v -> u { it.copy(solidColor = v) } }
    }
}

@Composable
private fun ClockTab(s: LauncherSettings, u: Update) {
    SectionHeader("時計")
    SwitchSetting("時計を表示", s.showClock) { v -> u { it.copy(showClock = v) } }
    ChoiceSetting("スタイル", ClockStyle.entries, s.clockStyle, { it.label }) { v -> u { it.copy(clockStyle = v) } }
    SwitchSetting("24時間表示", s.clock24h) { v -> u { it.copy(clock24h = v) } }
    SwitchSetting("秒を表示", s.showSeconds) { v -> u { it.copy(showSeconds = v) } }
    SwitchSetting("日付を表示", s.showDate) { v -> u { it.copy(showDate = v) } }
    SliderSetting("時計の大きさ", s.clockScale, 0.5f..1.6f, format = ::pct) { v -> u { it.copy(clockScale = v) } }
    ChoiceSetting("時計をタップした時", GestureAction.entries, s.clockTapAction, { it.label }) { v -> u { it.copy(clockTapAction = v) } }
}

@Composable
private fun SearchTab(s: LauncherSettings, u: Update) {
    SectionHeader("検索")
    SwitchSetting("開いたらキーボードを表示", s.searchAutoKeyboard) { v -> u { it.copy(searchAutoKeyboard = v) } }
    SwitchSetting("Enterで一番上を起動", s.searchEnterLaunches) { v -> u { it.copy(searchEnterLaunches = v) } }
    SwitchSetting("パッケージ名でも検索", s.searchPackageNames) { v -> u { it.copy(searchPackageNames = v) } }
    SwitchSetting("見つからない時にWEB検索", s.searchWebFallback) { v -> u { it.copy(searchWebFallback = v) } }
    SwitchSetting("非表示アプリも検索に出す", s.searchShowHidden) { v -> u { it.copy(searchShowHidden = v) } }
    SliderSetting("列の数", s.searchColumns.toFloat(), 3f..6f, steps = 2, format = { "${it.toInt()}列" }) { v -> u { it.copy(searchColumns = v.toInt()) } }
}

@Composable
private fun RecentTab(s: LauncherSettings, u: Update, vm: LauncherViewModel) {
    val context = LocalContext.current
    val p = LocalPalette.current
    SectionHeader("最近使ったアプリ")
    ChoiceSetting(
        "履歴の取得元", RecentSource.entries, s.recentSource, { it.label },
        "ランチャー: このランチャーから起動したアプリ / システム: 端末全体の使用履歴（許可が必要）",
    ) { v -> u { it.copy(recentSource = v) } }
    if (s.recentSource == RecentSource.SYSTEM) {
        val granted = SystemActions.hasUsageAccess(context)
        ActionSetting(
            "使用状況へのアクセス", if (granted) "許可済み" else "許可する",
            if (granted) "OK" else "システム履歴を読むために必要です",
            color = if (granted) p.mint else p.yellow,
        ) { SystemActions.openUsageAccessSettings(context) }
    }
    SliderSetting("表示する件数", s.recentCount.toFloat(), 5f..40f, format = { "${it.toInt()}件" }) { v -> u { it.copy(recentCount = v.toInt()) } }
    SwitchSetting("経過時間を表示", s.recentShowTime) { v -> u { it.copy(recentShowTime = v) } }
    ActionSetting("ランチャーの履歴を消去", "CLEAR", color = p.pink) {
        vm.clearRecent()
        Toast.makeText(context, "消去しました", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun FolderDockTab(
    s: LauncherSettings, u: Update, data: LauncherData, apps: List<AppInfo>,
    icons: Map<String, ImageBitmap>, vm: LauncherViewModel,
) {
    val p = LocalPalette.current
    SectionHeader("フォルダ")
    SliderSetting("フォルダ内の列数", s.folderColumns.toFloat(), 3f..6f, steps = 2, format = { "${it.toInt()}列" }) { v -> u { it.copy(folderColumns = v.toInt()) } }
    SwitchSetting("フォルダ内でアプリ名を表示", s.folderShowLabels) { v -> u { it.copy(folderShowLabels = v) } }
    InfoSetting("フォルダ数", "${data.folders.size}")

    SectionHeader("ドック")
    SwitchSetting("ドックを表示", s.showDock) { v -> u { it.copy(showDock = v) } }
    SliderSetting("ドックのアイコンサイズ", s.dockIconSizeDp, 36f..72f, format = { "${it.toInt()}dp" }) { v -> u { it.copy(dockIconSizeDp = v) } }
    SwitchSetting("すりガラス背景", s.dockGlass) { v -> u { it.copy(dockGlass = v) } }
    RetroLabel("ドックのアプリ（最大${LauncherViewModel.DOCK_MAX}個・アプリ長押し→「ドックに追加」）", size = 11.sp, color = p.cyan, maxLines = 2)
    Spacer(Modifier.height(6.dp))
    val byKey = apps.associateBy { it.key }
    data.dockApps.mapNotNull { byKey[it] }.forEach { app ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icons[app.key], app.label, 32.dp)
            Spacer(Modifier.width(10.dp))
            RetroLabel(app.label, modifier = Modifier.weight(1f))
            RetroButton("◀", { vm.moveDock(app.key, -1) }, color = p.cyan)
            Spacer(Modifier.width(4.dp))
            RetroButton("▶", { vm.moveDock(app.key, 1) }, color = p.cyan)
            Spacer(Modifier.width(4.dp))
            RetroButton("✕", { vm.toggleDock(app.key) }, color = p.pink)
        }
    }
}

@Composable
private fun GestureTab(s: LauncherSettings, u: Update, apps: List<AppInfo>, onChooseApp: (GestureSlot) -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val a11y = remember { SpecialPermission.ACCESSIBILITY.isGranted(context) }
    SectionHeader("ジェスチャーの割り当て")
    RetroLabel(
        "🔒 の付いた動作は 権限 → ユーザー補助 を ON にすると使えます",
        size = 11.sp, color = p.text.copy(alpha = 0.6f), maxLines = 2,
    )
    GestureSlot.entries.forEach { slot ->
        val current = s.actionFor(slot)
        ChoiceSetting(
            slot.label, GestureAction.entries, current,
            { a -> if (a.needsAccessibility) "🔒" + a.label else a.label },
        ) { v ->
            if (v == GestureAction.LAUNCH_APP) onChooseApp(slot) else u { it.withAction(slot, v) }
        }
        if (current == GestureAction.LAUNCH_APP) {
            val name = s.gestureApps[slot.name]?.let { key -> apps.firstOrNull { it.key == key }?.label } ?: "未選択"
            ActionSetting("  └ 起動するアプリ: $name", "変更", color = p.mint) { onChooseApp(slot) }
        }
        if (current.needsAccessibility && !a11y) {
            ActionSetting("  └ ユーザー補助が OFF です", "ON にする", color = p.yellow) {
                SpecialPermission.ACCESSIBILITY.request(context)
            }
        }
    }
    if (!Migration.settingsReachable(s)) {
        RetroLabel("⚠ 設定を開く手段が必要なので、長押しは「設定」に戻ります", color = p.yellow, size = 11.sp, maxLines = 2)
    }
    SectionHeader("いつでも使える操作")
    RetroLabel(
        "・1本指スワイプ（球体の上）: 球体を回転\n・2本指ピンチ: 球体の拡大縮小\n・アイコン長押し: アプリメニュー\n・ホームボタン（パネル表示中）: パネルを閉じる",
        size = 12.sp, maxLines = 6, color = p.cyan, modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun PermissionsTab(s: LauncherSettings, u: Update) {
    val context = LocalContext.current
    val p = LocalPalette.current
    // 設定アプリから戻ってきたら状態を読み直す
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    SectionHeader("特別な権限")
    RetroLabel(
        "許可すると使える機能が増えます。どれも任意で、いつでも取り消せます。",
        size = 11.sp, color = p.text.copy(alpha = 0.6f), maxLines = 2,
    )
    SpecialPermission.entries.forEach { perm ->
        val granted = remember(tick) { perm.isGranted(context) }
        val shape = RoundedCornerShape(10.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .clip(shape)
                .background((if (granted) p.mint else p.pink).copy(alpha = 0.08f))
                .border(1.dp, (if (granted) p.mint else p.pink).copy(alpha = 0.6f), shape)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RetroGlyph(Glyph.KEY, if (granted) p.mint else p.pink, Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                RetroLabel(perm.title, size = 14.sp, maxLines = 2)
                RetroLabel(perm.unlocks, size = 11.sp, color = p.text.copy(alpha = 0.6f), maxLines = 3)
            }
            Spacer(Modifier.width(8.dp))
            RetroButton(
                if (granted) "許可済み" else "許可する",
                { perm.request(context) },
                color = if (granted) p.mint else p.yellow,
                filled = !granted,
            )
        }
    }
    SectionHeader("通知バッジ")
    SwitchSetting("アイコンに通知バッジを表示", s.showBadges, "「通知へのアクセス」が必要") { v -> u { it.copy(showBadges = v) } }
    if (s.showBadges) SwitchSetting("件数を表示", s.badgeShowCount, "OFF なら点だけ") { v -> u { it.copy(badgeShowCount = v) } }
}

@Composable
private fun WidgetsTab(s: LauncherSettings, u: Update, data: LauncherData, vm: LauncherViewModel) {
    val p = LocalPalette.current
    var confirm by remember { mutableStateOf(false) }
    SectionHeader("ウィジェット画面")
    RetroLabel(
        "右下のボタン（初期設定）で開きます。「配置を編集」でドラッグ移動・◢でサイズ変更・✕で削除。",
        size = 11.sp, color = p.text.copy(alpha = 0.6f), maxLines = 3,
    )
    SwitchSetting("ネオン枠", s.widgetFrame) { v -> u { it.copy(widgetFrame = v) } }
    SwitchSetting("編集中にグリッドを表示", s.widgetGrid) { v -> u { it.copy(widgetGrid = v) } }
    InfoSetting("置いているウィジェット", "${data.widgets.size}")
    ActionSetting("ウィジェット画面を開く", "OPEN", color = p.mint) { vm.openPanel(Panel.WIDGETS) }
    ActionSetting("すべて削除", if (confirm) "本当に?" else "CLEAR", color = p.pink) {
        if (confirm) { vm.clearWidgets(); confirm = false } else confirm = true
    }
}

@Composable
private fun SystemTab(s: LauncherSettings, u: Update, vm: LauncherViewModel) {
    val context = LocalContext.current
    val p = LocalPalette.current
    SectionHeader("ホームアプリ")
    val isDefault = SystemActions.isDefaultHome(context)
    ActionSetting(
        "デフォルトのホームに設定", if (isDefault) "設定済み" else "設定する",
        if (isDefault) "このランチャーがホームです" else "ホームボタンでこのランチャーが開くようにする",
        color = if (isDefault) p.mint else p.yellow,
    ) { SystemActions.safeStart(context, SystemActions.requestDefaultHome(context)) }

    SectionHeader("表示")
    SwitchSetting("ステータスバーを隠す", s.hideStatusBar) { v -> u { it.copy(hideStatusBar = v) } }
    SwitchSetting("ナビゲーションバーを隠す", s.hideNavBar) { v -> u { it.copy(hideNavBar = v) } }
    ChoiceSetting("画面の向き", OrientationMode.entries, s.orientation, { it.label }) { v -> u { it.copy(orientation = v) } }

    SectionHeader("メンテナンス")
    ActionSetting("アプリ一覧を再読み込み", "REFRESH") {
        vm.repo.refresh()
        Toast.makeText(context, "再読み込みしました", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun BackupTab(vm: LauncherViewModel) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray()) }
                }.isSuccess
            }
            Toast.makeText(context, if (ok) "書き出しました" else "書き出しに失敗しました", Toast.LENGTH_SHORT).show()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            val ok = text != null && vm.importJson(text)
            Toast.makeText(context, if (ok) "復元しました" else "読み込めないファイルです", Toast.LENGTH_SHORT).show()
        }
    }
    SectionHeader("バックアップ")
    ActionSetting("設定を書き出す", "EXPORT", "設定・フォルダ・ドック・スフィア構成をJSONで保存", color = p.mint) {
        exporter.launch("wonderful_launcher_backup.json")
    }
    ActionSetting("設定を読み込む", "IMPORT", "書き出したJSONから復元", color = p.cyan) {
        importer.launch(arrayOf("application/json", "text/plain", "*/*"))
    }
    SectionHeader("リセット")
    ActionSetting(
        "設定を初期値に戻す", if (confirmReset) "本当に?" else "RESET",
        "フォルダやスフィアのアプリはそのまま", color = p.pink,
    ) {
        if (confirmReset) {
            vm.resetSettings(); confirmReset = false
            Toast.makeText(context, "初期化しました", Toast.LENGTH_SHORT).show()
        } else confirmReset = true
    }
}

@Composable
private fun AboutTab(data: LauncherData, apps: List<AppInfo>) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    Spacer(Modifier.height(20.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        NeonText("ｗｏｎｄｅｒｆｕｌ", size = 28.sp)
        NeonText("ｌａｕｎｃｈｅｒ", size = 28.sp, glow = p.cyan)
        Spacer(Modifier.height(6.dp))
        RetroLabel("ｖａｐｏｒｗａｖｅ  ｅｄｉｔｉｏｎ", color = p.pink)
    }
    SectionHeader("ステータス")
    InfoSetting("バージョン", version)
    InfoSetting("インストール済みアプリ", "${apps.size}")
    InfoSetting("スフィア内のアプリ", "${data.sphereApps.size}")
    InfoSetting("非表示のアプリ", "${data.hiddenApps.size}")
    InfoSetting("フォルダ", "${data.folders.size}")
    InfoSetting("累計起動回数", "${data.launchCounts.values.sum()}")
    Spacer(Modifier.height(16.dp))
    RetroLabel("ＡＥＳＴＨＥＴＩＣ　ｆｏｒ　ｙｏｕｒ　ｐｈｏｎｅ", color = p.cyan, size = 12.sp, maxLines = 2)
}

// ─────────────────────────────────────────────
// アプリ管理（スフィアへの出し入れと非表示）
// ─────────────────────────────────────────────

@Composable
private fun AppsTab(data: LauncherData, apps: List<AppInfo>, icons: Map<String, ImageBitmap>, vm: LauncherViewModel) {
    val p = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    val q = normalizeForSearch(query.trim())
    val sphere = remember(data.sphereApps) { data.sphereApps.toSet() }
    val list = remember(apps, q) { apps.filter { matchScore(it, q, true) > 0 } }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(12.dp)) {
            RetroTextField(query, { query = it }, "アプリを絞り込み…", Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                RetroButton("全部入れる", { vm.addAllToSphere() }, color = p.mint)
                RetroButton("全部外す", { vm.clearSphere() }, color = p.pink)
                RetroLabel("${sphere.size}/${apps.size}", color = p.yellow, size = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            RetroLabel("● 球体に表示   ◌ 非表示（検索・球体から隠す）", size = 11.sp, color = p.cyan)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            items(list, key = { it.key }) { app ->
                val inSphere = app.key in sphere
                val hidden = app.key in data.hiddenApps
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(icons[app.key], app.label, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        RetroLabel(app.label, color = if (hidden) p.text.copy(alpha = 0.4f) else p.text)
                        if (app.isWorkProfile) RetroLabel("WORK", size = 10.sp, color = p.yellow)
                    }
                    RetroLabel("球体", size = 10.sp, color = p.cyan)
                    Spacer(Modifier.width(4.dp))
                    RetroSwitch(inSphere) { vm.toggleSphere(app.key) }
                    Spacer(Modifier.width(10.dp))
                    RetroLabel("隠す", size = 10.sp, color = p.pink)
                    Spacer(Modifier.width(4.dp))
                    RetroSwitch(hidden) { vm.toggleHidden(app.key) }
                }
            }
        }
    }
}
