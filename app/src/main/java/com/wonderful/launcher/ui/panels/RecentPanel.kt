package com.wonderful.launcher.ui.panels

import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wonderful.launcher.data.AppInfo
import com.wonderful.launcher.data.LauncherSettings
import com.wonderful.launcher.data.RecentSource
import com.wonderful.launcher.data.SystemActions
import com.wonderful.launcher.ui.RecentItem
import com.wonderful.launcher.ui.components.AppIcon
import com.wonderful.launcher.ui.components.EmptyMessage
import com.wonderful.launcher.ui.components.NeonText
import com.wonderful.launcher.ui.components.RetroButton
import com.wonderful.launcher.ui.components.RetroLabel
import com.wonderful.launcher.ui.components.RetroWindow
import com.wonderful.launcher.ui.theme.LocalPalette

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentPanel(
    settings: LauncherSettings,
    items: List<RecentItem>,
    icons: Map<String, ImageBitmap>,
    onLaunch: (AppInfo) -> Unit,
    onMenu: (AppInfo) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val needsPermission = settings.recentSource == RecentSource.SYSTEM && !SystemActions.hasUsageAccess(context)

    RetroWindow("RECENT.LOG", onClose, modifier) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RetroLabel(
                "SOURCE: " + if (settings.recentSource == RecentSource.SYSTEM) "SYSTEM" else "LAUNCHER",
                color = p.cyan, size = 11.sp, modifier = Modifier.weight(1f),
            )
            if (settings.recentSource == RecentSource.LAUNCHER && items.isNotEmpty()) {
                RetroButton("履歴を消去", onClear, color = p.pink)
            }
        }
        if (needsPermission) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                RetroLabel("システム履歴を使うには「使用状況へのアクセス」の許可が必要です", maxLines = 3)
                Spacer(Modifier.size(10.dp))
                RetroButton("許可する", { SystemActions.openUsageAccessSettings(context) }, color = p.yellow, filled = true)
            }
        }
        if (items.isEmpty() && !needsPermission) {
            EmptyMessage("まだ履歴がありません\nスフィアからアプリを起動してみよう")
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(items, key = { _, it -> it.app.key }) { index, item ->
                // 上から順にスライドインする
                val anim = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(index * 35L)
                    anim.animateTo(1f, tween((380 / settings.animSpeed).toInt()))
                }
                val shape = RoundedCornerShape(10.dp)
                Row(
                    Modifier
                        .animateItem()
                        .graphicsLayer {
                            alpha = anim.value
                            translationX = (1f - anim.value) * -120f
                        }
                        .fillMaxWidth()
                        .clip(shape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    (if (index % 2 == 0) p.pink else p.cyan).copy(alpha = 0.18f),
                                    Color.Transparent,
                                ),
                            ),
                        )
                        .border(1.dp, (if (index % 2 == 0) p.pink else p.cyan).copy(alpha = 0.5f), shape)
                        .combinedClickable(onClick = { onLaunch(item.app) }, onLongClick = { onMenu(item.app) })
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NeonText(
                        "%02d".format(index + 1), size = 13.sp, color = p.yellow, glow = p.yellow,
                        chromatic = false,
                    )
                    Spacer(Modifier.width(10.dp))
                    AppIcon(icons[item.app.key], item.app.label, 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        RetroLabel(item.app.label, size = 15.sp)
                        if (settings.recentShowTime) {
                            RetroLabel(
                                DateUtils.getRelativeTimeSpanString(item.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                                size = 11.sp, color = p.cyan,
                            )
                        }
                    }
                    if (settings.recentSource == RecentSource.LAUNCHER) {
                        RetroLabel(
                            "✕", color = p.pink, size = 18.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .combinedClickable(onClick = { onRemove(item.app.key) })
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}
