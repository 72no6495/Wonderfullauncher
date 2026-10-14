package com.wonderful.launcher.ui.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wonderful.launcher.data.AppInfo
import com.wonderful.launcher.data.LauncherData
import com.wonderful.launcher.data.SystemActions
import com.wonderful.launcher.ui.components.AppTile
import com.wonderful.launcher.ui.components.EmptyMessage
import com.wonderful.launcher.ui.components.RetroButton
import com.wonderful.launcher.ui.components.RetroChip
import com.wonderful.launcher.ui.components.RetroLabel
import com.wonderful.launcher.ui.components.RetroTextField
import com.wonderful.launcher.ui.components.RetroWindow
import com.wonderful.launcher.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import java.text.Normalizer

/** 検索の正規化：大文字小文字・全角半角・カタカナ/ひらがなを同一視 */
fun normalizeForSearch(s: String): String {
    val n = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()
    val sb = StringBuilder(n.length)
    for (c in n) sb.append(if (c in 'ァ'..'ヶ') (c - 0x60) else c)
    return sb.toString()
}

/** 頭文字一致・部分一致・頭字語一致でスコアを付ける */
fun matchScore(app: AppInfo, q: String, withPackage: Boolean): Int {
    if (q.isEmpty()) return 1
    val label = normalizeForSearch(app.label)
    return when {
        label == q -> 100
        label.startsWith(q) -> 80
        label.split(' ', '-', '_', '・').any { it.startsWith(q) } -> 60
        label.contains(q) -> 40
        initials(label).startsWith(q) -> 30
        withPackage && app.packageName.lowercase().contains(q) -> 10
        else -> 0
    }
}

private fun initials(s: String) = s.split(' ', '-', '_').mapNotNull { it.firstOrNull() }.joinToString("")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchPanel(
    data: LauncherData,
    apps: List<AppInfo>,
    icons: Map<String, ImageBitmap>,
    onLaunch: (AppInfo) -> Unit,
    onMenu: (AppInfo) -> Unit,
    onToggleSphere: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = data.settings
    val p = LocalPalette.current
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    var editMode by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(0) } // 0=全部 1=スフィア 2=未追加 3=非表示
    val focus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        if (s.searchAutoKeyboard) {
            delay(250) // パネルのアニメーションが落ち着いてから
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }

    val q = normalizeForSearch(query.trim())
    val sphereSet = remember(data.sphereApps) { data.sphereApps.toSet() }
    val results = remember(q, apps, data.hiddenApps, sphereSet, filter, s.searchPackageNames, s.searchShowHidden) {
        apps.asSequence()
            .filter { app ->
                val hidden = app.key in data.hiddenApps
                when (filter) {
                    1 -> app.key in sphereSet && !hidden
                    2 -> app.key !in sphereSet && !hidden
                    3 -> hidden
                    else -> !hidden || s.searchShowHidden
                }
            }
            .map { it to matchScore(it, q, s.searchPackageNames) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .toList()
    }
    LaunchedEffect(q) { gridState.scrollToItem(0) }

    RetroWindow(
        title = if (editMode) "SPHERE_EDIT.EXE" else "SEARCH.EXE",
        onClose = onClose,
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            RetroTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "アプリを検索…",
                focusRequester = focus,
                imeAction = ImeAction.Go,
                onAction = {
                    if (s.searchEnterLaunches && results.isNotEmpty()) onLaunch(results.first())
                    else if (query.isNotBlank() && s.searchWebFallback) SystemActions.webSearch(context, query)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                RetroChip("全部", filter == 0, { filter = 0 })
                RetroChip("球体内", filter == 1, { filter = 1 })
                RetroChip("未追加", filter == 2, { filter = 2 })
                RetroChip("非表示", filter == 3, { filter = 3 })
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RetroLabel(
                    if (editMode) "タップでスフィアに追加 / 削除" else "${results.size} APPS  ·  長押しでメニュー",
                    size = 11.sp, color = p.cyan, modifier = Modifier.weight(1f),
                )
                RetroButton(
                    if (editMode) "完了" else "球体を編集",
                    onClick = { editMode = !editMode; keyboard?.hide() },
                    color = if (editMode) p.yellow else p.pink,
                    filled = editMode,
                )
            }
        }
        AnimatedVisibility(
            visible = results.isEmpty(),
            enter = fadeIn(tween(200)) + expandVertically(),
            exit = fadeOut(tween(150)) + shrinkVertically(),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyMessage("NO SIGNAL… 該当するアプリなし")
                if (query.isNotBlank() && s.searchWebFallback) {
                    RetroButton("「$query」をWEBで検索", { SystemActions.webSearch(context, query) }, color = p.yellow)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(s.searchColumns.coerceIn(3, 6)),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
        ) {
            items(results, key = { it.key }) { app ->
                val inSphere = app.key in sphereSet
                AppTile(
                    app = app,
                    icon = icons[app.key],
                    onClick = { if (editMode) onToggleSphere(app.key) else onLaunch(app) },
                    onLongClick = { onMenu(app) },
                    badge = if (editMode) (if (inSphere) "✓" else "+") else if (inSphere) "●" else null,
                    badgeColor = if (editMode && !inSphere) p.pink else p.mint,
                    dimmed = editMode && !inSphere,
                    modifier = Modifier.animateItem(),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
        }
    }
}
