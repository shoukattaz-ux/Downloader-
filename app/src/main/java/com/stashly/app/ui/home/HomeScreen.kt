package com.stashly.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.stashly.app.core.Formatters
import com.stashly.app.data.db.DownloadEntity
import com.stashly.app.domain.model.DownloadStatus
import com.stashly.app.domain.model.MediaFormat
import com.stashly.app.domain.model.MediaInfo
import com.stashly.app.domain.model.MediaKind
import com.stashly.app.domain.provider.LinkHint
import com.stashly.app.ui.components.DownloadActions
import com.stashly.app.ui.components.DownloadRow
import com.stashly.app.ui.components.ErrorState
import com.stashly.app.ui.components.FileIntents
import com.stashly.app.ui.components.SectionTitle
import com.stashly.app.ui.components.SkeletonBox
import com.stashly.app.ui.components.StashCard
import com.stashly.app.ui.components.StashTopBar
import com.stashly.app.ui.theme.Brand

@Composable
fun HomeScreen(
    onSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenDownload: (DownloadEntity) -> Unit,
    onShareDownload: (DownloadEntity) -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val settings by vm.settings.collectAsState()
    val active by vm.activeDownload.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    // Clipboard suggestion only when the user opted in (consent stored in settings).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (settings.clipboardDetection == true) vm.onClipboardText(clipboard.getText()?.text)
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* download proceeds either way */ }
    fun startDownload() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        vm.download()
    }

    Scaffold(
        topBar = { StashTopBar("Downloader", onSettings = onSettings) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (settings.clipboardDetection == null) item { ClipboardConsentCard(vm::setClipboardConsent) }
            state.clipboardCandidate?.let { c -> item { ClipboardSuggestion(c, vm::useClipboardCandidate, vm::dismissClipboardCandidate) } }

            item {
                UrlInput(
                    value = state.input, hint = state.hint,
                    onChange = vm::onInputChange, onClear = vm::clear,
                    onPaste = { vm.onInputChange(clipboard.getText()?.text.orEmpty()) },
                    onAnalyze = vm::analyze,
                    loading = state.phase is Phase.Loading,
                )
            }

            when (val phase = state.phase) {
                is Phase.Idle -> item { SupportedSourcesCard() }
                is Phase.Loading -> item { LoadingPreview() }
                is Phase.Error -> item { StashCard(Modifier.fillMaxWidth()) { ErrorState(phase.error.userMessage, onRetry = vm::analyze) } }
                is Phase.Restricted -> item { RestrictedCard(phase.platform, phase.url) }
                is Phase.Result -> {
                    item { PreviewCard(phase.info, state.selectedFormat) }
                    item { KindAndFormats(phase.info, state, vm::selectKind, vm::selectFormat) }
                    item {
                        DownloadSection(state.selectedFormat, active, vm, startDownload = ::startDownload,
                            onOpenDownloads = onOpenDownloads, onOpen = onOpenDownload, onShare = onShareDownload)
                    }
                    item {
                        Text("Only download content you own or have permission to save. Respect the licence shown by the source.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun UrlInput(value: String, hint: LinkHint, onChange: (String) -> Unit, onClear: () -> Unit,
                     onPaste: () -> Unit, onAnalyze: () -> Unit, loading: Boolean) {
    StashCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Paste a media link", style = MaterialTheme.typography.titleMedium)
            val invalid = hint is LinkHint.Invalid
            OutlinedTextField(
                value = value, onValueChange = onChange,
                modifier = Modifier.fillMaxWidth().testTag("url_input"),
                placeholder = { Text("Paste your video or media link here...") },
                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                trailingIcon = {
                    if (value.isNotEmpty()) IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, "Clear") }
                    else IconButton(onClick = onPaste) { Icon(Icons.Rounded.ContentPaste, "Paste URL") }
                },
                singleLine = true, isError = invalid && value.isNotBlank(),
                supportingText = { HintText(hint) },
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (hint is LinkHint.Supported || hint is LinkHint.Restricted) onAnalyze() }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onPaste, modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Paste")
                }
                Button(
                    onClick = onAnalyze, enabled = !loading && (hint is LinkHint.Supported || hint is LinkHint.Restricted),
                    modifier = Modifier.weight(1.4f).height(48.dp).testTag("analyze_button"),
                ) { Text(if (loading) "Analyzing…" else "Analyze") }
            }
        }
    }
}

@Composable
private fun HintText(hint: LinkHint) {
    when (hint) {
        LinkHint.Empty -> {}
        is LinkHint.Invalid -> Text(hint.message)
        is LinkHint.Restricted -> Text("${hint.platform} doesn't allow third-party downloads", color = Brand.Amber)
        is LinkHint.Supported -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp)); Text("Source: ${hint.sourceName}")
        }
    }
}

@Composable
private fun SupportedSourcesCard() {
    StashCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("What you can download", style = MaterialTheme.typography.titleMedium)
            Bullet("Direct links to media files (MP4, WebM, MP3, M4A, images, PDFs) that you own or are allowed to save")
            Bullet("Internet Archive items (archive.org/details/…), with the real formats each item offers")
            Spacer(Modifier.height(2.dp))
            Text("Streaming and social platforms such as YouTube, Instagram, TikTok or Facebook don't permit third-party downloads, so Stashly won't download from them. Paste one and we'll help you open it officially instead.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun Bullet(text: String) = Row {
    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
    Spacer(Modifier.size(10.dp)); Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun LoadingPreview() {
    StashCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBox(Modifier.fillMaxWidth().aspectRatio(16 / 9f))
            SkeletonBox(Modifier.fillMaxWidth(0.8f).height(18.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.4f).height(14.dp))
            SkeletonBox(Modifier.fillMaxWidth().height(48.dp))
        }
    }
}

@Composable
private fun RestrictedCard(platform: String, url: String) {
    val context = LocalContext.current
    StashCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WarningAmber, null, tint = Brand.Amber)
                Spacer(Modifier.size(10.dp))
                Text("$platform can't be downloaded here", style = MaterialTheme.typography.titleMedium)
            }
            Text("$platform's terms don't permit downloading its content with third-party apps, and Stashly doesn't bypass those rules. You can watch or share this link through the official app or website.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button({ FileIntents.openWeb(context, url) }) { Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Open in $platform") }
                OutlinedButton({
                    context.startActivity(android.content.Intent.createChooser(
                        android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, url), "Share link")
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Share link") }
            }
        }
    }
}

@Composable
private fun PreviewCard(info: MediaInfo, selected: MediaFormat?) {
    StashCard(Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (info.thumbnailUrl != null)
                    AsyncImage(info.thumbnailUrl, info.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                info.durationSeconds?.takeIf { it > 0 }?.let {
                    Text(Formatters.duration(it), color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(0.6f)).padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(info.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("preview_title"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = {}, label = { Text(info.sourceName) })
                    selected?.sizeBytes?.let { Text("≈ ${Formatters.bytes(it)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun KindAndFormats(info: MediaInfo, state: HomeUiState, onKind: (MediaKind) -> Unit, onFormat: (String) -> Unit) {
    val kinds = info.formats.map { it.kind }.distinct()
    StashCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (kinds.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    kinds.forEach { k -> FilterChip(selected = state.kind == k, onClick = { onKind(k) },
                        label = { Text(k.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
                }
            }
            SectionTitle("Available formats")
            val options = info.formats.filter { it.kind == state.kind }.ifEmpty { info.formats }
            options.forEach { f ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onFormat(f.id) }
                        .background(if (state.selectedFormatId == f.id) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.selectedFormatId == f.id, onClick = { onFormat(f.id) })
                    Column(Modifier.weight(1f)) {
                        Text(f.label, style = MaterialTheme.typography.titleSmall)
                        if (f.qualityLabel != "Original" && f.container.length <= 5 && f.id != "original")
                            Text(f.id.substringAfterLast('/').take(40), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(Formatters.bytes(f.sizeBytes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun DownloadSection(
    format: MediaFormat?, active: DownloadEntity?, vm: HomeViewModel, startDownload: () -> Unit,
    onOpenDownloads: () -> Unit, onOpen: (DownloadEntity) -> Unit, onShare: (DownloadEntity) -> Unit,
) {
    if (active == null) {
        Button(
            onClick = startDownload, enabled = format != null,
            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("download_button"),
            shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(),
        ) {
            Icon(Icons.Rounded.Download, null); Spacer(Modifier.size(10.dp))
            Text(if (format != null) "Download ${format.label}" else "Select a format", style = MaterialTheme.typography.titleMedium)
        }
    } else {
        StashCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                DownloadRow(
                    active, fileMissing = false,
                    actions = DownloadActions(vm::pause, vm::resume, vm::cancel, vm::retry, { vm.cancel(it) }, onOpen, onShare),
                )
                if (active.status != DownloadStatus.COMPLETED) TextButton(onOpenDownloads, Modifier.align(Alignment.End)) { Text("View all downloads") }
            }
        }
    }
}

@Composable
private fun ClipboardConsentCard(onChoice: (Boolean) -> Unit) {
    StashCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.size(10.dp))
                Text("Suggest links from clipboard?", style = MaterialTheme.typography.titleMedium)
            }
            Text("Stashly can check your clipboard for a link when you open the app, and offer to use it. The clipboard is only read on this device and never uploaded. You can change this in Settings.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ onChoice(true) }) { Text("Allow") }
                TextButton({ onChoice(false) }) { Text("No thanks") }
            }
        }
    }
}

@Composable
private fun ClipboardSuggestion(url: String, onUse: () -> Unit, onDismiss: () -> Unit) {
    StashCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ContentPaste, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Link found in clipboard", style = MaterialTheme.typography.labelLarge)
                Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onUse) { Text("Use") }
            IconButton(onDismiss) { Icon(Icons.Rounded.Close, "Dismiss") }
        }
    }
}
