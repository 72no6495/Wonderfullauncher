package com.wonderful.launcher.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wonderful.launcher.data.AppInfo
import com.wonderful.launcher.ui.theme.LocalPalette
import com.wonderful.launcher.ui.theme.LocalRetroFont
import com.wonderful.launcher.ui.theme.LocalSettings
import com.wonderful.launcher.ui.theme.jelly
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────
// テキスト
// ─────────────────────────────────────────────

/** ネオン発光＋色収差（赤青ずれ）のテキスト */
@Composable
fun NeonText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    color: Color = LocalPalette.current.text,
    glow: Color = LocalPalette.current.pink,
    weight: FontWeight = FontWeight.Bold,
    letterSpacing: TextUnit = 1.5.sp,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    chromatic: Boolean = LocalSettings.current.chromaticText,
) {
    val p = LocalPalette.current
    val font = LocalRetroFont.current
    val base = TextStyle(
        fontFamily = font,
        fontSize = size,
        fontWeight = weight,
        letterSpacing = letterSpacing,
        textAlign = align,
    )
    Box(modifier) {
        if (chromatic) {
            Text(
                text, style = base.copy(color = p.cyan.copy(alpha = 0.55f)), maxLines = maxLines,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.graphicsLayer { translationX = 2.5f },
            )
            Text(
                text, style = base.copy(color = p.pink.copy(alpha = 0.55f)), maxLines = maxLines,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.graphicsLayer { translationX = -2.5f },
            )
        }
        Text(
            text,
            style = base.copy(color = color, shadow = Shadow(glow, Offset.Zero, 18f)),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun RetroLabel(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 13.sp,
    color: Color = LocalPalette.current.text,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
    weight: FontWeight = FontWeight.Normal,
) {
    Text(
        text,
        modifier = modifier,
        style = TextStyle(fontFamily = LocalRetroFont.current, fontSize = size, color = color, fontWeight = weight, textAlign = align),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

// ─────────────────────────────────────────────
// ウィンドウ
// ─────────────────────────────────────────────

/**
 * Windows 95 風 × ヴェイパーウェイヴのウィンドウ枠。
 * タイトルバーのグラデーションが流れ、✕ で閉じる。
 */
@Composable
fun RetroWindow(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    val s = LocalSettings.current
    val shift by rememberInfiniteTransition(label = "title").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween((4000 / s.animSpeed).toInt(), easing = LinearEasing), RepeatMode.Reverse),
        label = "shift",
    )
    val shape = RoundedCornerShape(if (s.windowChrome) 4.dp else 18.dp)
    Column(
        modifier
            .clip(shape)
            .background(p.panel.copy(alpha = s.panelOpacity))
            .border(2.dp, Brush.linearGradient(listOf(p.pink, p.cyan, p.purple)), shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val w = size.width
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(p.titleStart, p.titleEnd, p.titleStart),
                            startX = -w * shift, endX = w * (2f - shift),
                        ),
                    )
                    // 走査線っぽい横縞
                    var y = 0f
                    while (y < size.height) {
                        drawLine(Color.Black.copy(alpha = 0.12f), Offset(0f, y), Offset(w, y), 1f)
                        y += 4f
                    }
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeonText(
                title, size = 15.sp, color = Color.White, glow = p.purple,
                modifier = Modifier.weight(1f), chromatic = false,
            )
            actions()
            if (s.windowChrome) {
                WinButton("_") {}
                Spacer(Modifier.width(4.dp))
                WinButton("□") {}
                Spacer(Modifier.width(4.dp))
            }
            WinButton("✕", onClose)
        }
        Column(Modifier.fillMaxWidth().weight(1f, fill = false), content = content)
    }
}

@Composable
private fun WinButton(label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(26.dp)
            .background(Color(0xFFC0C0C0))
            .drawBehind {
                // 立体的な 95 風ボタン枠
                val w = size.width; val h = size.height
                drawLine(Color.White, Offset(0f, 0f), Offset(w, 0f), 3f)
                drawLine(Color.White, Offset(0f, 0f), Offset(0f, h), 3f)
                drawLine(Color(0xFF404040), Offset(0f, h), Offset(w, h), 3f)
                drawLine(Color(0xFF404040), Offset(w, 0f), Offset(w, h), 3f)
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (label == "✕") p.purple else Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────
// ボタン
// ─────────────────────────────────────────────

@Composable
fun RetroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.cyan,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = LocalSettings.current.jelly(0.4f)), label = "btn")
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .scale(scale)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(shape)
            .background(if (filled) color.copy(alpha = 0.85f) else color.copy(alpha = 0.12f))
            .border(1.5.dp, color, shape)
            .clickable(interaction, null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        RetroLabel(text, color = if (filled) Color.Black else color, weight = FontWeight.Bold, size = 13.sp)
    }
}

/** レトロな入力欄 */
@Composable
fun RetroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Done,
    onAction: () -> Unit = {},
    leading: String = "▶",
) {
    val p = LocalPalette.current
    val font = LocalRetroFont.current
    val blink by rememberInfiniteTransition(label = "caret").animateFloat(
        0f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "blink",
    )
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.5.dp, Brush.horizontalGradient(listOf(p.cyan, p.pink)), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RetroLabel(leading, color = p.pink.copy(alpha = 0.5f + blink * 0.5f), size = 15.sp)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) RetroLabel(placeholder, color = p.text.copy(alpha = 0.4f), size = 15.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 15.sp, fontFamily = font),
                cursorBrush = SolidColor(p.cyan),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(onAny = { onAction() }),
                modifier = Modifier.fillMaxWidth().let { m -> focusRequester?.let { m.focusRequester(it) } ?: m },
            )
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            RetroLabel("✕", color = p.cyan, modifier = Modifier.clickable { onValueChange("") }, size = 15.sp)
        }
    }
}

// ─────────────────────────────────────────────
// アプリのタイル
// ─────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppInfo,
    icon: ImageBitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    showLabel: Boolean = true,
    iconSize: Dp = 52.dp,
    badge: String? = null,
    badgeColor: Color = LocalPalette.current.mint,
    dimmed: Boolean = false,
) {
    val p = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.85f else 1f,
        spring(dampingRatio = LocalSettings.current.jelly(0.35f), stiffness = 600f),
        label = "tile",
    )
    Column(
        modifier
            .scale(scale)
            .graphicsLayer { alpha = if (dimmed) 0.4f else 1f }
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(interaction, null, onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIcon(icon, app.label, iconSize)
            if (badge != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                        .border(1.dp, Color.Black, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(badge, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            RetroLabel(app.label, size = 11.sp, align = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = p.text)
        }
    }
}

@Composable
fun AppIcon(icon: ImageBitmap?, label: String, size: Dp, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    if (icon != null) {
        Image(icon, contentDescription = label, modifier = modifier.size(size))
    } else {
        Box(modifier.size(size).clip(CircleShape).background(p.purple), contentAlignment = Alignment.Center) {
            Text(label.take(1), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────
// 自前で描くレトロなアイコン（グリフ）
// ─────────────────────────────────────────────

enum class Glyph { SEARCH, SETTINGS, RECENT, FOLDER, WIDGETS, NONE, PLUS, TRASH, EDIT, SPHERE, STAR, BACK, KEY }

@Composable
fun RetroGlyph(glyph: Glyph, color: Color, modifier: Modifier = Modifier, stroke: Dp = 2.2.dp) {
    Canvas(modifier) {
        val sw = stroke.toPx()
        val st = Stroke(sw, cap = StrokeCap.Round)
        val w = size.width; val h = size.height
        val c = Offset(w / 2, h / 2)
        when (glyph) {
            Glyph.SEARCH -> {
                drawCircle(color, w * 0.28f, Offset(w * 0.42f, h * 0.42f), style = st)
                drawLine(color, Offset(w * 0.62f, h * 0.62f), Offset(w * 0.86f, h * 0.86f), sw * 1.3f, StrokeCap.Round)
            }
            Glyph.SETTINGS -> gear(color, c, w, st, sw)
            Glyph.RECENT -> {
                drawCircle(color, w * 0.38f, c, style = st)
                drawLine(color, c, Offset(c.x, h * 0.25f), sw, StrokeCap.Round)
                drawLine(color, c, Offset(w * 0.68f, c.y), sw, StrokeCap.Round)
                // 反時計回りの矢印
                drawLine(color, Offset(w * 0.06f, h * 0.34f), Offset(w * 0.14f, h * 0.48f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * 0.14f, h * 0.48f), Offset(w * 0.28f, h * 0.42f), sw, StrokeCap.Round)
            }
            Glyph.FOLDER -> {
                val path = Path().apply {
                    moveTo(w * 0.1f, h * 0.25f)
                    lineTo(w * 0.4f, h * 0.25f)
                    lineTo(w * 0.48f, h * 0.35f)
                    lineTo(w * 0.9f, h * 0.35f)
                    lineTo(w * 0.9f, h * 0.8f)
                    lineTo(w * 0.1f, h * 0.8f)
                    close()
                }
                drawPath(path, color, style = st)
                drawLine(color, Offset(w * 0.1f, h * 0.45f), Offset(w * 0.9f, h * 0.45f), sw)
            }
            Glyph.WIDGETS -> {
                // 大小のタイル
                drawRoundRect(color, Offset(w * 0.12f, h * 0.12f), Size(w * 0.76f, h * 0.32f), CornerRadius(sw * 1.5f), style = st)
                drawRoundRect(color, Offset(w * 0.12f, h * 0.56f), Size(w * 0.32f, h * 0.32f), CornerRadius(sw * 1.5f), style = st)
                drawRoundRect(color, Offset(w * 0.56f, h * 0.56f), Size(w * 0.32f, h * 0.32f), CornerRadius(sw * 1.5f), style = st)
            }
            Glyph.KEY -> {
                drawCircle(color, w * 0.18f, Offset(w * 0.3f, h * 0.5f), style = st)
                drawLine(color, Offset(w * 0.48f, h * 0.5f), Offset(w * 0.88f, h * 0.5f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * 0.75f, h * 0.5f), Offset(w * 0.75f, h * 0.66f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * 0.86f, h * 0.5f), Offset(w * 0.86f, h * 0.62f), sw, StrokeCap.Round)
            }
            Glyph.PLUS -> {
                drawLine(color, Offset(c.x, h * 0.2f), Offset(c.x, h * 0.8f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * 0.2f, c.y), Offset(w * 0.8f, c.y), sw, StrokeCap.Round)
            }
            Glyph.TRASH -> {
                drawLine(color, Offset(w * 0.18f, h * 0.25f), Offset(w * 0.82f, h * 0.25f), sw, StrokeCap.Round)
                drawRoundRect(color, Offset(w * 0.27f, h * 0.3f), Size(w * 0.46f, h * 0.55f), CornerRadius(sw), style = st)
                drawLine(color, Offset(w * 0.4f, h * 0.15f), Offset(w * 0.6f, h * 0.15f), sw, StrokeCap.Round)
            }
            Glyph.EDIT -> {
                drawLine(color, Offset(w * 0.2f, h * 0.8f), Offset(w * 0.75f, h * 0.25f), sw * 1.6f, StrokeCap.Round)
                drawLine(color, Offset(w * 0.15f, h * 0.88f), Offset(w * 0.32f, h * 0.88f), sw, StrokeCap.Round)
            }
            Glyph.SPHERE -> {
                drawCircle(color, w * 0.38f, c, style = st)
                drawOval(color, Offset(w * 0.12f, h * 0.38f), Size(w * 0.76f, h * 0.24f), style = st)
                drawOval(color, Offset(w * 0.38f, h * 0.12f), Size(w * 0.24f, h * 0.76f), style = st)
            }
            Glyph.STAR -> {
                val path = Path()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) w * 0.42f else w * 0.18f
                    val a = -PI / 2 + i * PI / 5
                    val pt = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                    if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                }
                path.close()
                drawPath(path, color, style = st)
            }
            Glyph.BACK -> {
                drawLine(color, Offset(w * 0.65f, h * 0.2f), Offset(w * 0.32f, h * 0.5f), sw * 1.3f, StrokeCap.Round)
                drawLine(color, Offset(w * 0.32f, h * 0.5f), Offset(w * 0.65f, h * 0.8f), sw * 1.3f, StrokeCap.Round)
            }
            Glyph.NONE -> {}
        }
    }
}

private fun DrawScope.gear(color: Color, c: Offset, w: Float, st: Stroke, sw: Float) {
    drawCircle(color, w * 0.14f, c, style = st)
    val path = Path()
    val teeth = 8
    for (i in 0 until teeth * 2) {
        val a1 = 2 * PI * i / (teeth * 2)
        val a2 = 2 * PI * (i + 1) / (teeth * 2)
        val r = if (i % 2 == 0) w * 0.42f else w * 0.32f
        val p1 = Offset(c.x + r * cos(a1).toFloat(), c.y + r * sin(a1).toFloat())
        val p2 = Offset(c.x + r * cos(a2).toFloat(), c.y + r * sin(a2).toFloat())
        if (i == 0) path.moveTo(p1.x, p1.y) else path.lineTo(p1.x, p1.y)
        path.lineTo(p2.x, p2.y)
    }
    path.close()
    drawPath(path, color, style = st)
}

// ─────────────────────────────────────────────
// その他
// ─────────────────────────────────────────────

/** セクションの見出し（ネオン下線つき） */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp)) {
        NeonText("// $text", size = 14.sp, color = p.cyan, glow = p.cyan)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(2.dp)
                .background(Brush.horizontalGradient(listOf(p.pink, p.cyan, Color.Transparent))),
        )
    }
}

/** 空のときの表示 */
@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val a by rememberInfiniteTransition(label = "empty").animateFloat(
        0.4f, 1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a",
    )
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        RetroLabel(text, color = p.text.copy(alpha = a), align = TextAlign.Center, maxLines = 4)
    }
}

@Composable
fun ChipRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
fun RetroChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val bg by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "chip")
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(p.pink.copy(alpha = 0.15f + 0.7f * bg))
            .border(1.dp, if (selected) p.yellow else p.pink.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(PaddingValues(horizontal = 12.dp, vertical = 6.dp)),
    ) {
        RetroLabel(text, size = 12.sp, color = if (selected) Color.Black else p.text, weight = FontWeight.Bold)
    }
}

/** 中央に置いたオーバーレイ（ダイアログ代わり） */
@Composable
fun BoxScope.Scrim(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
    )
}
