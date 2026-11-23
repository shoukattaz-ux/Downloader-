package com.stashly.app.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stashly.app.core.AppError
import com.stashly.app.data.db.LibraryFileEntity
import com.stashly.app.data.repository.LibraryRepository
import com.stashly.app.data.repository.StorageInfo
import com.stashly.app.domain.model.FileCategory
import com.stashly.app.domain.model.SortOrder
import com.stashly.app.ui.player.AudioController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FilesTab(val label: String, val category: FileCategory?) {
    VIDEOS("Videos", FileCategory.VIDEO), AUDIO("Audio", FileCategory.AUDIO), IMAGES("Images", FileCategory.IMAGE),
    DOCUMENTS("Documents", FileCategory.DOCUMENT), DOWNLOADS("Downloads", null), FOLDERS("Folders", null)
}

data class FilesUiState(
    val loading: Boolean = true,
    val tab: FilesTab = FilesTab.VIDEOS,
    val sort: SortOrder = SortOrder.DATE,
    val query: String = "",
    val searching: Boolean = false,
    val folder: String = "",
    val subfolders: List<String> = emptyList(),
    val allFolders: List<String> = emptyList(),
    val files: List<LibraryFileEntity> = emptyList(),
    val storage: StorageInfo? = null,
    val refreshing: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class FilesViewModel @Inject constructor(
    private val library: LibraryRepository,
    val audio: AudioController,
) : ViewModel() {
    private val tab = MutableStateFlow(FilesTab.VIDEOS)
    private val sort = MutableStateFlow(SortOrder.DATE)
    private val query = MutableStateFlow("")
    private val searching = MutableStateFlow(false)
    private val folder = MutableStateFlow("")
    private val refreshing = MutableStateFlow(false)
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    private data class Params(val tab: FilesTab, val sort: SortOrder, val query: String, val folder: String)

    private val files = combine(tab, sort, query.debounce(150), folder) { t, s, q, f -> Params(t, s, q, f) }
        .flatMapLatest { p ->
            when (p.tab) {
                FilesTab.FOLDERS -> library.observeFolder(p.folder, p.query, p.sort)
                else -> library.observe(p.tab.category, p.query, p.sort)
            }
        }

    private val meta = combine(files, folder.flatMapLatest { library.observeSubfolders(it) }, library.observeAllFolders(), library.observeAppBytes()) { f, sub, all, bytes ->
        listOf(f, sub, all, bytes)
    }

    @Suppress("UNCHECKED_CAST")
    val state: StateFlow<FilesUiState> = combine(meta, tab, sort, query, combine(searching, folder, refreshing) { a, b, c -> Triple(a, b, c) }) { m, t, s, q, x ->
        FilesUiState(
            loading = false, tab = t, sort = s, query = q, searching = x.first, folder = x.second, refreshing = x.third,
            files = m[0] as List<LibraryFileEntity>, subfolders = m[1] as List<String>, allFolders = m[2] as List<String>,
            storage = runCatching { library.storage(m[3] as Long) }.getOrNull(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilesUiState())

    init { refresh() }

    fun refresh() { viewModelScope.launch { refreshing.value = true; runCatching { library.sync() }; refreshing.value = false } }
    fun setTab(t: FilesTab) { tab.value = t; if (t != FilesTab.FOLDERS) folder.value = "" }
    fun setSort(s: SortOrder) { sort.value = s }
    fun setQuery(q: String) { query.value = q }
    fun setSearching(on: Boolean) { searching.value = on; if (!on) query.value = "" }
    fun openFolder(path: String) { folder.value = path }
    fun up() { folder.value = folder.value.substringBeforeLast('/', "") }

    fun rename(f: LibraryFileEntity, name: String) = op("Renamed") { library.rename(f, name) }
    fun move(f: LibraryFileEntity, dest: String) = op("Moved") { library.move(f, dest) }
    fun copy(f: LibraryFileEntity, dest: String) = op("Copied") { library.copy(f, dest) }
    fun delete(f: LibraryFileEntity) = op("Deleted") { audio.stopIf(f.uri); library.delete(f) }
    fun createFolder(name: String) = op("Folder created") { library.createFolder(folder.value, name) }
    fun renameFolder(path: String, name: String) = op("Folder renamed") { library.renameFolder(path, name) }
    fun deleteFolder(path: String) = op("Folder deleted") { library.deleteFolder(path) }
    fun message(text: String) { viewModelScope.launch { messages.send(text) } }

    private fun op(success: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block(); messages.send(success) } catch (e: AppError) { messages.send(e.userMessage) }
            catch (t: Throwable) { messages.send(AppError.from(t).userMessage) }
        }
    }
}
