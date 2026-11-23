package com.stashly.app.ui.downloads

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stashly.app.data.db.DownloadEntity
import com.stashly.app.data.db.LibraryFileEntity
import com.stashly.app.data.repository.DownloadRepository
import com.stashly.app.data.repository.LibraryRepository
import com.stashly.app.domain.model.DownloadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DownloadTab(val label: String) { ALL("All"), DOWNLOADING("Downloading"), COMPLETED("Completed"), FAILED("Failed"), PAUSED("Paused") }

data class DownloadsUiState(
    val loading: Boolean = true,
    val tab: DownloadTab = DownloadTab.ALL,
    val query: String = "",
    val searching: Boolean = false,
    val items: List<DownloadEntity> = emptyList(),
    val counts: Map<DownloadTab, Int> = emptyMap(),
    val missingFiles: Set<Long> = emptySet(),
)

private fun DownloadTab.matches(s: DownloadStatus) = when (this) {
    DownloadTab.ALL -> true
    DownloadTab.DOWNLOADING -> s == DownloadStatus.QUEUED || s == DownloadStatus.DOWNLOADING
    DownloadTab.COMPLETED -> s == DownloadStatus.COMPLETED
    DownloadTab.FAILED -> s == DownloadStatus.FAILED || s == DownloadStatus.CANCELED
    DownloadTab.PAUSED -> s == DownloadStatus.PAUSED
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repo: DownloadRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    private val tab = MutableStateFlow(DownloadTab.ALL)
    private val query = MutableStateFlow("")
    private val searching = MutableStateFlow(false)
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    val state: StateFlow<DownloadsUiState> = combine(repo.observeAll(), library.observeUris(), tab, query, searching) { all, uris, t, q, s ->
        val missing = all.filter { it.status == DownloadStatus.COMPLETED && it.fileUri != null && it.fileUri !in uris }.map { it.id }.toSet()
        DownloadsUiState(
            loading = false, tab = t, query = q, searching = s,
            items = all.filter { t.matches(it.status) && (q.isBlank() || it.fileName.contains(q, true) || it.title.contains(q, true)) },
            counts = DownloadTab.entries.associateWith { tb -> all.count { tb.matches(it.status) } },
            missingFiles = missing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun setTab(t: DownloadTab) { tab.value = t }
    fun setQuery(q: String) { query.value = q }
    fun setSearching(on: Boolean) { searching.value = on; if (!on) query.value = "" }

    fun pause(e: DownloadEntity) = launch { repo.pause(e.id) }
    fun resume(e: DownloadEntity) = launch { repo.resume(e.id) }
    fun cancel(e: DownloadEntity) = launch { repo.cancel(e.id) }
    fun retry(e: DownloadEntity) = launch { repo.retry(e.id) }
    fun remove(e: DownloadEntity) = launch { repo.remove(e.id) }
    fun clearHistory() = launch { repo.clearHistory(); messages.send("History cleared") }

    suspend fun libraryFile(e: DownloadEntity): LibraryFileEntity? = e.fileUri?.let { library.get(it) }
    fun message(text: String) = launch { messages.send(text) }

    private fun launch(block: suspend () -> Unit) { viewModelScope.launch { runCatching { block() } } }
}
