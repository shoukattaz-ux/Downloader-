package com.stashly.app.ui

import com.stashly.app.core.AppError
import com.stashly.app.core.ErrorCode
import com.stashly.app.data.repository.AppSettings
import com.stashly.app.data.repository.DownloadRepository
import com.stashly.app.data.repository.SettingsRepository
import com.stashly.app.domain.model.MediaFormat
import com.stashly.app.domain.model.MediaInfo
import com.stashly.app.domain.model.MediaKind
import com.stashly.app.domain.provider.MediaSourceProvider
import com.stashly.app.domain.provider.ProviderRegistry
import com.stashly.app.ui.home.HomeViewModel
import com.stashly.app.ui.home.Phase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.HttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val fmts = listOf(
        MediaFormat("a", MediaKind.VIDEO, "MP4", "mp4", "video/mp4", "720p", height = 720, sizeBytes = 10, url = "https://h/a.mp4"),
        MediaFormat("b", MediaKind.VIDEO, "MP4", "mp4", "video/mp4", "1080p", height = 1080, sizeBytes = 20, url = "https://h/b.mp4"),
        MediaFormat("c", MediaKind.AUDIO, "MP3", "mp3", "audio/mpeg", "128 kbps", bitrateKbps = 128, url = "https://h/c.mp3"),
    )
    private val info = MediaInfo("https://h/page", "fake", "Fake", "Title", null, 60, fmts)
    private val provider = object : MediaSourceProvider {
        override val id = "fake"; override val displayName = "Fake"
        override fun supports(url: HttpUrl) = true
        override suspend fun resolve(url: HttpUrl): MediaInfo = if (url.encodedPath.contains("bad")) throw AppError(ErrorCode.NO_MEDIA, "nothing") else info
    }
    private val downloads = mockk<DownloadRepository>(relaxed = true)
    private val settings = mockk<SettingsRepository>().also {
        every { it.settings } returns flowOf(AppSettings(defaultKind = MediaKind.VIDEO, defaultQuality = "720"))
        coEvery { it.current() } returns AppSettings(defaultKind = MediaKind.VIDEO, defaultQuality = "720")
    }

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm() = HomeViewModel(ProviderRegistry(listOf(provider)), downloads, settings)

    @Test fun `analyze preselects the default quality from real formats`() = runTest {
        val vm = vm(); vm.onInputChange("https://h/page"); vm.analyze()
        assertTrue(vm.state.value.phase is Phase.Result)
        assertEquals("a", vm.state.value.selectedFormatId)
    }
    @Test fun `failures surface as an error phase and no download is possible`() = runTest {
        val vm = vm(); vm.onInputChange("https://h/bad"); vm.analyze()
        val p = vm.state.value.phase as Phase.Error
        assertEquals(ErrorCode.NO_MEDIA, p.error.code); assertNull(vm.state.value.selectedFormat)
    }
    @Test fun `restricted platform yields a compliant alternative and never enqueues`() = runTest {
        val vm = vm(); vm.onInputChange("https://www.youtube.com/watch?v=1"); vm.analyze()
        assertTrue(vm.state.value.phase is Phase.Restricted)
        vm.download()
        coVerify(exactly = 0) { downloads.enqueue(any(), any()) }
    }
    @Test fun `selecting audio switches to the best audio format`() = runTest {
        val vm = vm(); vm.onInputChange("https://h/page"); vm.analyze(); vm.selectKind(MediaKind.AUDIO)
        assertEquals("c", vm.state.value.selectedFormatId)
    }
    @Test fun `download enqueues the selected format and tracks it`() = runTest {
        coEvery { downloads.enqueue(any(), any()) } returns 42L
        every { downloads.observe(42L) } returns flowOf(null)
        val vm = vm(); vm.onInputChange("https://h/page"); vm.analyze(); vm.selectFormat("b"); vm.download()
        coVerify { downloads.enqueue(info, fmts[1]) }
        assertEquals(42L, vm.state.value.activeDownloadId)
    }
    @Test fun `duplicate download shows the message and does not track`() = runTest {
        coEvery { downloads.enqueue(any(), any()) } throws AppError(ErrorCode.DUPLICATE, "Already downloaded")
        val vm = vm(); vm.onInputChange("https://h/page"); vm.analyze(); vm.download()
        assertNull(vm.state.value.activeDownloadId)
    }
    @Test fun `editing the link discards the stale result`() = runTest {
        val vm = vm(); vm.onInputChange("https://h/page"); vm.analyze(); vm.onInputChange("https://h/other")
        assertTrue(vm.state.value.phase is Phase.Idle)
    }
    @Test fun `clipboard candidate only accepts valid links`() = runTest {
        val vm = vm(); vm.onClipboardText("just words"); assertNull(vm.state.value.clipboardCandidate)
        vm.onClipboardText("https://h/page"); assertEquals("https://h/page", vm.state.value.clipboardCandidate)
        vm.useClipboardCandidate(); assertEquals("https://h/page", vm.state.value.input); assertNull(vm.state.value.clipboardCandidate)
    }
}
