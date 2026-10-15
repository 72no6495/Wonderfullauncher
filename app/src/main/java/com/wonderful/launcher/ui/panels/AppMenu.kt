// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (c) 2026 72no6495

package com.wonderful.launcher.ui.panels

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wonderful.launcher.data.AppInfo
import com.wonderful.launcher.data.LauncherData
import com.wonderful.launcher.data.SystemActions
import com.wonderful.launcher.ui.LauncherViewModel
import com.wonderful.launcher.ui.components.AppIcon
import com.wonderful.launcher.ui.components.Glyph
import com.wonderful.launcher.ui.components.NeonText
import com.wonderful.launcher.ui.components.RetroGlyph
import com.wonderful.launcher.ui.components.RetroLabel
import com.wonderful.launcher.ui.components.RetroWindow
import com.wonderful.launcher.ui.theme.LocalPalette

/** アプリ長押しで出るメニュー */
@Composable
fun AppMenu(
    app: AppInfo,
    icon: ImageBitmap?,
    data: LauncherData,
    vm: LauncherViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    var folderMode by remember { mutableStateOf(false) }
    val inSphere = app.key in data.sphereApps
    val inDock = app.key in data.dockApps
    val hidden = app.key in data.hiddenApps

    RetroWindow("APP_MENU.EXE", onDismiss, modifier) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon, app.label, 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                NeonText(app.label, size = 18.sp, maxLines = 2)
                RetroLabel(app.packageName, size = 10.sp, color = p.cyan.copy(alpha = 0.7f))
                if (app.isWorkProfile) RetroLabel("仕事用プロファイル", size = 10.sp, color = p.yellow)
            }
        }
        AnimatedContent(
            folderMode,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "menu",
        ) { fm ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!fm) {
                    MenuItem(Glyph.SPHERE, if (inSphere) "スフィアから外す" else "スフィアに追加", if (inSphere) p.pink else p.mint) {
                        vm.toggleSphere(app.key); onDismiss()
                    }
                    MenuItem(Glyph.FOLDER, "フォルダに追加…", p.cyan) { folderMode = true }
                    MenuItem(Glyph.STAR, if (inDock) "ドックから外す" else "ドックに追加", p.yellow) {
                        vm.toggleDock(app.key); onDismiss()
                    }
                    if (inDock) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MenuItem(Glyph.BACK, "ドックで左へ", p.yellow, Modifier.weight(1f)) { vm.moveDock(app.key, -1) }
                            MenuItem(Glyph.BACK, "右へ", p.yellow, Modifier.weight(1f), flip = true) { vm.moveDock(app.key, 1) }
                        }
                    }
                    val isPet = data.pets.any { it.key == app.key }
                    MenuItem(Glyph.PLUS, if (isPet) "飼育をやめる" else "飼育する（横持ちモード）", p.mint) {
                        if (!vm.togglePet(app.key)) {
                            android.widget.Toast.makeText(context, "飼えるのは最大 ${com.wonderful.launcher.data.MAX_PETS} 体までです", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    }
                    MenuItem(Glyph.NONE, if (hidden) "非表示を解除" else "アプリを非表示", p.purple) {
                        vm.toggleHidden(app.key); onDismiss()
                    }
                    MenuItem(Glyph.EDIT, "アプリ情報", p.cyan) { SystemActions.openAppInfo(context, app); onDismiss() }
                    MenuItem(Glyph.TRASH, "アンインストール", p.pink) { SystemActions.uninstall(context, app); onDismiss() }
                } else {
                    MenuItem(Glyph.BACK, "戻る", p.text) { folderMode = false }
                    MenuItem(Glyph.PLUS, "新しいフォルダを作って追加", p.mint) {
                        vm.createFolder(app.label.take(10).uppercase(), data.folders.size, listOf(app.key))
                        onDismiss()
                    }
                    data.folders.forEach { f ->
                        val contains = app.key in f.apps
                        MenuItem(Glyph.FOLDER, f.name + if (contains) "  ✓" else "", p.folderColor(f.colorIndex)) {
                            if (contains) vm.removeFromFolder(f.id, app.key) else vm.addToFolder(f.id, app.key)
                            onDismiss()
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
            }
        }
    }
}

@Composable
private fun MenuItem(
    glyph: Glyph,
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    flip: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RetroGlyph(
            glyph, color,
            Modifier.size(20.dp).graphicsLayer { scaleX = if (flip) -1f else 1f },
        )
        Spacer(Modifier.width(12.dp))
        RetroLabel(text, color = color, size = 14.sp)
    }
}
