package com.wonderful.launcher.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wonderful.launcher.data.AuroraConfig
import com.wonderful.launcher.data.CameraConfig
import com.wonderful.launcher.data.CameraEffect
import com.wonderful.launcher.data.CameraLens
import com.wonderful.launcher.data.CellShape
import com.wonderful.launcher.data.ColorMode
import com.wonderful.launcher.data.GlitchConfig
import com.wonderful.launcher.data.LauncherSettings
import com.wonderful.launcher.data.LavaConfig
import com.wonderful.launcher.data.LifeColor
import com.wonderful.launcher.data.LifeConfig
import com.wonderful.launcher.data.LifeInject
import com.wonderful.launcher.data.LifeRule
import com.wonderful.launcher.data.LifeSeed
import com.wonderful.launcher.data.MatrixCharset
import com.wonderful.launcher.data.MatrixConfig
import com.wonderful.launcher.data.MazeConfig
import com.wonderful.launcher.data.MazeGenerator
import com.wonderful.launcher.data.MazeSolver
import com.wonderful.launcher.data.MirrorConfig
import com.wonderful.launcher.data.MirrorQuality
import com.wonderful.launcher.data.ScreenCorner
import com.wonderful.launcher.data.StarfieldConfig
import com.wonderful.launcher.data.UnderwaterConfig
import com.wonderful.launcher.data.VaporConfig
import com.wonderful.launcher.data.WallpaperType
import com.wonderful.launcher.ui.components.RetroButton
import com.wonderful.launcher.ui.components.RetroLabel
import com.wonderful.launcher.ui.components.RetroTextField
import com.wonderful.launcher.ui.components.SectionHeader
import com.wonderful.launcher.ui.theme.LocalPalette
import com.wonderful.launcher.ui.wallpaper.hasCameraPermission

typealias SettingsUpdate = ((LauncherSettings) -> LauncherSettings) -> Unit

private fun pc(v: Float) = "${(v * 100).toInt()}%"
private fun times(v: Float) = "x%.1f".format(v)

/** 選んでいる壁紙の詳細設定 */
@Composable
fun WallpaperDetailSettings(s: LauncherSettings, u: SettingsUpdate) {
    if (s.wallpaperType.animated) {
        SectionHeader("アニメーション")
        SliderSetting("動く速さ", s.gridSpeed, 0.1f..4f, format = ::times) { v -> u { it.copy(gridSpeed = v) } }
    }
    when (s.wallpaperType) {
        WallpaperType.VAPOR_GRID -> VaporSettings(s, u)
        WallpaperType.GRADIENT -> {
            SectionHeader("グラデーション")
            SliderSetting("角度", s.gradientAngle, 0f..360f, format = { "${it.toInt()}°" }) { v -> u { it.copy(gradientAngle = v) } }
            SwitchSetting("ゆっくり回転する", s.gradientAnimate) { v -> u { it.copy(gradientAnimate = v) } }
            RetroLabel("色は「見た目 → カラーテーマ」に連動します", size = 11.sp, color = LocalPalette.current.text.copy(alpha = 0.6f))
        }
        WallpaperType.UNDERWATER -> UnderwaterSettings(s.underwater) { f -> u { it.copy(underwater = f(it.underwater)) } }
        WallpaperType.MAZE -> MazeSettings(s.maze) { f -> u { it.copy(maze = f(it.maze)) } }
        WallpaperType.GLITCH -> GlitchSettings(s.glitch) { f -> u { it.copy(glitch = f(it.glitch)) } }
        WallpaperType.MATRIX -> MatrixSettings(s.matrix) { f -> u { it.copy(matrix = f(it.matrix)) } }
        WallpaperType.STARFIELD -> StarfieldSettings(s.starfield) { f -> u { it.copy(starfield = f(it.starfield)) } }
        WallpaperType.LAVA -> LavaSettings(s.lava) { f -> u { it.copy(lava = f(it.lava)) } }
        WallpaperType.AURORA -> AuroraSettings(s.aurora) { f -> u { it.copy(aurora = f(it.aurora)) } }
        WallpaperType.LIFE -> LifeSettings(s.life) { f -> u { it.copy(life = f(it.life)) } }
        WallpaperType.LIFE_FOLLOW -> {
            SectionHeader("追尾カメラ")
            RetroLabel("広い世界のうち、一番変化が起きている所をカメラが追いかけます。落ち着いたら初期位置（真ん中）に戻ります。初期配置は「Rペントミノ」や「どんぐり」がおすすめ", size = 11.sp, color = LocalPalette.current.text.copy(alpha = 0.7f), maxLines = 4)
            SliderSetting("世界の広さ", s.life.followWorld, 1.5f..6f, "画面の何倍の広さか（広いほど長く育つが少し重くなる）", format = { "%.1f倍".format(it) }) { v -> u { it.copy(life = it.life.copy(followWorld = v)) } }
            // 遅い側を細かく選べるよう、つまみは速さの平方根で動かす
            SliderSetting("カメラの速さ", kotlin.math.sqrt(s.life.followSpeed), 0.14f..2f, "左ほどゆっくり", format = { "%.2f倍".format(it * it) }) { v -> u { it.copy(life = it.life.copy(followSpeed = v * v)) } }
            SliderSetting("たまに追尾", s.life.followInterval, 0f..60f, "何秒ごとに追いかけ直すか。0 はいつも追いかける。それ以外は狙った場所をしばらく映し続ける", format = { if (it < 0.5f) "いつも" else "${it.toInt()}秒ごと" }) { v -> u { it.copy(life = it.life.copy(followInterval = if (v < 0.5f) 0f else v)) } }
            SwitchSetting("自動ズーム", s.life.followAutoZoom, "変化が広がったら引いて、小さくまとまったら寄る") { v -> u { it.copy(life = it.life.copy(followAutoZoom = v)) } }
            SwitchSetting("世界の地図（情報表示の横）", s.life.minimap) { v -> u { it.copy(life = it.life.copy(minimap = v)) } }
            LifeSettings(s.life) { f -> u { it.copy(life = f(it.life)) } }
        }
        WallpaperType.MIRROR -> MirrorSettings(s.mirror) { f -> u { it.copy(mirror = f(it.mirror)) } }
        WallpaperType.CAMERA -> CameraSettings(s.camera) { f -> u { it.copy(camera = f(it.camera)) } }
        WallpaperType.SYSTEM, WallpaperType.IMAGE, WallpaperType.SOLID -> Unit
    }
}

@Composable
private fun MirrorSettings(c: MirrorConfig, set: ((MirrorConfig) -> MirrorConfig) -> Unit) {
    val p = LocalPalette.current
    SectionHeader("合わせ鏡")
    RetroLabel(
        "ホーム画面（アイコンや時計）が、合わせ鏡のように奥へ奥へと映り込みます。アイコンを動かすと、少し遅れて奥に伝わっていきます。",
        size = 11.sp, color = p.text.copy(alpha = 0.6f), maxLines = 4,
    )
    SliderSetting("ディレイ（1 枚奥に映るまで）", c.delaySec, 0.04f..2f, "短いほどなめらか・長いほど軽くて残像のように遅れる", format = { "%.2f秒".format(it) }) { v -> set { it.copy(delaySec = v) } }
    SliderSetting("奥行き（1 枚ごとの大きさ）", c.shrink, 0.5f..0.97f, "大きいほど細かく深く続く", format = ::pc) { v -> set { it.copy(shrink = v) } }
    SliderSetting("1 枚ごとの回転", c.rotation, -30f..30f, "ずらすと渦巻きになる", format = { "%.1f°".format(it) }) { v -> set { it.copy(rotation = v) } }
    SliderSetting("奥ほど暗く", c.darken, 0f..0.6f, format = ::pc) { v -> set { it.copy(darken = v) } }
    SliderSetting("ゆらぎ", c.wobble, 0f..1f, format = ::pc) { v -> set { it.copy(wobble = v) } }
    SwitchSetting("鏡なので左右反転", c.flip) { v -> set { it.copy(flip = v) } }
    SwitchSetting("鏡の枠", c.frame) { v -> set { it.copy(frame = v) } }
    SwitchSetting("奥ほど色が変わる", c.rainbow) { v -> set { it.copy(rainbow = v) } }
    ChoiceSetting("画質", MirrorQuality.entries, c.quality, { it.label }, "軽いほど電池にやさしい") { v -> set { it.copy(quality = v) } }

    SectionHeader("おすすめの組み合わせ")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RetroButton("まっすぐ", { set { MirrorConfig() } }, color = p.cyan)
        RetroButton("渦巻き", { set { it.copy(rotation = 8f, shrink = 0.9f, rainbow = true, frame = true) } }, color = p.pink)
        RetroButton("トンネル", { set { it.copy(shrink = 0.93f, darken = 0.08f, rotation = 0f, frame = true, delaySec = 0.06f) } }, color = p.mint)
        RetroButton("のんびり残像", { set { it.copy(delaySec = 0.8f, shrink = 0.8f, wobble = 0.5f) } }, color = p.yellow)
        RetroButton("鏡の部屋", { set { it.copy(flip = true, shrink = 0.88f, darken = 0.15f) } }, color = p.purple)
    }
    ResetRow { set { MirrorConfig() } }
}

@Composable
private fun CameraSettings(c: CameraConfig, set: ((CameraConfig) -> CameraConfig) -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    // カメラの壁紙を選んだらすぐ許可を聞く（1 回だけ）
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    SectionHeader("カメラ")
    RetroLabel(
        "カメラで見ている景色を壁紙に映します。映像は画面に映すだけで、保存も送信もしません。設定などを開いている間はカメラを止めます。",
        size = 11.sp, color = p.text.copy(alpha = 0.6f), maxLines = 4,
    )
    if (!granted) RetroButton("カメラを許可", { launcher.launch(Manifest.permission.CAMERA) }, Modifier.fillMaxWidth(), color = p.pink)
    ChoiceSetting("カメラ", CameraLens.entries, c.lens, { it.label }) { v -> set { it.copy(lens = v) } }
    ChoiceSetting("色の効果", CameraEffect.entries, c.effect, { it.label }) { v -> set { it.copy(effect = v) } }
    SwitchSetting("走査線", c.scanlines) { v -> set { it.copy(scanlines = v) } }
    RetroLabel("アイコンが見づらい時は、上の「調整 → 暗くする」で暗くできます", size = 11.sp, color = p.text.copy(alpha = 0.6f), maxLines = 2)
    ResetRow { set { CameraConfig() } }
}

@Composable
private fun ResetRow(onReset: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    RetroButton("この壁紙の設定を初期値に戻す", onReset, Modifier.fillMaxWidth(), color = LocalPalette.current.pink)
}

@Composable
private fun VaporSettings(s: LauncherSettings, u: SettingsUpdate) {
    val c = s.vapor
    fun set(f: (VaporConfig) -> VaporConfig) = u { it.copy(vapor = f(it.vapor)) }
    SectionHeader("ヴェイパー風景")
    SwitchSetting("太陽", s.showSun) { v -> u { it.copy(showSun = v) } }
    if (s.showSun) {
        SwitchSetting("太陽の横縞", s.sunStripes) { v -> u { it.copy(sunStripes = v) } }
        SliderSetting("太陽の大きさ", c.sunSize, 0.4f..1.8f, format = ::times) { v -> set { it.copy(sunSize = v) } }
    }
    SwitchSetting("山", s.showMountains) { v -> u { it.copy(showMountains = v) } }
    SwitchSetting("星", s.showStars) { v -> u { it.copy(showStars = v) } }
    if (s.showStars) SliderSetting("星の数", c.starCount.toFloat(), 0f..400f, format = { "${it.toInt()}" }) { v -> set { it.copy(starCount = v.toInt()) } }
    SliderSetting("地平線の高さ", c.horizon, 0.3f..0.85f, format = ::pc) { v -> set { it.copy(horizon = v) } }
    SliderSetting("グリッドの細かさ", c.gridDensity, 0.4f..3f, format = ::times) { v -> set { it.copy(gridDensity = v) } }
    SwitchSetting("グリッドを光らせる", c.gridGlow) { v -> set { it.copy(gridGlow = v) } }
    ResetRow { set { VaporConfig() } }
}

@Composable
private fun UnderwaterSettings(c: UnderwaterConfig, set: ((UnderwaterConfig) -> UnderwaterConfig) -> Unit) {
    SectionHeader("水中")
    SliderSetting("深さ（暗さ）", c.depth, 0f..1f, format = ::pc) { v -> set { it.copy(depth = v) } }
    SliderSetting("泡の数", c.bubbles.toFloat(), 0f..200f, format = { "${it.toInt()}" }) { v -> set { it.copy(bubbles = v.toInt()) } }
    SliderSetting("魚の数", c.fish.toFloat(), 0f..30f, format = { "${it.toInt()}" }) { v -> set { it.copy(fish = v.toInt()) } }
    SwitchSetting("光の筋", c.lightRays) { v -> set { it.copy(lightRays = v) } }
    SwitchSetting("水面のゆらめき", c.caustics) { v -> set { it.copy(caustics = v) } }
    SwitchSetting("海藻", c.seaweed) { v -> set { it.copy(seaweed = v) } }
    SwitchSetting("浮遊する粒", c.plankton) { v -> set { it.copy(plankton = v) } }
    ResetRow { set { UnderwaterConfig() } }
}

@Composable
private fun MazeSettings(c: MazeConfig, set: ((MazeConfig) -> MazeConfig) -> Unit) {
    val p = LocalPalette.current
    SectionHeader("迷路")
    RetroLabel(
        "迷路を自動で作り（ピンク）、左上から右下へ自動で解きます（水色の探索 → 黄色の答え）",
        size = 11.sp, color = p.text.copy(alpha = 0.6f), maxLines = 3,
    )
    SliderSetting("マスの大きさ", c.cellDp, 12f..80f, "小さいほど複雑な迷路", format = { "${it.toInt()}dp" }) { v -> set { it.copy(cellDp = v) } }
    ChoiceSetting("作り方", MazeGenerator.entries, c.generator, { it.label }, "穴掘り: 長い一本道 / プリム: 枝分かれが多い") { v -> set { it.copy(generator = v) } }
    ChoiceSetting("解き方", MazeSolver.entries, c.solver, { it.label }, "幅優先: 波のように広がる / 深さ優先: 一本ずつ突き進む / A*: ゴールへ一直線 / 右手法: 壁沿いに歩く") { v -> set { it.copy(solver = v) } }
    SliderSetting("作る速さ", c.genSpeed, 0.1f..5f, format = ::times) { v -> set { it.copy(genSpeed = v) } }
    SliderSetting("解く速さ", c.solveSpeed, 0.1f..5f, format = ::times) { v -> set { it.copy(solveSpeed = v) } }
    SliderSetting("解けた後に止まる時間", c.holdSec, 0f..15f, format = { "%.1f秒".format(it) }) { v -> set { it.copy(holdSec = v) } }
    SwitchSetting("探索した所を表示", c.showSearch) { v -> set { it.copy(showSearch = v) } }
    SliderSetting("壁の太さ", c.wallWidth, 0.3f..3f, format = ::times) { v -> set { it.copy(wallWidth = v) } }
    SwitchSetting("壁を光らせる", c.wallGlow) { v -> set { it.copy(wallGlow = v) } }
    ResetRow { set { MazeConfig() } }
}

@Composable
private fun GlitchSettings(c: GlitchConfig, set: ((GlitchConfig) -> GlitchConfig) -> Unit) {
    SectionHeader("グリッチ")
    SliderSetting("強さ", c.intensity, 0f..1f, format = ::pc) { v -> set { it.copy(intensity = v) } }
    SliderSetting("頻度", c.frequency, 0f..1f, format = ::pc) { v -> set { it.copy(frequency = v) } }
    SwitchSetting("文字を表示", c.showText) { v -> set { it.copy(showText = v) } }
    if (c.showText) {
        RetroLabel("表示する文字", size = 13.sp)
        RetroTextField(c.text, { t -> set { it.copy(text = t.take(24)) } }, "ｗｏｎｄｅｒｆｕｌ", Modifier.fillMaxWidth())
    }
    SwitchSetting("図形を表示", c.shapes) { v -> set { it.copy(shapes = v) } }
    SwitchSetting("反転フラッシュ", c.invertFlash, "ときどき画面の色が反転する") { v -> set { it.copy(invertFlash = v) } }
    ResetRow { set { GlitchConfig() } }
}

@Composable
private fun MatrixSettings(c: MatrixConfig, set: ((MatrixConfig) -> MatrixConfig) -> Unit) {
    SectionHeader("デジタル雨")
    ChoiceSetting("文字の種類", MatrixCharset.entries, c.charset, { it.label }) { v -> set { it.copy(charset = v) } }
    ChoiceSetting("色", ColorMode.entries, c.color, { it.label }) { v -> set { it.copy(color = v) } }
    SliderSetting("文字の大きさ", c.charSizeDp, 8f..40f, format = { "${it.toInt()}dp" }) { v -> set { it.copy(charSizeDp = v) } }
    SliderSetting("密度", c.density, 0.1f..1f, format = ::pc) { v -> set { it.copy(density = v) } }
    SliderSetting("尾の長さ", c.trail, 0.2f..3f, format = ::times) { v -> set { it.copy(trail = v) } }
    ResetRow { set { MatrixConfig() } }
}

@Composable
private fun StarfieldSettings(c: StarfieldConfig, set: ((StarfieldConfig) -> StarfieldConfig) -> Unit) {
    SectionHeader("ワープ")
    SliderSetting("星の数", c.count.toFloat(), 20f..1200f, format = { "${it.toInt()}" }) { v -> set { it.copy(count = v.toInt()) } }
    SliderSetting("ワープの速さ", c.warp, 0.1f..5f, format = ::times) { v -> set { it.copy(warp = v) } }
    SliderSetting("光の尾の長さ", c.streak, 0.2f..6f, format = ::times) { v -> set { it.copy(streak = v) } }
    ChoiceSetting("色", ColorMode.entries, c.color, { it.label }) { v -> set { it.copy(color = v) } }
    SwitchSetting("中央の光", c.centerGlow) { v -> set { it.copy(centerGlow = v) } }
    ResetRow { set { StarfieldConfig() } }
}

@Composable
private fun LavaSettings(c: LavaConfig, set: ((LavaConfig) -> LavaConfig) -> Unit) {
    SectionHeader("ラバランプ")
    SliderSetting("かたまりの数", c.blobs.toFloat(), 1f..16f, format = { "${it.toInt()}" }) { v -> set { it.copy(blobs = v.toInt()) } }
    SliderSetting("かたまりの大きさ", c.size, 0.3f..2f, format = ::times) { v -> set { it.copy(size = v) } }
    SwitchSetting("ガラスの反射", c.glossy) { v -> set { it.copy(glossy = v) } }
    ResetRow { set { LavaConfig() } }
}

@Composable
private fun AuroraSettings(c: AuroraConfig, set: ((AuroraConfig) -> AuroraConfig) -> Unit) {
    SectionHeader("オーロラ")
    SliderSetting("光の帯の数", c.ribbons.toFloat(), 1f..6f, steps = 4, format = { "${it.toInt()}" }) { v -> set { it.copy(ribbons = v.toInt()) } }
    SliderSetting("明るさ", c.brightness, 0.2f..2f, format = ::times) { v -> set { it.copy(brightness = v) } }
    SwitchSetting("星", c.stars) { v -> set { it.copy(stars = v) } }
    SwitchSetting("山と湖", c.mountains) { v -> set { it.copy(mountains = v) } }
    ResetRow { set { AuroraConfig() } }
}

@Composable
private fun LifeSettings(c: LifeConfig, set: ((LifeConfig) -> LifeConfig) -> Unit) {
    val p = LocalPalette.current
    SectionHeader("ライフゲーム")
    RetroLabel(
        "セルが周りの数で生まれたり消えたりします。ルールを変えると全く違う世界に。何もない所をタップするとセルを置けます。",
        size = 11.sp, color = p.text.copy(alpha = 0.6f), maxLines = 4,
    )
    ChoiceSetting("ルール", LifeRule.entries, c.rule, { "${it.label} B${it.birth}/S${it.survive}" }) { v -> set { it.copy(rule = v) } }
    ChoiceSetting("始まり方", LifeSeed.entries, c.seed, { it.label }) { v -> set { it.copy(seed = v) } }
    SliderSetting("最初のセルの多さ", c.density, 0.02f..0.8f, format = ::pc) { v -> set { it.copy(density = v) } }
    SliderSetting("世代が進む間隔", c.stepMs, 16f..1000f, "小さいほど速い", format = { "${it.toInt()}ms" }) { v -> set { it.copy(stepMs = v) } }
    SwitchSetting("端がつながる（トーラス）", c.wrap, "右端を越えると左端から出てくる") { v -> set { it.copy(wrap = v) } }

    SectionHeader("ライフゲームの見た目")
    SliderSetting("セルの大きさ", c.cellDp, 5f..40f, format = { "${it.toInt()}dp" }) { v -> set { it.copy(cellDp = v) } }
    ChoiceSetting("セルの形", CellShape.entries, c.shape, { it.label }) { v -> set { it.copy(shape = v) } }
    if (c.shape != CellShape.PIXEL) SliderSetting("セルの隙間", c.cellGap, 0f..0.6f, format = ::pc) { v -> set { it.copy(cellGap = v) } }
    ChoiceSetting("色の付け方", LifeColor.entries, c.color, { it.label }, "年齢: 生まれたて=黄→ピンク→水色 / 近傍数: 周りの数で色分け / ヒート: 長生きほど赤く") { v -> set { it.copy(color = v) } }
    SliderSetting("消えたセルの残光", c.afterglow, 0f..1f, format = ::pc) { v -> set { it.copy(afterglow = v) } }
    SwitchSetting("ネオンの光", c.glow) { v -> set { it.copy(glow = v) } }
    if (c.glow) SliderSetting("光の大きさ", c.glowSize, 0.3f..3f, format = ::times) { v -> set { it.copy(glowSize = v) } }
    SwitchSetting("生まれた瞬間に光る", c.birthFlash) { v -> set { it.copy(birthFlash = v) } }
    if (c.birthFlash) {
        SliderSetting("誕生の光の長さ", c.birthFlashSec, 0.2f..3f, format = { "%.1f秒".format(it) }) { v -> set { it.copy(birthFlashSec = v) } }
        SliderSetting("誕生の光の大きさ", c.birthFlashSize, 0.5f..3f, format = ::times) { v -> set { it.copy(birthFlashSize = v) } }
    }
    SwitchSetting("セルが脈打つ", c.pulse) { v -> set { it.copy(pulse = v) } }
    SwitchSetting("マス目の線", c.gridLines) { v -> set { it.copy(gridLines = v) } }
    SwitchSetting("情報表示（世代・人口グラフ）", c.hud) { v -> set { it.copy(hud = v) } }
    if (c.hud) {
        ChoiceSetting("情報表示の位置", ScreenCorner.entries, c.hudCorner, { it.label }, "時計やドックと重なる時は位置や距離を変えてください") { v -> set { it.copy(hudCorner = v) } }
        SliderSetting("端からの距離", c.hudMarginDp, 0f..400f, format = { "${it.toInt()}dp" }) { v -> set { it.copy(hudMarginDp = v) } }
    }

    SectionHeader("ライフゲームの動き")
    SwitchSetting("タップでセルを置く", c.touch, "グライダーなどの模様がランダムに置かれる") { v -> set { it.copy(touch = v) } }
    SwitchSetting("ときどき模様を投入", c.injectGliders) { v -> set { it.copy(injectGliders = v) } }
    if (c.injectGliders) {
        ChoiceSetting("投入する模様", LifeInject.entries, c.injectPattern, { it.label }) { v -> set { it.copy(injectPattern = v) } }
        SliderSetting("投入の間隔", c.injectEverySec, 0.5f..30f, format = { "%.1f秒".format(it) }) { v -> set { it.copy(injectEverySec = v) } }
    }
    SliderSetting("突然変異", c.mutation, 0f..2f, "毎世代、ランダムなセルが反転する割合。止まりかけた世界がまた動き出す", format = { "%.2f%%".format(it) }) { v -> set { it.copy(mutation = v) } }
    SwitchSetting("止まったら自動で植え直す", c.autoReseed) { v -> set { it.copy(autoReseed = v) } }
    if (c.autoReseed) {
        SliderSetting("植え直すまでの最大世代", c.maxGenerations.toFloat(), 50f..5000f, format = { "${it.toInt()}世代" }) { v -> set { it.copy(maxGenerations = v.toInt()) } }
    }

    SectionHeader("おすすめの組み合わせ")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RetroButton("クラシック", { set { LifeConfig() } }, color = p.cyan)
        RetroButton("ネオン粒", { set { it.copy(shape = CellShape.ROUND, color = LifeColor.RAINBOW, cellDp = 9f, afterglow = 0.8f, glow = true) } }, color = p.pink)
        RetroButton("ドット絵", { set { it.copy(shape = CellShape.PIXEL, color = LifeColor.MONO, cellDp = 6f, afterglow = 0.2f, glow = false, gridLines = false) } }, color = p.mint)
        RetroButton("迷路が育つ", { set { it.copy(rule = LifeRule.MAZE, seed = LifeSeed.SOUP, color = LifeColor.HEAT, cellDp = 8f) } }, color = p.yellow)
        RetroButton("グライダー銃", { set { it.copy(rule = LifeRule.CONWAY, seed = LifeSeed.GUN, wrap = false, cellDp = 10f, autoReseed = false) } }, color = p.purple)
        RetroButton("昼と夜", { set { it.copy(rule = LifeRule.DAY_NIGHT, seed = LifeSeed.RANDOM, density = 0.5f, color = LifeColor.NEIGHBORS) } }, color = p.cyan)
        RetroButton("ホタル", { set { it.copy(shape = CellShape.ROUND, color = LifeColor.AGE, glow = true, glowSize = 2.2f, birthFlash = true, pulse = true, afterglow = 0.9f, cellDp = 10f) } }, color = p.yellow)
        RetroButton("終わらない世界", { set { it.copy(mutation = 0.05f, injectGliders = true, injectPattern = LifeInject.RANDOM, injectEverySec = 4f, autoReseed = false) } }, color = p.mint)
    }
    ResetRow { set { LifeConfig() } }
}
