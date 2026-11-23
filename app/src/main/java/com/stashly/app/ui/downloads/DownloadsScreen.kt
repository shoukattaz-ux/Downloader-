package com.stashly.app.ui.downloads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stashly.app.data.db.LibraryFileEntity
import com.stashly.app.domain.model.MediaKind
import com.stashly.app.ui.components.DownloadActions
import com.stashly.app.ui.components.DownloadRow
import com.stashly.app.ui.components.EmptyState
import com.stashly.app.ui.components.FileIntents
import com.stashly.app.ui.components.SkeletonRow
import com.stashly.app.ui.components.StashTopBar
import kotlinx.coroutines.launch

@Composable
fun DownloadsScreen(
    onSettings: () -> Unit,
    onOpenFile: (LibraryFileEntity) -> Unit,
    vm: DownloadsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    val actions = DownloadActions(
        onPause = vm::pause, onResume = vm::resume, onCancel = vm::cancel, onRetry = vm::retry, onRemove = vm::remove,
        onOpen = { e -> scope.launch { vm.libraryFile(e)?.let(onOpenFile) ?: vm.message("That file was moved or deleted.") } },
        onShare = { e -> scope.launch {
            val f = vm.libraryFile(e)
            if (f != null) FileIntents.share(context, f.uri, f.mimeType, f.displayName) else vm.message("That file was moved or deleted.")
        } },
    )

    Scaffold(
        topBar = {
            Column {
                StashTopBar("Downloads", onSettings = onSettings, onSearch = { vm.setSearching(!state.searching) },
                    extraActions = { IconButton({ confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, "Clear history") } })
                if (state.searching) OutlinedTextField(
                    value = state.query, onValueChange = vm::setQuery, singleLine = true,
                    placeholder = { Text("Search downloads") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { IconButton({ vm.setSearching(false) }) { Icon(Icons.Rounded.Close, "Close search") } },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
                PrimaryScrollableTabRow(selectedTabIndex = state.tab.ordinal, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background) {
                    DownloadTab.entries.forEach { t ->
                        Tab(selected = state.tab == t, onClick = { vm.setTab(t) },
                            text = { Text(if (t == DownloadTab.ALL) t.label else "${t.label} (${state.counts[t] ?: 0})") })
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                state.loading -> Column(Modifier.padding(16.dp)) { repeat(5) { SkeletonRow() } }
                state.items.isEmpty() -> EmptyState(
                    Icons.Rounded.Download,
                    if (state.query.isNotBlank()) "No matches" else "Nothing here yet",
                    when {
                        state.query.isNotBlank() -> "Try a different search."
                        state.tab == DownloadTab.ALL -> "Downloads you start from Home will show up here with live progress."
                        else -> "No ${state.tab.label.lowercase()} downloads."
                    },
                )
                else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    items(state.items, key = { it.id }) { item ->
                        DownloadRow(item, fileMissing = item.id in state.missingFiles, actions = actions)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear download history?") },
        text = { Text("This removes completed, failed and canceled entries from this list. Your downloaded files stay in Files.") },
        confirmButton = { TextButton({ confirmClear = false; vm.clearHistory() }) { Text("Clear") } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } },
    )
}
