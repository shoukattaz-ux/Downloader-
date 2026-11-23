package com.stashly.app.ui.nav

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stashly.app.R
import com.stashly.app.data.db.LibraryFileEntity
import com.stashly.app.domain.model.FileCategory
import com.stashly.app.ui.components.FileIntents
import com.stashly.app.ui.downloads.DownloadsScreen
import com.stashly.app.ui.files.FilesScreen
import com.stashly.app.ui.home.HomeScreen
import com.stashly.app.ui.player.AudioController
import com.stashly.app.ui.player.ImageViewerScreen
import com.stashly.app.ui.player.PlayerScreen
import com.stashly.app.ui.settings.LegalScreen
import com.stashly.app.ui.settings.SettingsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

object Routes {
    const val HOME = "home"
    const val FILES = "files"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val TERMS = "terms"
    const val PLAYER = "player/{uri}"
    const val VIEWER = "viewer/{uri}"
    fun player(uri: String) = "player/${Uri.encode(uri)}"
    fun viewer(uri: String) = "viewer/${Uri.encode(uri)}"
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Routes.HOME, "Home", Icons.Rounded.Home),
    TabItem(Routes.FILES, "Files", Icons.Rounded.Folder),
    TabItem(Routes.DOWNLOADS, "Downloads", Icons.Rounded.Download),
)

@HiltViewModel
class RootViewModel @Inject constructor(val audio: AudioController, val library: com.stashly.app.data.repository.LibraryRepository) : ViewModel()

@Composable
fun AppRoot(openDownloadId: Long, onOpenDownloadHandled: () -> Unit, rootVm: RootViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val context = LocalContext.current

    fun navigateTab(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true; restoreState = true
    }

    // Single place that decides how a library file is opened.
    val openFile: (LibraryFileEntity) -> Unit = { f ->
        rootVm.audio.pause()
        when (FileCategory.valueOf(f.category)) {
            FileCategory.VIDEO, FileCategory.AUDIO -> nav.navigate(Routes.player(f.uri))
            FileCategory.IMAGE -> nav.navigate(Routes.viewer(f.uri))
            FileCategory.DOCUMENT -> FileIntents.view(context, f.uri, f.mimeType)
        }
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(openDownloadId) {
        if (openDownloadId >= 0) { navigateTab(Routes.DOWNLOADS); onOpenDownloadHandled() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (route in tabs.map { it.route }) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = backStack?.destination?.route == t.route,
                        onClick = { navigateTab(t.route) },
                        icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                        modifier = Modifier.testTag("tab_${t.route}"),
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(pad)) {
            composable(Routes.HOME) {
                HomeScreen(
                    onSettings = { nav.navigate(Routes.SETTINGS) }, onOpenDownloads = { navigateTab(Routes.DOWNLOADS) },
                    onOpenDownload = { e -> scope.launch { openDownloaded(rootVm, e.fileUri, openFile, context) } },
                    onShareDownload = { e -> e.fileUri?.let { FileIntents.share(context, it, e.mimeType, e.fileName) } },
                )
            }
            composable(Routes.FILES) { FilesScreen({ nav.navigate(Routes.SETTINGS) }, openFile) }
            composable(Routes.DOWNLOADS) { DownloadsScreen({ nav.navigate(Routes.SETTINGS) }, openFile) }
            composable(Routes.SETTINGS) { SettingsScreen({ nav.popBackStack() }, { nav.navigate(Routes.PRIVACY) }, { nav.navigate(Routes.TERMS) }) }
            composable(Routes.PRIVACY) { LegalScreen("Privacy Policy", R.raw.privacy_policy) { nav.popBackStack() } }
            composable(Routes.TERMS) { LegalScreen("Terms of Service", R.raw.terms_of_service) { nav.popBackStack() } }
            composable(Routes.PLAYER, listOf(navArgument("uri") { type = NavType.StringType })) { e ->
                PlayerScreen(e.arguments?.getString("uri").orEmpty()) { nav.popBackStack() }
            }
            composable(Routes.VIEWER, listOf(navArgument("uri") { type = NavType.StringType })) { e ->
                ImageViewerScreen(e.arguments?.getString("uri").orEmpty()) { nav.popBackStack() }
            }
        }
    }
}

private suspend fun openDownloaded(vm: RootViewModel, uri: String?, open: (LibraryFileEntity) -> Unit, context: android.content.Context) {
    val file = uri?.let { vm.library.get(it) }
    if (file != null) open(file) else android.widget.Toast.makeText(context, "That file was moved or deleted.", android.widget.Toast.LENGTH_SHORT).show()
}
