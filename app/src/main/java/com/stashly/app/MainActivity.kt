package com.stashly.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.stashly.app.data.repository.SettingsRepository
import com.stashly.app.data.repository.ThemeMode
import com.stashly.app.ui.nav.AppRoot
import com.stashly.app.ui.player.AudioController
import com.stashly.app.ui.theme.StashlyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_DOWNLOAD_ID = "open_download_id" }

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var audio: AudioController

    private var openDownload by mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openDownload = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: -1L
        setContent {
            val s = settings.settings.collectAsState(initial = com.stashly.app.data.repository.AppSettings()).value
            val dark = when (s.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            StashlyTheme(darkTheme = dark) {
                AppRoot(openDownloadId = openDownload, onOpenDownloadHandled = { openDownload = -1L })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openDownload = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
    }

    override fun onStop() {
        super.onStop()
        audio.pause() // no background playback service by design (battery + simplicity)
    }
}
