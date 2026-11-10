package com.wonderful.launcher.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.wonderful.launcher.data.AppInfo
import com.wonderful.launcher.data.CylinderAction
import com.wonderful.launcher.data.CylinderColor
import com.wonderful.launcher.data.CylinderConfig
import com.wonderful.launcher.data.CylinderItem
import com.wonderful.launcher.data.LauncherData
import com.wonderful.launcher.data.MAX_CYLINDER_ITEMS
import com.wonderful.launcher.data.SpecialPermission
import com.wonderful.launcher.system.CylinderService
import com.wonderful.launcher.ui.LauncherViewModel
import com.wonderful.launcher.ui.components.AppIcon
import com.wonderful.launcher.ui.components.RetroButton
import com.wonderful.launcher.ui.components.RetroLabel
import com.wonderful.launcher.ui.components.SectionHeader
import com.wonderful.launcher.ui.theme.LocalPalette

/** 設定 →「シリンダー」 */
@Composable
fun CylinderSettings(
    data: LauncherData,
    apps: List<AppInfo>,
    icons: Map<String, ImageBitmap>,
    vm: LauncherViewModel,
    onAddApp: () -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val c = data.settings.cylinder
    fun set(f: (CylinderConfig) -> CylinderConfig) = vm.updateSettings { it.copy(cylinder = f(it.cylinder)) }
    // 許可して戻ってきた時に表示を変える
    var granted by remember { mutableStateOf(SpecialPermission.OVERLAY.isGranted(context)) }
    LifecycleResumeEffect(Unit) {
        granted = SpecialPermission.OVERLAY.isGranted(context)
        onPauseOrDispose { }
    }

    SectionHeader("シリンダーメニュー")
    RetroLabel(
        "ほかのアプリを使っている時に、画面の右上の端を上から下へスワイプすると、ホログラムのリボルバーのシリンダーが出てきます。" +
            "上下に回して選び、穴をタップで起動。下から上へ素早くはじくか、しばらくさわらないと引っ込みます。",
        size = 11.sp, color = p.text.copy(alpha = 0.65f), maxLines = 6,
    )
    Spacer(Modifier.height(6.dp))
    if (!granted) {
        RetroLabel("「他のアプリに重ねて表示」の許可が必要です", size = 12.sp, color = p.yellow, maxLines = 2)
        Spacer(Modifier.height(4.dp))
        RetroButton("許可する", { SpecialPermission.OVERLAY.request(context) }, Modifier.fillMaxWidth(), color = p.yellow, filled = true)
        Spacer(Modifier.height(6.dp))
    }
    SwitchSetting("シリンダーメニューを使う", c.enabled) { v ->
        set { it.copy(enabled = v) }
        if (v && !granted) SpecialPermission.OVERLAY.request(context)
    }
    if (c.enabled && granted) {
        ActionSetting("試しに出す", "SHOW", "この画面の上に出してみます") { CylinderService.showNow(context) }
    }

    SectionHeader("並べるもの（${data.cylinderItems.size}/$MAX_CYLINDER_ITEMS）")
    RetroLabel("上から順に、シリンダーの穴に時計回りで入ります。* はユーザー補助の許可が必要", size = 11.sp, color = p.text.copy(alpha = 0.6f), maxLines = 2)
    val byKey = remember(apps) { apps.associateBy { it.key } }
    data.cylinderItems.forEachIndexed { i, item ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            val app = item.app?.let { byKey[it] }
            if (app != null) AppIcon(icons[app.key], app.label, 30.dp)
            else RetroLabel(item.action?.short ?: "?", size = 11.sp, color = p.cyan, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            RetroLabel(
                app?.label ?: item.action?.let { it.label + if (it.needsAccessibility) " *" else "" } ?: "（アンインストール済み）",
                modifier = Modifier.weight(1f),
            )
            RetroButton("▲", { vm.moveCylinderItem(i, -1) }, color = p.cyan, enabled = i > 0)
            Spacer(Modifier.width(4.dp))
            RetroButton("▼", { vm.moveCylinderItem(i, 1) }, color = p.cyan, enabled = i < data.cylinderItems.lastIndex)
            Spacer(Modifier.width(4.dp))
            RetroButton("✕", { vm.removeCylinderItem(i) }, color = p.pink)
        }
    }
    val full = data.cylinderItems.size >= MAX_CYLINDER_ITEMS
    Spacer(Modifier.height(6.dp))
    RetroButton("+ アプリを追加", onAddApp, Modifier.fillMaxWidth(), color = p.mint, enabled = !full)
    Spacer(Modifier.height(6.dp))
    RetroLabel("ショートカットを追加", size = 12.sp, color = p.cyan)
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CylinderAction.entries.forEach { a ->
            RetroButton(a.label + if (a.needsAccessibility) "*" else "", { vm.addCylinderItem(CylinderItem(action = a)) }, color = p.purple, enabled = !full)
        }
    }

    SectionHeader("反応する所（右上の端）")
    SwitchSetting("反応する所をうっすら表示", c.showTrigger, "位置合わせ用。ふだんは OFF で何も表示されません") { v -> set { it.copy(showTrigger = v) } }
    SliderSetting("幅", c.triggerWidthDp, 8f..60f, "広いほど反応しやすいが、その部分はほかのアプリをさわれなくなる", format = { "${it.toInt()}dp" }) { v -> set { it.copy(triggerWidthDp = v) } }
    SliderSetting("上からの位置", c.triggerTopDp, 0f..600f, "ステータスバーと重なると反応しないことがあります", format = { "${it.toInt()}dp" }) { v -> set { it.copy(triggerTopDp = v) } }
    SliderSetting("長さ", c.triggerLengthDp, 40f..600f, format = { "${it.toInt()}dp" }) { v -> set { it.copy(triggerLengthDp = v) } }

    SectionHeader("見た目")
    SliderSetting("大きさ", c.size, 0.35f..0.95f, format = { "${(it * 100).toInt()}%" }) { v -> set { it.copy(size = v) } }
    SliderSetting("縦の位置", c.centerY, 0.2f..0.8f, "上 ← → 下", format = { "${(it * 100).toInt()}%" }) { v -> set { it.copy(centerY = v) } }
    ChoiceSetting("ホログラムの色", CylinderColor.entries, c.color, { it.label }) { v -> set { it.copy(color = v) } }
    SwitchSetting("選んでいるものの名前を表示", c.showLabel) { v -> set { it.copy(showLabel = v) } }

    SectionHeader("動き")
    SliderSetting("自動で引っ込むまで", c.autoHideSec, 1f..60f, "この秒数さわらないと引っ込む", format = { "${it.toInt()}秒" }) { v -> set { it.copy(autoHideSec = v) } }
    SwitchSetting("シリンダーの外をさわったら引っ込む", c.closeOnOutside) { v -> set { it.copy(closeOnOutside = v) } }
    SwitchSetting("振動", c.haptics, "出た時・回して選んだ時にカチッと") { v -> set { it.copy(haptics = v) } }

    SectionHeader("軽量化")
    RetroLabel("出ていない時は右上の細い透明な帯があるだけで、何も描かず何も動きません。", size = 11.sp, color = p.text.copy(alpha = 0.6f), maxLines = 3)
    SwitchSetting("ホログラムのゆらぎ", c.hologramMotion, "OFF にすると、出ている間もさわっている時しか描き直さない") { v -> set { it.copy(hologramMotion = v) } }
    SwitchSetting("アイコンを覚えておく", c.cacheIcons, "ON だと 2 回目から速く出る代わりに、少しメモリを使う") { v -> set { it.copy(cacheIcons = v) } }
    SwitchSetting(
        "常駐して安定動作", c.keepAlive,
        "ON だと通知領域に「待機中」が常駐します（通知を許可していなければ表示されません）。OFF だとしばらくすると Android に止められることがあり、その時はホーム画面に戻ると再開します",
    ) { v -> set { it.copy(keepAlive = v) } }
}
