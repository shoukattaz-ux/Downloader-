package com.stashly.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stashly.app.BuildConfig
import com.stashly.app.core.Formatters
import com.stashly.app.data.repository.ThemeMode
import com.stashly.app.domain.model.MediaKind
import com.stashly.app.ui.components.FileIntents
import com.stashly.app.ui.components.SectionTitle
import com.stashly.app.ui.components.StashCard

private val qualityOptions = listOf("BEST" to "Highest available", "1080" to "Up to 1080p", "720" to "Up to 720p", "480" to "Up to 480p", "360" to "Up to 360p", "LOWEST" to "Lowest (saves data)")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onPrivacy: () -> Unit, onTerms: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsState()
    val folders by vm.folders.collectAsState()
    val appBytes by vm.appBytes.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmPartials by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) },
        snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Group("Appearance") {
                Text("Theme", style = MaterialTheme.typography.bodyLarge)
                SegmentedRow(ThemeMode.entries.map { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, s.theme, vm::setTheme)
            }
            Group("Downloads") {
                Text("Default format", style = MaterialTheme.typography.bodyLarge)
                SegmentedRow(listOf(MediaKind.VIDEO to "Video (MP4…)", MediaKind.AUDIO to "Audio (MP3/M4A…)"), s.defaultKind, vm::setKind)
                DropdownSetting("Default quality", qualityOptions.firstOrNull { it.first == s.defaultQuality }?.second ?: "Highest available",
                    qualityOptions.map { it.second }) { idx -> vm.setQuality(qualityOptions[idx].first) }
                val folderLabels = listOf("Stashly (main folder)") + folders
                DropdownSetting("Download folder", if (s.defaultFolder.isEmpty()) "Stashly (main folder)" else s.defaultFolder, folderLabels) { idx ->
                    vm.setFolder(if (idx == 0) "" else folders[idx - 1])
                }
                Text("Files are saved in Downloads/Stashly. Create more folders in Files.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SwitchRow("Wi-Fi only", "Wait for an unmetered connection before downloading", s.wifiOnly, vm::setWifiOnly)
            }
            Group("Notifications & privacy") {
                SwitchRow("Download notifications", "Completed and failed downloads", s.notifications, vm::setNotifications)
                TextButton({ context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("System notification settings") }
                SwitchRow("Suggest links from clipboard", "Checks the clipboard when the app opens. Never uploaded.", s.clipboardDetection == true, vm::setClipboard)
            }
            Group("Storage") {
                Text("Stashly files: ${Formatters.bytes(appBytes)}", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(vm::rescan) { Text("Refresh library") }
                    TextButton({ confirmPartials = true }) { Text("Discard unfinished") }
                }
                TextButton({ confirmClear = true }) { Text("Clear download history", color = MaterialTheme.colorScheme.error) }
            }
            Group("About") {
                LinkRow("Privacy Policy", onPrivacy)
                LinkRow("Terms of Service", onTerms)
                LinkRow("Contact support") { FileIntents.email(context, BuildConfig.SUPPORT_EMAIL, "Stashly support (v${BuildConfig.VERSION_NAME})") }
                LinkRow("About Stashly") { showAbout = true }
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (confirmClear) AlertDialog({ confirmClear = false }, title = { Text("Clear download history?") },
        text = { Text("Removes finished entries from Downloads. Files stay in Files.") },
        confirmButton = { TextButton({ confirmClear = false; vm.clearHistory() }) { Text("Clear") } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } })
    if (confirmPartials) AlertDialog({ confirmPartials = false }, title = { Text("Discard unfinished downloads?") },
        text = { Text("Deletes partly downloaded data for paused, failed and canceled downloads. You can retry them later; they'll restart from zero.") },
        confirmButton = { TextButton({ confirmPartials = false; vm.discardPartials() }) { Text("Discard") } },
        dismissButton = { TextButton({ confirmPartials = false }) { Text("Cancel") } })
    if (showAbout) AlertDialog({ showAbout = false }, title = { Text("Stashly ${BuildConfig.VERSION_NAME}") },
        text = { Text("A media downloader and file manager for content you own or are authorised to save. Stashly never bypasses DRM, sign-in or platform download restrictions.") },
        confirmButton = { TextButton({ showAbout = false }) { Text("OK") } })
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(title)
        StashCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } }
    }
}

@Composable
private fun <T> SegmentedRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(selected == value, { onSelect(value) }, SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun DropdownSetting(title: String, current: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(current, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        DropdownMenu(open, { open = false }) { options.forEachIndexed { i, o -> DropdownMenuItem({ Text(o) }, { open = false; onPick(i) }) } }
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) { Text(label, style = MaterialTheme.typography.bodyLarge) }
}
