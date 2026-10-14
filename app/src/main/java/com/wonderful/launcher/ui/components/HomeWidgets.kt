package com.wonderful.launcher.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.composed
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.abs
import androidx.compose.ui.input.pointer.positionChange
import com.wonderful.launcher.data.AppInfo
import com.wonderful.launcher.data.ClockStyle
import com.wonderful.launcher.data.CornerAction
import com.wonderful.launcher.ui.theme.LocalPalette
import com.wonderful.launcher.ui.theme.LocalRetroFont
import com.wonderful.launcher.ui.theme.LocalSettings
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────
// 時計
// ─────────────────────────────────────────────

@Composable
fun RetroClock(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalSettings.current
    val p = LocalPalette.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.showSeconds) {
        while (true) {
            now = System.currentTimeMillis()
            val step = if (s.showSeconds) 1000L else 60_000L
            delay(step - now % step + 5)
        }
    }
    val pattern = buildString {
        append(if (s.clock24h) "HH:mm" else "h:mm")
        if (s.showSeconds) append(":ss")
    }
    val time = remember(now, pattern) { SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now)) }
    val ampm = remember(now) { SimpleDateFormat("a", Locale.US).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("yyyy.MM.dd (E)", Locale.JAPAN).format(Date(now)) }

    val flicker by rememberInfiniteTransition(label = "clock").animateFloat(
        0.85f, 1f, infiniteRepeatable(tween((1800 / s.animSpeed).toInt(), easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "flicker",
    )
    val size = (64 * s.clockScale).sp
    val font = LocalRetroFont.current

    Column(
        modifier
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            when (s.clockStyle) {
                ClockStyle.NEON -> NeonText(
                    time, size = size, color = Color.White, glow = p.pink,
                    letterSpacing = 4.sp, modifier = Modifier.graphicsLayer { alpha = flicker },
                )
                ClockStyle.CHROME -> {
                    // 80 年代ロゴ風のクローム文字
                    Text(
                        time,
                        style = TextStyle(
                            brush = Brush.verticalGradient(
                                listOf(Color.White, p.cyan, Color(0xFF1B1B5E), p.pink, p.yellow),
                            ),
                            fontSize = size,
                            fontWeight = FontWeight.Black,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 2.sp,
                            shadow = Shadow(p.purple, Offset(5f, 5f), 4f),
                        ),
                    )
                }
                ClockStyle.LCD -> Box {
                    val ghost = time.map { if (it.isDigit()) '8' else it }.joinToString("")
                    val st = TextStyle(fontFamily = FontFamily.Monospace, fontSize = size, letterSpacing = 3.sp)
                    Text(ghost, style = st.copy(color = p.mint.copy(alpha = 0.1f)))
                    Text(time, style = st.copy(color = p.mint, shadow = Shadow(p.mint, Offset.Zero, 20f)))
                }
            }
            if (!s.clock24h) {
                Spacer(Modifier.size(6.dp))
                NeonText(ampm, size = (16 * s.clockScale).sp, color = p.cyan, glow = p.cyan, modifier = Modifier.padding(bottom = 12.dp))
            }
        }
        if (s.showDate) {
            Text(
                date,
                style = TextStyle(
                    fontFamily = font, fontSize = (15 * s.clockScale).sp, color = p.cyan,
                    letterSpacing = 3.sp, shadow = Shadow(p.cyan, Offset.Zero, 12f), textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

// ─────────────────────────────────────────────
// 四隅のボタン
// ─────────────────────────────────────────────

fun CornerAction.glyph(): Glyph = when (this) {
    CornerAction.SEARCH -> Glyph.SEARCH
    CornerAction.SETTINGS -> Glyph.SETTINGS
    CornerAction.RECENT -> Glyph.RECENT
    CornerAction.FOLDERS -> Glyph.FOLDER
    CornerAction.WIDGETS -> Glyph.WIDGETS
    CornerAction.NONE -> Glyph.NONE
}

fun CornerAction.englishLabel(): String = when (this) {
    CornerAction.SEARCH -> "SEARCH"
    CornerAction.SETTINGS -> "CONFIG"
    CornerAction.RECENT -> "RECENT"
    CornerAction.FOLDERS -> "FOLDER"
    CornerAction.WIDGETS -> "WIDGET"
    CornerAction.NONE -> ""
}

/**
 * ふわふわ浮かぶネオンのボタン。押すとバネで縮んでから光る。
 * [phase] をずらすと 4 つのボタンがバラバラに揺れる。
 */
@Composable
fun CornerButton(
    action: CornerAction,
    phase: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (action == CornerAction.NONE) return
    val s = LocalSettings.current
    val p = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.82f else 1f, spring(dampingRatio = 0.3f, stiffness = 500f), label = "corner")
    val infinite = rememberInfiniteTransition(label = "float")
    val bob by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween((2600 / s.animSpeed).toInt(), easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val glowPulse by infinite.animateFloat(
        0.4f, 1f, infiniteRepeatable(tween((1500 / s.animSpeed).toInt()), RepeatMode.Reverse), label = "glow",
    )
    val color = when (action) {
        CornerAction.SEARCH -> p.cyan
        CornerAction.SETTINGS -> p.yellow
        CornerAction.RECENT -> p.pink
        CornerAction.FOLDERS -> p.mint
        CornerAction.WIDGETS -> p.purple
        CornerAction.NONE -> p.text
    }
    val sizeDp = s.cornerSizeDp.dp
    val shape = RoundedCornerShape(30)
    Column(
        modifier
            .graphicsLayer {
                val wobble = if (s.cornerFloat) ((bob + phase) % 1f - 0.5f) * 10f else 0f
                translationY = wobble
                scaleX = scale; scaleY = scale
                alpha = s.cornerOpacity
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(sizeDp)
                .drawBehind {
                    if (s.cornerGlow) {
                        drawCircle(
                            Brush.radialGradient(listOf(color.copy(alpha = 0.55f * glowPulse), Color.Transparent)),
                            radius = size.minDimension * 0.95f,
                        )
                    }
                }
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), color.copy(alpha = 0.25f))))
                .border(2.dp, color, shape)
                .clickable(interaction, null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            RetroGlyph(action.glyph(), color, Modifier.size(sizeDp * 0.5f))
        }
        if (s.cornerLabels) {
            Spacer(Modifier.height(3.dp))
            Text(
                action.englishLabel(),
                style = TextStyle(
                    fontFamily = LocalRetroFont.current, fontSize = 10.sp, color = color, letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold, shadow = Shadow(color, Offset.Zero, 10f),
                ),
            )
        }
    }
}

// ─────────────────────────────────────────────
// ドック
// ─────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Dock(
    apps: List<AppInfo>,
    icons: Map<String, ImageBitmap>,
    badges: Map<String, Int>,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) return
    val s = LocalSettings.current
    val p = LocalPalette.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .let {
                if (s.dockGlass) it.clip(shape)
                    .background(Brush.verticalGradient(listOf(p.purple.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.45f))))
                    .border(1.dp, Brush.horizontalGradient(listOf(p.pink, p.cyan)), shape)
                else it
            }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        apps.forEach { app ->
            val interaction = remember(app.key) { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val sc by animateFloatAsState(if (pressed) 0.8f else 1f, spring(dampingRatio = 0.35f), label = "dock")
            Box(
                Modifier
                    .scale(sc)
                    .combinedClickable(interaction, null, onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) }),
            ) {
                AppIcon(icons[app.key], app.label, s.dockIconSizeDp.dp)
                val count = if (app.isWorkProfile) 0 else badges[app.packageName] ?: 0
                if (s.showBadges && count > 0) {
                    Badge(count, s.badgeShowCount, Modifier.align(Alignment.TopEnd))
                }
            }
        }
    }
}

/** 通知件数のバッジ */
@Composable
fun Badge(count: Int, showCount: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val pulse by rememberInfiniteTransition(label = "badge").animateFloat(
        0.85f, 1.1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse",
    )
    Box(
        modifier
            .scale(pulse)
            .size(if (showCount) 19.dp else 11.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(p.pink)
            .border(1.5.dp, Color.White, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (showCount) {
            Text(
                if (count > 99) "99+" else count.toString(),
                color = Color.White, fontSize = if (count > 9) 8.sp else 10.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

enum class SwipeDir { UP, DOWN, LEFT, RIGHT }

/**
 * 1 本指のスワイプを検出する。タップや長押しは子要素にそのまま届く。
 * [enabled] で方向ごとに有効/無効を切り替え、無効な方向のドラッグは消費しない。
 */
fun Modifier.swipeGestures(enabled: (SwipeDir) -> Boolean, onSwipe: (SwipeDir) -> Unit): Modifier = composed {
    val isEnabled by rememberUpdatedState(enabled)
    val callback by rememberUpdatedState(onSwipe)
    pointerInput(Unit) {
        val threshold = 56.dp.toPx()
        awaitEachGesture {
            // Initial パスで先に受け取り、スワイプと判定したら子（時計のタップ等）に渡さない
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var total = Offset.Zero
            var fired = false
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) {
                    if (fired) ch.consume()
                    break
                }
                if (ev.changes.count { it.pressed } > 1) break // 2 本指は別ジェスチャー
                total += ch.positionChange()
                if (!fired && total.getDistance() > threshold) {
                    val dir = if (abs(total.x) > abs(total.y)) {
                        if (total.x > 0) SwipeDir.RIGHT else SwipeDir.LEFT
                    } else {
                        if (total.y > 0) SwipeDir.DOWN else SwipeDir.UP
                    }
                    if (!isEnabled(dir)) break
                    fired = true
                    callback(dir)
                }
                if (fired) ch.consume()
            }
        }
    }
}
