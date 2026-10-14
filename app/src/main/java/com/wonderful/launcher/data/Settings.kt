package com.wonderful.launcher.data

import kotlinx.serialization.Serializable

/** スフィアの並べ方 */
enum class SphereLayout(val label: String) {
    SPHERE("球体"),
    RING("リング"),
    HELIX("螺旋"),
    GALAXY("銀河"),
}

enum class IconShape(val label: String) {
    CIRCLE("円"),
    SQUIRCLE("スクワークル"),
    ROUNDED("角丸"),
    HEXAGON("六角形"),
    ORIGINAL("オリジナル"),
}

enum class LabelMode(val label: String) {
    NONE("非表示"),
    FRONT("前面のみ"),
    ALL("すべて"),
}

enum class SortOrder(val label: String) {
    NAME("名前順"),
    RECENT_INSTALL("新しい順"),
    MOST_LAUNCHED("よく使う順"),
    SHUFFLE("シャッフル"),
}

enum class WallpaperType(val label: String) {
    VAPOR_GRID("ヴェイパー"),
    SYSTEM("システム"),
    IMAGE("画像"),
    GRADIENT("グラデ"),
    SOLID("単色"),
}

enum class ClockStyle(val label: String) {
    NEON("ネオン"),
    CHROME("クローム"),
    LCD("液晶"),
}

/** 四隅のボタンに割り当てる機能 */
enum class CornerAction(val label: String) {
    SEARCH("検索"),
    SETTINGS("設定"),
    RECENT("最近"),
    FOLDERS("フォルダ"),
    NONE("なし"),
}

enum class GestureAction(val label: String) {
    NONE("なし"),
    SEARCH("検索"),
    SETTINGS("設定"),
    RECENT("最近"),
    FOLDERS("フォルダ"),
    NOTIFICATIONS("通知"),
    RESET_SPHERE("回転リセット"),
    SHUFFLE_SPHERE("シャッフル"),
}

enum class PalettePreset(val label: String) {
    VAPORWAVE("Vaporwave"),
    SYNTHWAVE("Synthwave"),
    OUTRUN("Outrun"),
    MIAMI("Miami"),
    SEAPUNK("Seapunk"),
    MACPLUS("Mac Plus"),
}

enum class PanelAnim(val label: String) {
    ZOOM("ズーム"),
    SLIDE("スライド"),
    GLITCH("グリッチ"),
    FADE("フェード"),
}

enum class RecentSource(val label: String) {
    LAUNCHER("ランチャー"),
    SYSTEM("システム"),
}

enum class FontStyle(val label: String) {
    MONO("等幅"),
    SANS("ゴシック"),
    SERIF("明朝"),
}

enum class OrientationMode(val label: String) {
    PORTRAIT("縦固定"),
    AUTO("自動"),
    LANDSCAPE("横固定"),
}

/**
 * ランチャーの全設定。値を追加する場合はデフォルト値を必ず付ける
 * （古いバックアップやバージョンから読み込んでも壊れないように）。
 */
@Serializable
data class LauncherSettings(
    // ── スフィア ──
    val sphereLayout: SphereLayout = SphereLayout.SPHERE,
    val sphereSize: Float = 0.85f,
    val iconSizeDp: Float = 54f,
    val perspective: Float = 0.55f,
    val depthFade: Float = 0.7f,
    val depthScale: Float = 0.55f,
    val dragSensitivity: Float = 1f,
    val inertia: Float = 0.85f,
    val autoRotate: Boolean = true,
    val autoRotateSpeed: Float = 0.25f,
    val autoRotateTilt: Float = 18f,
    val autoRotateResumeSec: Float = 2.5f,
    val reverseAutoRotate: Boolean = false,
    val labelMode: LabelMode = LabelMode.FRONT,
    val labelSizeSp: Float = 11f,
    val iconShape: IconShape = IconShape.CIRCLE,
    val iconGlow: Boolean = true,
    val glowIntensity: Float = 0.6f,
    val iconRing: Boolean = true,
    val sortOrder: SortOrder = SortOrder.NAME,
    val shuffleSeed: Int = 0,
    val wireframe: Boolean = true,
    val wireframeOpacity: Float = 0.35f,
    val pinchToZoom: Boolean = true,
    val tapBounce: Boolean = true,
    val launchZoom: Boolean = true,
    val entranceAnim: Boolean = true,
    val focusFrontIcon: Boolean = true,
    val haptics: Boolean = true,
    val sphereMaxApps: Int = 80,

    // ── 四隅ボタン ──
    val cornerTopLeft: CornerAction = CornerAction.RECENT,
    val cornerTopRight: CornerAction = CornerAction.SEARCH,
    val cornerBottomLeft: CornerAction = CornerAction.FOLDERS,
    val cornerBottomRight: CornerAction = CornerAction.SETTINGS,
    val cornerSizeDp: Float = 54f,
    val cornerOpacity: Float = 0.95f,
    val cornerSpread: Float = 1f,
    val cornerLabels: Boolean = true,
    val cornerFloat: Boolean = true,
    val cornerGlow: Boolean = true,

    // ── 見た目 ──
    val palette: PalettePreset = PalettePreset.VAPORWAVE,
    val fontStyle: FontStyle = FontStyle.MONO,
    val scanlines: Boolean = true,
    val scanlineIntensity: Float = 0.12f,
    val vignette: Boolean = true,
    val chromaticText: Boolean = true,
    val animSpeed: Float = 1f,
    val panelAnim: PanelAnim = PanelAnim.ZOOM,
    val panelOpacity: Float = 0.9f,
    val windowChrome: Boolean = true,

    // ── 壁紙 ──
    val wallpaperType: WallpaperType = WallpaperType.VAPOR_GRID,
    val wallpaperImageVersion: Long = 0L,
    val wallpaperDim: Float = 0.15f,
    val wallpaperBlur: Float = 0f,
    val gridSpeed: Float = 1f,
    val showSun: Boolean = true,
    val showStars: Boolean = true,
    val showMountains: Boolean = true,
    val sunStripes: Boolean = true,
    val solidColor: Long = 0xFF1A0033,
    val gradientAngle: Float = 90f,

    // ── 時計 ──
    val showClock: Boolean = true,
    val clock24h: Boolean = true,
    val showSeconds: Boolean = false,
    val showDate: Boolean = true,
    val clockStyle: ClockStyle = ClockStyle.NEON,
    val clockScale: Float = 1f,
    val clockTapOpensAlarm: Boolean = true,

    // ── 検索 ──
    val searchAutoKeyboard: Boolean = true,
    val searchPackageNames: Boolean = false,
    val searchEnterLaunches: Boolean = true,
    val searchWebFallback: Boolean = true,
    val searchColumns: Int = 4,
    val searchShowHidden: Boolean = false,

    // ── 最近 ──
    val recentSource: RecentSource = RecentSource.LAUNCHER,
    val recentCount: Int = 15,
    val recentShowTime: Boolean = true,

    // ── フォルダ ──
    val folderColumns: Int = 4,
    val folderShowLabels: Boolean = true,

    // ── ドック ──
    val showDock: Boolean = true,
    val dockIconSizeDp: Float = 50f,
    val dockGlass: Boolean = true,

    // ── ジェスチャー ──
    val doubleTapAction: GestureAction = GestureAction.NOTIFICATIONS,
    val longPressAction: GestureAction = GestureAction.SETTINGS,

    // ── システム ──
    val hideStatusBar: Boolean = false,
    val hideNavBar: Boolean = false,
    val orientation: OrientationMode = OrientationMode.PORTRAIT,
)

@Serializable
data class Folder(
    val id: String,
    val name: String,
    val colorIndex: Int = 0,
    val apps: List<String> = emptyList(),
)

@Serializable
data class RecentEntry(val key: String, val time: Long)

/** ディスクに保存されるランチャーの全状態 */
@Serializable
data class LauncherData(
    val settings: LauncherSettings = LauncherSettings(),
    val sphereApps: List<String> = emptyList(),
    val sphereInitialized: Boolean = false,
    val folders: List<Folder> = emptyList(),
    val dockApps: List<String> = emptyList(),
    val hiddenApps: Set<String> = emptySet(),
    val recent: List<RecentEntry> = emptyList(),
    val launchCounts: Map<String, Int> = emptyMap(),
)
