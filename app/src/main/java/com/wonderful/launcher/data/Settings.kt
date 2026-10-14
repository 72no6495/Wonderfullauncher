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

enum class WallpaperType(val label: String, val animated: Boolean = true) {
    VAPOR_GRID("ヴェイパー"),
    SYSTEM("システム", animated = false),
    IMAGE("画像", animated = false),
    GRADIENT("グラデ"),
    SOLID("単色", animated = false),
    UNDERWATER("水中"),
    MAZE("迷路"),
    GLITCH("グリッチ"),
    MATRIX("デジタル雨"),
    STARFIELD("ワープ"),
    LAVA("ラバランプ"),
    AURORA("オーロラ"),
    LIFE("ライフゲーム"),
}

/** 残像の描き方 */
enum class TrailStyle(val label: String) {
    GHOST("アイコン"),
    NEON("ネオン光"),
    LINE("光の線"),
}

enum class MazeSolver(val label: String) {
    BFS("幅優先"),
    DFS("深さ優先"),
    ASTAR("A*"),
    RIGHT_HAND("右手法"),
}

enum class MazeGenerator(val label: String) {
    BACKTRACK("穴掘り"),
    PRIM("プリム"),
}

enum class MatrixCharset(val label: String, val chars: String) {
    KATAKANA("カタカナ", "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜｦﾝ0123456789"),
    BINARY("2進数", "01"),
    HEX("16進数", "0123456789ABCDEF"),
    SYMBOLS("記号", "★☆♪♫◆◇○●□■△▽※♥♦♣♠"),
}

enum class ColorMode(val label: String) {
    PALETTE("テーマ色"),
    CLASSIC("緑"),
    RAINBOW("虹"),
}

/** ライフゲームのルール（B=誕生する近傍数 / S=生き残る近傍数） */
enum class LifeRule(val label: String, val birth: String, val survive: String) {
    CONWAY("コンウェイ", "3", "23"),
    HIGHLIFE("ハイライフ", "36", "23"),
    DAY_NIGHT("昼と夜", "3678", "34678"),
    SEEDS("シーズ", "2", ""),
    MAZE("迷路", "3", "12345"),
    CORAL("サンゴ", "3", "45678"),
    NO_DEATH("不死", "3", "012345678"),
    TWO_BY_TWO("2x2", "36", "125"),
    MOVE("ムーブ", "368", "245"),
    AMOEBA("アメーバ", "357", "1358"),
    REPLICATOR("複製", "1357", "1357"),
    DIAMOEBA("ダイアメーバ", "35678", "5678"),
    ANNEAL("アニール", "4678", "35678"),
}

enum class LifeColor(val label: String) {
    AGE("年齢"),
    RAINBOW("虹"),
    NEIGHBORS("近傍数"),
    HEAT("ヒート"),
    MONO("単色"),
}

enum class CellShape(val label: String) {
    SQUARE("四角"),
    ROUND("丸"),
    DIAMOND("ひし形"),
    PIXEL("ドット"),
}

enum class LifeSeed(val label: String) {
    RANDOM("ランダム"),
    SYMMETRIC("対称"),
    GLIDERS("グライダー群"),
    GUN("グライダー銃"),
    R_PENTOMINO("Rペントミノ"),
    SOUP("中央に塊"),
}

// ── 壁紙ごとの詳細設定 ──

@Serializable
data class VaporConfig(
    val horizon: Float = 0.62f,
    val gridDensity: Float = 1f,
    val sunSize: Float = 1f,
    val starCount: Int = 90,
    val gridGlow: Boolean = true,
)

@Serializable
data class UnderwaterConfig(
    val bubbles: Int = 46,
    val fish: Int = 6,
    val lightRays: Boolean = true,
    val caustics: Boolean = true,
    val seaweed: Boolean = true,
    val plankton: Boolean = true,
    val depth: Float = 0.5f,
)

@Serializable
data class MazeConfig(
    val cellDp: Float = 26f,
    val generator: MazeGenerator = MazeGenerator.BACKTRACK,
    val solver: MazeSolver = MazeSolver.BFS,
    val genSpeed: Float = 1f,
    val solveSpeed: Float = 1f,
    val showSearch: Boolean = true,
    val holdSec: Float = 2.6f,
    val wallWidth: Float = 1f,
    val wallGlow: Boolean = true,
)

@Serializable
data class GlitchConfig(
    val intensity: Float = 0.5f,
    val frequency: Float = 0.5f,
    val showText: Boolean = true,
    val text: String = "ｗｏｎｄｅｒｆｕｌ",
    val invertFlash: Boolean = true,
    val shapes: Boolean = true,
)

@Serializable
data class MatrixConfig(
    val charSizeDp: Float = 18f,
    val density: Float = 1f,
    val trail: Float = 1f,
    val charset: MatrixCharset = MatrixCharset.KATAKANA,
    val color: ColorMode = ColorMode.PALETTE,
)

@Serializable
data class StarfieldConfig(
    val count: Int = 260,
    val warp: Float = 1f,
    val streak: Float = 1f,
    val centerGlow: Boolean = true,
    val color: ColorMode = ColorMode.PALETTE,
)

@Serializable
data class LavaConfig(
    val blobs: Int = 7,
    val size: Float = 1f,
    val glossy: Boolean = true,
)

@Serializable
data class AuroraConfig(
    val ribbons: Int = 3,
    val brightness: Float = 1f,
    val stars: Boolean = true,
    val mountains: Boolean = true,
)

@Serializable
data class LifeConfig(
    val cellDp: Float = 12f,
    val stepMs: Float = 110f,
    val density: Float = 0.28f,
    val rule: LifeRule = LifeRule.CONWAY,
    val wrap: Boolean = true,
    val shape: CellShape = CellShape.SQUARE,
    val color: LifeColor = LifeColor.AGE,
    val afterglow: Float = 0.5f,
    val cellGap: Float = 0.18f,
    val glow: Boolean = true,
    val gridLines: Boolean = false,
    val hud: Boolean = true,
    val seed: LifeSeed = LifeSeed.RANDOM,
    val autoReseed: Boolean = true,
    val maxGenerations: Int = 600,
    val injectGliders: Boolean = false,
    /** 何もない所をタップするとセルを置ける */
    val touch: Boolean = true,
)

/** アイコンをタップした時の動き */
enum class TapEffect(val label: String) {
    JELLY("ぷるん"),
    BOUNCE("バウンド"),
    SPIN("スピン"),
    NONE("なし"),
}

enum class ClockStyle(val label: String) {
    NEON("ネオン"),
    CHROME("クローム"),
    LCD("液晶"),
}

/*
 * ⚠ バックアップ互換性のための約束
 * - enum の定数は名前で保存される。既存の定数の名前変更・削除は禁止（追加のみ OK）
 * - 設定項目を追加するときは必ずデフォルト値を付ける
 * - 意味が変わる変更は Migration.kt にマイグレーションを書く
 */

/** 四隅のボタンに割り当てる機能 */
enum class CornerAction(val label: String) {
    SEARCH("検索"),
    SETTINGS("設定"),
    RECENT("最近"),
    FOLDERS("フォルダ"),
    WIDGETS("ウィジェット"),
    NONE("なし"),
}

/** ジェスチャーに割り当てる機能 */
enum class GestureAction(val label: String, val needsAccessibility: Boolean = false) {
    NONE("なし"),
    SEARCH("検索"),
    SETTINGS("設定"),
    RECENT("最近"),
    FOLDERS("フォルダ"),
    WIDGETS("ウィジェット"),
    NOTIFICATIONS("通知"),
    QUICK_SETTINGS("クイック設定"),
    RESET_SPHERE("回転リセット"),
    SHUFFLE_SPHERE("シャッフル"),
    SPIN_SPHERE("スピン"),
    LAUNCH_APP("アプリ起動"),
    ALARM("アラーム"),
    CAMERA("カメラ"),
    ASSISTANT("アシスタント"),
    FLASHLIGHT("ライト"),
    DND("おやすみ切替"),
    LOCK_SCREEN("画面ロック", needsAccessibility = true),
    SYSTEM_RECENTS("タスク一覧", needsAccessibility = true),
    SCREENSHOT("スクショ", needsAccessibility = true),
    POWER_MENU("電源メニュー", needsAccessibility = true),
}

/** ジェスチャーの種類（LAUNCH_APP のときの起動アプリを紐付けるキー） */
enum class GestureSlot(val label: String) {
    DOUBLE_TAP("何もない所をダブルタップ"),
    LONG_PRESS("何もない所を長押し"),
    SWIPE_DOWN("上部を下にスワイプ"),
    SWIPE_UP("下部を上にスワイプ"),
    SWIPE_LEFT("上部を左にスワイプ"),
    SWIPE_RIGHT("上部を右にスワイプ"),
    TWO_FINGER_DOWN("2本指で下にスワイプ"),
    TWO_FINGER_UP("2本指で上にスワイプ"),
    HOME_BUTTON("ホームでホームボタン"),
    CLOCK_TAP("時計をタップ"),
}

/** ウィジェットの種類 */
enum class WidgetKind(val label: String) {
    SYSTEM("Androidウィジェット"),
    CLOCK("レトロ時計"),
    BATTERY("バッテリー"),
    NOTE("メモ"),
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
    val tapBounce: Boolean = true, // v2 までの項目。v3 からは tapEffect に移行
    val launchZoom: Boolean = true,
    val entranceAnim: Boolean = true,
    val focusFrontIcon: Boolean = true,
    val haptics: Boolean = true,
    val sphereMaxApps: Int = 80,

    // ── 四隅ボタン ──
    val cornerTopLeft: CornerAction = CornerAction.RECENT,
    val cornerTopRight: CornerAction = CornerAction.SEARCH,
    val cornerBottomLeft: CornerAction = CornerAction.FOLDERS,
    val cornerBottomRight: CornerAction = CornerAction.WIDGETS,
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
    val mazeCellDp: Float = 26f, // v3 の項目。v4 からは maze.cellDp
    val glitchIntensity: Float = 0.5f, // v3 の項目。v4 からは glitch.intensity
    val vapor: VaporConfig = VaporConfig(),
    val underwater: UnderwaterConfig = UnderwaterConfig(),
    val maze: MazeConfig = MazeConfig(),
    val glitch: GlitchConfig = GlitchConfig(),
    val matrix: MatrixConfig = MatrixConfig(),
    val starfield: StarfieldConfig = StarfieldConfig(),
    val lava: LavaConfig = LavaConfig(),
    val aurora: AuroraConfig = AuroraConfig(),
    val life: LifeConfig = LifeConfig(),
    val gradientAnimate: Boolean = true,

    // ── 時計 ──
    val showClock: Boolean = true,
    val clock24h: Boolean = true,
    val showSeconds: Boolean = false,
    val showDate: Boolean = true,
    val clockStyle: ClockStyle = ClockStyle.NEON,
    val clockScale: Float = 1f,
    val clockTapOpensAlarm: Boolean = true, // v1 の項目。v2 からは clockTapAction に移行
    val clockTapAction: GestureAction = GestureAction.ALARM,

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
    val swipeDownAction: GestureAction = GestureAction.NOTIFICATIONS,
    val swipeUpAction: GestureAction = GestureAction.SEARCH,
    val swipeLeftAction: GestureAction = GestureAction.NONE,
    val swipeRightAction: GestureAction = GestureAction.NONE,
    val twoFingerDownAction: GestureAction = GestureAction.QUICK_SETTINGS,
    val twoFingerUpAction: GestureAction = GestureAction.WIDGETS,
    val homeButtonAction: GestureAction = GestureAction.SPIN_SPHERE,
    /** LAUNCH_APP 用: GestureSlot 名 → アプリのキー */
    val gestureApps: Map<String, String> = emptyMap(),

    // ── 通知バッジ（通知へのアクセス許可が必要） ──
    val showBadges: Boolean = true,
    val badgeShowCount: Boolean = true,

    // ── エフェクト ──
    /** ゴムのようにビヨンビヨン（0 = OFF） */
    val rubber: Float = 0.45f,
    /** 弾み（大きいほど揺れが長く続く） */
    val rubberBounce: Float = 0.6f,
    /** 伸び縮みの強さ */
    val rubberStretch: Float = 0.5f,
    /** どこまで遅れてついてくるか（アイコン何個ぶん） */
    val rubberMaxLag: Float = 0.8f,
    /** ボタンやタイルも弾ませる */
    val rubberButtons: Boolean = true,
    /** 回転が速いほど揺れを抑える（裏返り防止） */
    val rubberSpeedLimit: Boolean = true,
    val glowSize: Float = 1f,
    val glowAll: Boolean = false,
    val glowPulse: Boolean = false,
    val trailStyle: TrailStyle = TrailStyle.NEON,
    val trailSpacing: Float = 0.5f,
    val tapEffect: TapEffect = TapEffect.JELLY,
    val tapParticles: Boolean = true,
    val iconFloat: Float = 0f,
    val trailLength: Float = 0f,
    val rainbowGlow: Boolean = false,
    /** 奥のアイコンをぼかす（被写界深度、Android 12+） */
    val dofBlur: Float = 0.35f,
    /** パネルを開いた時に後ろをぼかす（Android 12+） */
    val panelBlur: Boolean = true,
    val filmGrain: Float = 0f,
    val vhsTracking: Boolean = false,
    val glitchOverlay: Boolean = false,

    // ── ウィジェット画面 ──
    val widgetFrame: Boolean = true,
    val widgetGrid: Boolean = true,

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

/**
 * 画面に置いたウィジェット 1 つ。位置とサイズは [WIDGET_COLUMNS] 列のグリッド単位。
 */
@Serializable
data class WidgetItem(
    val id: String,
    val kind: WidgetKind = WidgetKind.SYSTEM,
    /** Android ウィジェットの ID（SYSTEM のみ） */
    val appWidgetId: Int = -1,
    /** "package/class"。別端末へ復元した時に作り直すために保存しておく */
    val provider: String = "",
    val x: Int = 0,
    val y: Int = 0,
    val w: Int = 4,
    val h: Int = 2,
    /** メモの内容など */
    val text: String = "",
)

const val WIDGET_COLUMNS = 8

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
    val widgets: List<WidgetItem> = emptyList(),
    /**
     * 保存形式のバージョン。v1（最初のリリース）にはこの項目が無いので、
     * 読み込み時に無ければ 1 として扱いマイグレーションする。
     */
    val dataVersion: Int = 1,
)
