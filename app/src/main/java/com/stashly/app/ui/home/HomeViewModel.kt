package com.stashly.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stashly.app.core.AppError
import com.stashly.app.core.ErrorCode
import com.stashly.app.core.UrlRules
import com.stashly.app.data.db.DownloadEntity
import com.stashly.app.data.repository.AppSettings
import com.stashly.app.data.repository.DownloadRepository
import com.stashly.app.data.repository.SettingsRepository
import com.stashly.app.domain.model.AnalyzeOutcome
import com.stashly.app.domain.model.MediaFormat
import com.stashly.app.domain.model.MediaInfo
import com.stashly.app.domain.model.MediaKind
import com.stashly.app.domain.provider.FormatSelector
import com.stashly.app.domain.provider.LinkHint
import com.stashly.app.domain.provider.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface Phase {
    data object Idle : Phase
    data object Loading : Phase
    data class Result(val info: MediaInfo) : Phase
    data class Restricted(val platform: String, val url: String) : Phase
    data class Error(val error: AppError) : Phase
}

data class HomeUiState(
    val input: String = "",
    val hint: LinkHint = LinkHint.Empty,
    val phase: Phase = Phase.Idle,
    val kind: MediaKind = MediaKind.VIDEO,
    val selectedFormatId: String? = null,
    val activeDownloadId: Long? = null,
    val clipboardCandidate: String? = null,
) {
    val info: MediaInfo? get() = (phase as? Phase.Result)?.info
    val selectedFormat: MediaFormat? get() = info?.formats?.firstOrNull { it.id == selectedFormatId }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val registry: ProviderRegistry,
    private val downloads: DownloadRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    val activeDownload: StateFlow<DownloadEntity?> = _state.flatMapLatest { s ->
        s.activeDownloadId?.let { downloads.observe(it) } ?: flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var analyzeJob: Job? = null

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text, hint = registry.hint(text)) }
        // Editing the link invalidates any previous result.
        if (_state.value.phase !is Phase.Idle && _state.value.phase !is Phase.Loading)
            _state.update { it.copy(phase = Phase.Idle, selectedFormatId = null, activeDownloadId = null) }
    }

    fun clear() {
        analyzeJob?.cancel()
        _state.value = HomeUiState(clipboardCandidate = _state.value.clipboardCandidate)
    }

    fun onClipboardText(text: String?) {
        val candidate = text?.trim()?.takeIf { runCatching { UrlRules.parse(it) }.isSuccess }
        _state.update { it.copy(clipboardCandidate = candidate?.takeIf { c -> c != it.input.trim() }) }
    }

    fun useClipboardCandidate() {
        _state.value.clipboardCandidate?.let { onInputChange(it) }
        _state.update { it.copy(clipboardCandidate = null) }
    }

    fun dismissClipboardCandidate() = _state.update { it.copy(clipboardCandidate = null) }
    fun setClipboardConsent(enabled: Boolean) { viewModelScope.launch { settingsRepo.setClipboardDetection(enabled) } }

    fun analyze() {
        val raw = _state.value.input
        analyzeJob?.cancel()
        _state.update { it.copy(phase = Phase.Loading, selectedFormatId = null, activeDownloadId = null) }
        analyzeJob = viewModelScope.launch {
            try {
                when (val outcome = registry.analyze(raw)) {
                    is AnalyzeOutcome.Restricted -> _state.update { it.copy(phase = Phase.Restricted(outcome.platformName, outcome.url)) }
                    is AnalyzeOutcome.Success -> {
                        val s = settingsRepo.current()
                        val pick = FormatSelector.pick(outcome.info.formats, s.defaultKind, s.defaultQuality)
                        _state.update { it.copy(phase = Phase.Result(outcome.info), kind = pick?.kind ?: s.defaultKind, selectedFormatId = pick?.id) }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.update { it.copy(phase = Phase.Error(AppError.from(t))) }
            }
        }
    }

    fun selectKind(kind: MediaKind) {
        val info = _state.value.info ?: return
        val s = settings.value
        val pick = FormatSelector.pick(info.formats.filter { it.kind == kind }, kind, s.defaultQuality)
        _state.update { it.copy(kind = kind, selectedFormatId = pick?.id) }
    }

    fun selectFormat(id: String) = _state.update { it.copy(selectedFormatId = id, activeDownloadId = null) }

    fun download() {
        val st = _state.value
        val info = st.info ?: return
        val format = st.selectedFormat ?: return
        viewModelScope.launch {
            try {
                val id = downloads.enqueue(info, format)
                _state.update { it.copy(activeDownloadId = id) }
                messages.send("Download started")
            } catch (e: AppError) {
                messages.send(e.userMessage)
            } catch (t: Throwable) {
                messages.send(AppError.from(t).userMessage)
            }
        }
    }

    fun pause(e: DownloadEntity) { viewModelScope.launch { downloads.pause(e.id) } }
    fun resume(e: DownloadEntity) { viewModelScope.launch { downloads.resume(e.id) } }
    fun cancel(e: DownloadEntity) { viewModelScope.launch { downloads.cancel(e.id) } }
    fun retry(e: DownloadEntity) { viewModelScope.launch { downloads.retry(e.id) } }
}
