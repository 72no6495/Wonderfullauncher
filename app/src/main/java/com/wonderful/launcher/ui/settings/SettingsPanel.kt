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
    WALLPAPER("壁紙"),
    CLOCK("時計"),
    SEARCH("検索"),
    RECENT("履歴"),
    FOLDER("フォルダ/ドック"),
    GESTURE("ジェスチャー"),
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
    var tab by rememberSaveable { mutableStateOf(Tab.SPHERE) }
    val p = LocalPalette.current
    val s = data.settings
    val update: ((LauncherSettings) -> LauncherSettings) -> Unit = { vm.updateSettings(it) }

    RetroWindow("CONFIG.SYS", onClose, modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Tab.entries.forEach { t -> RetroChip(t.label, t == tab, { tab = t }) }
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
                else -> ScrollTab {
                    when (t) {
                        Tab.SPHERE -> SphereTab(s, update, vm, onResetSphere)
                        Tab.BUTTONS -> ButtonsTab(s, update)
                        Tab.LOOK -> LookTab(s, update)
                        Tab.WALLPAPER -> WallpaperTab(s, update)
                        Tab.CLOCK -> ClockTab(s, update)
                        Tab.SEARCH -> SearchTab(s, update)
                        Tab.RECENT -> RecentTab(s, update, vm)
                        Tab.FOLDER -> FolderDockTab(s, update, data, apps, icons, vm)
                        Tab.GESTURE -> GestureTab(s, update)
                        Tab.SYSTEM -> SystemTab(s, update, vm)
                        Tab.BACKUP -> BackupTab(vm)
                        Tab.ABOUT -> AboutTab(data, apps)
                        Tab.APPS -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrollTab(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
    SwitchSetting("タップでバウンド", s.tapBounce) { v -> u { it.copy(tapBounce = v) } }
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
            "⚠ 設定ボタンがありません。設定はホーム画面の長押し（ジェスチャー設定）から開けます",
            color = LocalPalette.current.yellow, size = 11.sp, maxLines = 3,
        )
    }
    SectionHeader("見た目")
    SliderSetting("ボタンの大きさ", s.cornerSizeDp, 36f..80f, format = { "${it.toInt()}dp" }) { v -> u { it.copy(cornerSizeDp = v) } }
    SliderSetting("不透明度", s.cornerOpacity, 0.2f..1f, format = ::pct) { v -> u { it.copy(cornerOpacity = v) } }
    SliderSetting("球体からの距離", s.cornerSpread, 0.6f..1.4f, format = ::pct) { v -> u { it.copy(cornerSpread = v) } }
    SwitchSetting("ラベル表示", s.cornerLabels) { v -> u { it.copy(cornerLabels = v) } }
    SwitchSetting("ふわふわ浮遊", s.cornerFloat) { v -> u { it.copy(cornerFloat = v) } }
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

    if (s.wallpaperType == WallpaperType.VAPOR_GRID) {
        SectionHeader("ヴェイパー風景")
        SliderSetting("グリッドの速さ", s.gridSpeed, 0.1f..4f, format = { "x%.1f".format(it) }) { v -> u { it.copy(gridSpeed = v) } }
        SwitchSetting("太陽", s.showSun) { v -> u { it.copy(showSun = v) } }
        if (s.showSun) SwitchSetting("太陽の横縞", s.sunStripes) { v -> u { it.copy(sunStripes = v) } }
        SwitchSetting("山", s.showMountains) { v -> u { it.copy(showMountains = v) } }
        SwitchSetting("星", s.showStars) { v -> u { it.copy(showStars = v) } }
    }
    if (s.wallpaperType == WallpaperType.GRADIENT) {
        SectionHeader("グラデーション")
        SliderSetting("角度", s.gradientAngle, 0f..360f, format = { "${it.toInt()}°" }) { v -> u { it.copy(gradientAngle = v) } }
        RetroLabel("色は「見た目 → カラーテーマ」に連動します", size = 11.sp, color = p.text.copy(alpha = 0.6f))
    }
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
    SwitchSetting("タップでアラームを開く", s.clockTapOpensAlarm) { v -> u { it.copy(clockTapOpensAlarm = v) } }
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
private fun GestureTab(s: LauncherSettings, u: Update) {
    SectionHeader("ホーム画面のジェスチャー")
    ChoiceSetting("何もない所をダブルタップ", GestureAction.entries, s.doubleTapAction, { it.label }) { v -> u { it.copy(doubleTapAction = v) } }
    ChoiceSetting("何もない所を長押し", GestureAction.entries, s.longPressAction, { it.label }) { v -> u { it.copy(longPressAction = v) } }
    RetroLabel(
        "・スワイプ: 球体を回転\n・2本指ピンチ: 球体の拡大縮小\n・アイコン長押し: アプリメニュー\n・ホームボタン: パネルを閉じる / 球体をスピン",
        size = 12.sp, maxLines = 6, color = LocalPalette.current.cyan, modifier = Modifier.padding(top = 12.dp),
    )
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
        NeonText("ＷＯＮＤＥＲＦＵＬ", size = 26.sp)
        NeonText("ＬＡＵＮＣＨＥＲ", size = 26.sp, glow = p.cyan)
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
