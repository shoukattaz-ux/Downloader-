package com.stashly.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stashly.app.data.repository.AppSettings
import com.stashly.app.data.repository.DownloadRepository
import com.stashly.app.data.repository.LibraryRepository
import com.stashly.app.data.repository.SettingsRepository
import com.stashly.app.data.repository.ThemeMode
import com.stashly.app.domain.model.MediaKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.stashly.app.core.Formatters

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val downloads: DownloadRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val folders = library.observeAllFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val appBytes = library.observeAppBytes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    fun setTheme(v: ThemeMode) = launch { repo.setTheme(v) }
    fun setKind(v: MediaKind) = launch { repo.setDefaultKind(v) }
    fun setQuality(v: String) = launch { repo.setDefaultQuality(v) }
    fun setFolder(v: String) = launch { repo.setDefaultFolder(v) }
    fun setWifiOnly(v: Boolean) = launch { repo.setWifiOnly(v) }
    fun setNotifications(v: Boolean) = launch { repo.setNotifications(v) }
    fun setClipboard(v: Boolean) = launch { repo.setClipboardDetection(v) }
    fun clearHistory() = launch { downloads.clearHistory(); messages.send("Download history cleared") }
    fun discardPartials() = launch { val n = downloads.discardPartials(); messages.send("Freed ${Formatters.bytes(n)}") }
    fun rescan() = launch { library.sync(); messages.send("Library refreshed") }

    private fun launch(block: suspend () -> Unit) { viewModelScope.launch { runCatching { block() } } }
}
