package com.wonderful.launcher.ui.panels

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wonderful.launcher.data.AppInfo
import com.wonderful.launcher.data.Folder
import com.wonderful.launcher.data.LauncherData
import com.wonderful.launcher.ui.components.AppIcon
import com.wonderful.launcher.ui.components.AppTile
import com.wonderful.launcher.ui.components.EmptyMessage
import com.wonderful.launcher.ui.components.Glyph
import com.wonderful.launcher.ui.components.NeonText
import com.wonderful.launcher.ui.components.RetroButton
import com.wonderful.launcher.ui.components.RetroGlyph
import com.wonderful.launcher.ui.components.RetroLabel
import com.wonderful.launcher.ui.components.RetroTextField
import com.wonderful.launcher.ui.components.RetroWindow
import com.wonderful.launcher.ui.theme.LocalPalette
import com.wonderful.launcher.ui.theme.RetroPalette

/** フォルダ操作のコールバックまとめ */
class FolderActions(
    val create: (name: String, color: Int, apps: List<String>) -> String,
    val rename: (id: String, name: String, color: Int) -> Unit,
    val delete: (id: String) -> Unit,
    val setApps: (id: String, apps: List<String>) -> Unit,
    val removeApp: (id: String, key: String) -> Unit,
    val move: (id: String, delta: Int) -> Unit,
)

private sealed interface FolderScreen {
    data object Grid : FolderScreen
    data class Detail(val id: String) : FolderScreen
    data class Edit(val id: String?) : FolderScreen // null = 新規作成
    data class Pick(val id: String) : FolderScreen
}

fun RetroPalette.folderColor(i: Int): Color = swatches[((i % swatches.size) + swatches.size) % swatches.size]

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderPanel(
    data: LauncherData,
    apps: List<AppInfo>,
    icons: Map<String, ImageBitmap>,
    actions: FolderActions,
    onLaunch: (AppInfo) -> Unit,
    onMenu: (AppInfo) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val s = data.settings
    var screen by remember { mutableStateOf<FolderScreen>(FolderScreen.Grid) }
    val byKey = remember(apps) { apps.associateBy { it.key } }

    val title = when (val sc = screen) {
        FolderScreen.Grid -> "FOLDERS.DIR"
        is FolderScreen.Detail -> (data.folders.firstOrNull { it.id == sc.id }?.name ?: "FOLDER").uppercase() + ".DIR"
        is FolderScreen.Edit -> if (sc.id == null) "NEW_FOLDER.EXE" else "RENAME.EXE"
        is FolderScreen.Pick -> "ADD_APPS.EXE"
    }

    // 1 段階ずつ戻る
    fun back() {
        screen = when (val sc = screen) {
            FolderScreen.Grid -> { onClose(); FolderScreen.Grid }
            is FolderScreen.Detail -> FolderScreen.Grid
            is FolderScreen.Edit -> if (sc.id != null) FolderScreen.Detail(sc.id) else FolderScreen.Grid
            is FolderScreen.Pick -> FolderScreen.Detail(sc.id)
        }
    }
    BackHandler(enabled = screen !is FolderScreen.Grid) { back() }

    RetroWindow(title = title, onClose = { back() }, modifier = modifier) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val forward = targetState !is FolderScreen.Grid
                if (forward) {
                    (slideInHorizontally(tween(320)) { it / 2 } + fadeIn(tween(250)) + scaleIn(initialScale = 0.9f)) togetherWith
                        (slideOutHorizontally(tween(280)) { -it / 3 } + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally(tween(320)) { -it / 2 } + fadeIn(tween(250))) togetherWith
                        (slideOutHorizontally(tween(280)) { it / 3 } + fadeOut(tween(200)) + scaleOut(targetScale = 0.9f))
                }
            },
            label = "folder",
        ) { sc ->
            when (sc) {
                FolderScreen.Grid -> FolderGrid(
                    data.folders, byKey, icons,
                    onOpen = { screen = FolderScreen.Detail(it) },
                    onCreate = { screen = FolderScreen.Edit(null) },
                    onMove = actions.move,
                )
                is FolderScreen.Detail -> {
                    val folder = data.folders.firstOrNull { it.id == sc.id }
                    if (folder == null) {
                        EmptyMessage("フォルダが見つかりません")
                    } else {
                        FolderDetail(
                            folder, byKey, icons, s.folderColumns, s.folderShowLabels,
                            onLaunch = onLaunch, onMenu = onMenu,
                            onRemove = { actions.removeApp(folder.id, it) },
                            onAdd = { screen = FolderScreen.Pick(folder.id) },
                            onEdit = { screen = FolderScreen.Edit(folder.id) },
                            onDelete = { actions.delete(folder.id); screen = FolderScreen.Grid },
                        )
                    }
                }
                is FolderScreen.Edit -> {
                    val folder = sc.id?.let { id -> data.folders.firstOrNull { it.id == id } }
                    FolderEditor(
                        initialName = folder?.name ?: "",
                        initialColor = folder?.colorIndex ?: data.folders.size,
                        isNew = folder == null,
                        onDone = { name, color ->
                            screen = if (folder == null) {
                                FolderScreen.Pick(actions.create(name, color, emptyList()))
                            } else {
                                actions.rename(folder.id, name, color)
                                FolderScreen.Detail(folder.id)
                            }
                        },
                    )
                }
                is FolderScreen.Pick -> {
                    val folder = data.folders.firstOrNull { it.id == sc.id }
                    AppPicker(
                        apps = apps.filter { it.key !in data.hiddenApps },
                        icons = icons,
                        initial = folder?.apps.orEmpty(),
                        onDone = { picked ->
                            actions.setApps(sc.id, picked)
                            screen = FolderScreen.Detail(sc.id)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderGrid(
    folders: List<Folder>,
    byKey: Map<String, AppInfo>,
    icons: Map<String, ImageBitmap>,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onMove: (String, Int) -> Unit,
) {
    val p = LocalPalette.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(folders, key = { it.id }) { folder ->
            val color = p.folderColor(folder.colorIndex)
            val shape = RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .animateItem()
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Black.copy(alpha = 0.4f))))
                    .border(2.dp, color, shape)
                    .combinedClickable(
                        onClick = { onOpen(folder.id) },
                        onLongClick = { onMove(folder.id, -1) },
                    )
                    .padding(10.dp),
            ) {
                // 中身のプレビュー（2x2）
                Box(Modifier.fillMaxWidth().aspectRatio(1.4f), contentAlignment = Alignment.Center) {
                    val preview = folder.apps.mapNotNull { byKey[it] }.take(4)
                    if (preview.isEmpty()) {
                        RetroGlyph(Glyph.FOLDER, color, Modifier.size(52.dp))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            preview.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { AppIcon(icons[it.key], it.label, 34.dp) }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                NeonText(folder.name, size = 14.sp, glow = color, chromatic = false)
                RetroLabel("${folder.apps.size} apps", size = 11.sp, color = color)
            }
        }
        item {
            val shape = RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .clip(shape)
                    .border(2.dp, Brush.linearGradient(listOf(p.pink, p.cyan)), shape)
                    .clickable(onClick = onCreate)
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1.4f), contentAlignment = Alignment.Center) {
                    RetroGlyph(Glyph.PLUS, p.pink, Modifier.size(48.dp))
                }
                Spacer(Modifier.height(6.dp))
                NeonText("NEW FOLDER", size = 14.sp, chromatic = false)
                RetroLabel("新しく作る", size = 11.sp, color = p.cyan)
            }
        }
        if (folders.size > 1) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                RetroLabel("フォルダ長押しで前へ移動", size = 11.sp, color = p.text.copy(alpha = 0.5f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderDetail(
    folder: Folder,
    byKey: Map<String, AppInfo>,
    icons: Map<String, ImageBitmap>,
    columns: Int,
    showLabels: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onMenu: (AppInfo) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = LocalPalette.current
    var removeMode by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val list = folder.apps.mapNotNull { byKey[it] }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RetroButton("+ 追加", onAdd, color = p.mint)
            RetroButton("名前/色", onEdit, color = p.cyan)
            RetroButton(if (removeMode) "完了" else "外す", { removeMode = !removeMode }, color = p.yellow, filled = removeMode)
            RetroButton(if (confirmDelete) "本当に?" else "削除", {
                if (confirmDelete) onDelete() else confirmDelete = true
            }, color = p.pink, filled = confirmDelete)
        }
        if (list.isEmpty()) {
            EmptyMessage("空っぽのフォルダ\n「+ 追加」でアプリを入れよう")
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns.coerceIn(3, 6)),
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
        ) {
            items(list, key = { it.key }) { app ->
                AppTile(
                    app, icons[app.key],
                    onClick = { if (removeMode) onRemove(app.key) else onLaunch(app) },
                    onLongClick = { onMenu(app) },
                    showLabel = showLabels,
                    badge = if (removeMode) "−" else null,
                    badgeColor = p.pink,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun FolderEditor(
    initialName: String,
    initialColor: Int,
    isNew: Boolean,
    onDone: (String, Int) -> Unit,
) {
    val p = LocalPalette.current
    var name by rememberSaveable { mutableStateOf(initialName) }
    var color by rememberSaveable { mutableStateOf(initialColor % p.swatches.size) }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        RetroLabel("フォルダ名", color = p.cyan, size = 12.sp)
        Spacer(Modifier.height(6.dp))
        RetroTextField(name, { name = it }, "例: GAMES", Modifier.fillMaxWidth(), onAction = { onDone(name.trim(), color) })
        Spacer(Modifier.height(16.dp))
        RetroLabel("カラー", color = p.cyan, size = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            p.swatches.forEachIndexed { i, c ->
                Box(
                    Modifier
                        .size(if (i == color) 40.dp else 34.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(if (i == color) 3.dp else 1.dp, if (i == color) Color.White else Color.Black, CircleShape)
                        .clickable { color = i },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        // プリセット名
        RetroLabel("おすすめ", color = p.cyan, size = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("GAMES", "SNS", "TOOLS", "MUSIC").forEach { preset ->
                RetroButton(preset, { name = preset }, color = p.purple)
            }
        }
        Spacer(Modifier.height(24.dp))
        RetroButton(
            if (isNew) "作成してアプリを選ぶ ▶" else "保存",
            { onDone(name.trim().ifEmpty { "FOLDER" }, color) },
            Modifier.fillMaxWidth(), color = p.pink, filled = true,
        )
    }
}

/** 複数選択できるアプリピッカー */
@Composable
fun AppPicker(
    apps: List<AppInfo>,
    icons: Map<String, ImageBitmap>,
    initial: List<String>,
    onDone: (List<String>) -> Unit,
) {
    val p = LocalPalette.current
    val selected = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    var query by rememberSaveable { mutableStateOf("") }
    val q = normalizeForSearch(query.trim())
    val filtered = remember(q, apps) { apps.filter { matchScore(it, q, false) > 0 } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RetroTextField(query, { query = it }, "絞り込み…", Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            RetroButton("決定 (${selected.size})", { onDone(selected.toList()) }, color = p.mint, filled = true)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
        ) {
            items(filtered, key = { it.key }) { app ->
                val on = app.key in selected
                AppTile(
                    app, icons[app.key],
                    onClick = { if (on) selected.remove(app.key) else selected.add(app.key) },
                    badge = if (on) "✓" else null,
                    dimmed = !on,
                )
            }
        }
    }
}
