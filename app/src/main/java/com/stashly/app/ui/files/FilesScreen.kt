package com.stashly.app.ui.files

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stashly.app.core.Formatters
import com.stashly.app.data.db.LibraryFileEntity
import com.stashly.app.data.repository.StorageInfo
import com.stashly.app.domain.model.FileCategory
import com.stashly.app.domain.model.SortOrder
import com.stashly.app.ui.components.EmptyState
import com.stashly.app.ui.components.FileIntents
import com.stashly.app.ui.components.FileThumb
import com.stashly.app.ui.components.SkeletonRow
import com.stashly.app.ui.components.StashCard
import com.stashly.app.ui.components.StashTopBar
import com.stashly.app.ui.components.categoryColor
import com.stashly.app.ui.theme.Brand

private sealed interface Dialog {
    data class Rename(val file: LibraryFileEntity) : Dialog
    data class Delete(val file: LibraryFileEntity) : Dialog
    data class Move(val file: LibraryFileEntity, val copy: Boolean) : Dialog
    data object NewFolder : Dialog
    data class RenameFolder(val path: String) : Dialog
    data class DeleteFolder(val path: String) : Dialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(onSettings: () -> Unit, onOpenFile: (LibraryFileEntity) -> Unit, vm: FilesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val audioState by vm.audio.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var sheetFile by remember { mutableStateOf<LibraryFileEntity?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = state.tab == FilesTab.FOLDERS && state.folder.isNotEmpty()) { vm.up() }

    val rowActions = FileRowActions(
        onOpen = onOpenFile,
        onMore = { sheetFile = it },
        onToggleAudio = { vm.audio.toggle(it.uri) },
        playingUri = audioState.uri.takeIf { audioState.isPlaying },
    )

    Scaffold(
        topBar = {
            Column {
                StashTopBar("Files", onSettings = onSettings, onSearch = { vm.setSearching(!state.searching) }, extraActions = {
                    Box {
                        IconButton({ sortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Sort") }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            SortOrder.entries.forEach { s ->
                                DropdownMenuItem({ Text(s.label + if (s == state.sort) "  ✓" else "") }, { vm.setSort(s); sortMenu = false })
                            }
                        }
                    }
                })
                if (state.searching) OutlinedTextField(
                    value = state.query, onValueChange = vm::setQuery, singleLine = true, placeholder = { Text("Search files") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { IconButton({ vm.setSearching(false) }) { Icon(Icons.Rounded.Close, "Close search") } },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
                PrimaryScrollableTabRow(selectedTabIndex = state.tab.ordinal, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background) {
                    FilesTab.entries.forEach { t -> Tab(selected = state.tab == t, onClick = { vm.setTab(t) }, text = { Text(t.label) }, modifier = Modifier.testTag("files_tab_${t.name}")) }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(pad)) {
            val isFolders = state.tab == FilesTab.FOLDERS
            val empty = state.files.isEmpty() && (!isFolders || state.subfolders.isEmpty())
            if (state.tab == FilesTab.IMAGES && state.files.isNotEmpty()) {
                LazyVerticalGrid(GridCells.Adaptive(110.dp), contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { StorageCard(state.storage) }
                    gridItems(state.files, key = { it.uri }) { f ->
                        Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp)).clickable { onOpenFile(f) }) {
                            FileThumb(f.uri, FileCategory.IMAGE, f.displayName, 220.dp, Modifier.fillMaxSize())
                            IconButton({ sheetFile = f }, Modifier.align(Alignment.TopEnd)) { Icon(Icons.Rounded.MoreVert, "More", tint = Color.White) }
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item { StorageCard(state.storage) }
                    if (isFolders) item { FolderHeader(state.folder, vm::openFolder, onNew = { dialog = Dialog.NewFolder }) }
                    if (state.loading) items(5) { SkeletonRow() }
                    if (isFolders) items(state.subfolders, key = { "folder:$it" }) { path ->
                        FolderRow(path, onOpen = { vm.openFolder(path) }, onRename = { dialog = Dialog.RenameFolder(path) }, onDelete = { dialog = Dialog.DeleteFolder(path) })
                    }
                    items(state.files, key = { it.uri }) { f -> FileRow(f, rowActions, showFolder = state.tab == FilesTab.DOWNLOADS) }
                    if (!state.loading && empty) item { EmptyFiles(state.tab, state.query.isNotBlank()) }
                }
            }
        }
    }

    // ---- bottom sheet with all file actions ----
    sheetFile?.let { f ->
        ModalBottomSheet(onDismissRequest = { sheetFile = null }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FileThumb(f.uri, FileCategory.valueOf(f.category), f.displayName, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(f.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(Formatters.bytes(f.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SheetItem(Icons.Rounded.PlayArrow, if (f.category == "DOCUMENT" || f.category == "IMAGE") "Open" else "Play") { sheetFile = null; onOpenFile(f) }
                SheetItem(Icons.Rounded.Share, "Share") { sheetFile = null; FileIntents.share(context, f.uri, f.mimeType, f.displayName) }
                SheetItem(Icons.Rounded.Edit, "Rename") { sheetFile = null; dialog = Dialog.Rename(f) }
                SheetItem(Icons.AutoMirrored.Rounded.DriveFileMove, "Move to folder") { sheetFile = null; dialog = Dialog.Move(f, copy = false) }
                SheetItem(Icons.Rounded.ContentCopy, "Copy to folder") { sheetFile = null; dialog = Dialog.Move(f, copy = true) }
                SheetItem(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.error) { sheetFile = null; dialog = Dialog.Delete(f) }
            }
        }
    }

    when (val d = dialog) {
        null -> {}
        is Dialog.Rename -> TextInputDialog("Rename file", d.file.displayName, "Rename", { dialog = null }) { vm.rename(d.file, it); dialog = null }
        is Dialog.NewFolder -> TextInputDialog("New folder", "", "Create", { dialog = null }) { vm.createFolder(it); dialog = null }
        is Dialog.RenameFolder -> TextInputDialog("Rename folder", d.path.substringAfterLast('/'), "Rename", { dialog = null }) { vm.renameFolder(d.path, it); dialog = null }
        is Dialog.Delete -> ConfirmDialog("Delete file?", "\"${d.file.displayName}\" will be permanently deleted from your device.", "Delete", { dialog = null }) { vm.delete(d.file); dialog = null }
        is Dialog.DeleteFolder -> ConfirmDialog("Delete folder?", "\"${d.path.substringAfterLast('/')}\" and all files inside it will be permanently deleted.", "Delete", { dialog = null }) { vm.deleteFolder(d.path); dialog = null }
        is Dialog.Move -> FolderPickerDialog(if (d.copy) "Copy to…" else "Move to…", state.allFolders, current = d.file.folder, onDismiss = { dialog = null }) { dest ->
            if (d.copy) vm.copy(d.file, dest) else vm.move(d.file, dest); dialog = null
        }
    }
}

@Composable
private fun SheetItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint)
        Spacer(Modifier.width(16.dp)); Text(label, color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint)
    }
}

// ---------------- storage overview ----------------
@Composable
private fun StorageCard(info: StorageInfo?) {
    if (info == null) return
    val cs = MaterialTheme.colorScheme
    StashCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("Storage", style = MaterialTheme.typography.titleMedium)
                Text("${Formatters.bytes(info.freeBytes)} free of ${Formatters.bytes(info.totalBytes)}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            val total = info.totalBytes.coerceAtLeast(1).toFloat()
            Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(cs.surfaceVariant)) {
                val app = (info.appBytes / total).coerceIn(0f, 1f); val other = (info.otherUsedBytes / total).coerceIn(0f, 1f - app)
                if (app > 0f) Box(Modifier.weight(app.coerceAtLeast(0.01f)).fillMaxSize().background(Brush.horizontalGradient(listOf(Brand.Violet, Brand.Teal))))
                if (other > 0f) Box(Modifier.weight(other).fillMaxSize().background(cs.outline.copy(alpha = 0.45f)))
                Spacer(Modifier.weight((1f - app - other).coerceAtLeast(0.01f)))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(Brand.Violet, "Stashly ${Formatters.bytes(info.appBytes)}")
                Legend(cs.outline, "Other ${Formatters.bytes(info.otherUsedBytes)}")
                Legend(cs.surfaceVariant, "Used ${Formatters.bytes(info.usedBytes)}")
            }
        }
    }
}

@Composable private fun Legend(color: Color, text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color)); Spacer(Modifier.width(6.dp))
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---------------- rows ----------------
data class FileRowActions(val onOpen: (LibraryFileEntity) -> Unit, val onMore: (LibraryFileEntity) -> Unit,
                          val onToggleAudio: (LibraryFileEntity) -> Unit, val playingUri: String?)

@Composable
private fun FileRow(f: LibraryFileEntity, a: FileRowActions, showFolder: Boolean) {
    val cat = FileCategory.valueOf(f.category)
    val meta = buildList {
        add(Formatters.bytes(f.sizeBytes))
        if (cat == FileCategory.VIDEO || cat == FileCategory.AUDIO) f.durationMs?.takeIf { it > 0 }?.let { add(Formatters.duration(it / 1000)) }
        if (cat == FileCategory.DOCUMENT) FileExt(f.displayName)?.let { add(it) }
        add(DateUtils.getRelativeTimeSpanString(f.dateAdded, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString())
        if (showFolder && f.folder.isNotEmpty()) add(f.folder)
    }.joinToString(" · ")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { if (cat == FileCategory.AUDIO) a.onToggleAudio(f) else a.onOpen(f) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        FileThumb(f.uri, cat, f.displayName, 56.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(meta, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (cat == FileCategory.AUDIO) IconButton({ a.onToggleAudio(f) }) {
            val playing = a.playingUri == f.uri
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton({ a.onMore(f) }) { Icon(Icons.Rounded.MoreVert, "More") }
    }
}

private fun FileExt(name: String): String? = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() }?.uppercase()

@Composable
private fun FolderRow(path: String, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Text(path.substringAfterLast('/'), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box {
            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Folder options") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Rename") }, { menu = false; onRename() }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                DropdownMenuItem({ Text("Delete") }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Rounded.Delete, null) })
            }
        }
    }
}

@Composable
private fun FolderHeader(path: String, onNavigate: (String) -> Unit, onNew: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ onNavigate("") }, contentPadding = PaddingValues(horizontal = 8.dp)) { Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Stashly") }
            var cur = ""
            path.split('/').filter { it.isNotEmpty() }.forEach { seg ->
                cur = if (cur.isEmpty()) seg else "$cur/$seg"
                val target = cur
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton({ onNavigate(target) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(seg, maxLines = 1) }
            }
        }
        OutlinedButton(onNew, Modifier.testTag("new_folder")) { Icon(Icons.Rounded.CreateNewFolder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("New") }
    }
}

@Composable
private fun EmptyFiles(tab: FilesTab, searching: Boolean) {
    val (icon, title, msg) = when {
        searching -> Triple(Icons.Rounded.Search, "No matches", "Try a different search term.")
        tab == FilesTab.VIDEOS -> Triple(Icons.Rounded.VideoLibrary, "No videos yet", "Videos you download appear here automatically.")
        tab == FilesTab.AUDIO -> Triple(Icons.Rounded.MusicNote, "No audio yet", "Downloaded audio and music appear here.")
        tab == FilesTab.IMAGES -> Triple(Icons.Rounded.Image, "No images yet", "Downloaded images appear here.")
        tab == FilesTab.DOCUMENTS -> Triple(Icons.Rounded.Description, "No documents yet", "PDFs and other documents appear here.")
        tab == FilesTab.FOLDERS -> Triple(Icons.Rounded.Folder, "This folder is empty", "Create a folder or move files into it from the file menu.")
        else -> Triple(Icons.Rounded.Folder, "No downloads yet", "Everything you download is listed here, newest first.")
    }
    EmptyState(icon, title, msg)
}

// ---------------- dialogs ----------------
@Composable
private fun TextInputDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.testTag("dialog_input")) },
        confirmButton = { TextButton({ onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
private fun FolderPickerDialog(title: String, folders: List<String>, current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            LazyColumn(Modifier.height(280.dp)) {
                items(listOf("") + folders) { path ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(path) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp))
                        Text(if (path.isEmpty()) "Stashly (main folder)" else path, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (path == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
