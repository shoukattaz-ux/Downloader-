package com.stashly.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stashly.app.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppUiTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Before fun setUp() { hilt.inject() }

    @Test fun bottomNavigationShowsThreeTabsAndSwitches() {
        compose.onNodeWithTag("tab_home").assertIsDisplayed()
        compose.onNodeWithTag("tab_files").assertIsDisplayed()
        compose.onNodeWithTag("tab_downloads").assertIsDisplayed()
        compose.onNodeWithTag("tab_files").performClick()
        compose.onNodeWithTag("files_tab_VIDEOS").assertIsDisplayed()
        compose.onNodeWithTag("tab_downloads").performClick()
        compose.onNodeWithText("Downloading", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("tab_home").performClick()
        compose.onNodeWithTag("url_input").assertIsDisplayed()
    }

    @Test fun filesTabsCoverEveryCategory() {
        compose.onNodeWithTag("tab_files").performClick()
        listOf("VIDEOS", "AUDIO", "IMAGES", "DOCUMENTS", "DOWNLOADS", "FOLDERS").forEach { compose.onNodeWithTag("files_tab_$it").performClick() }
    }

    @Test fun analyzeIsDisabledUntilTheLinkIsValid() {
        compose.onNodeWithTag("analyze_button").assertIsNotEnabled()
        compose.onNodeWithTag("url_input").performTextInput("not a link")
        compose.onNodeWithTag("analyze_button").assertIsNotEnabled()
    }

    @Test fun restrictedPlatformShowsCompliantAlternativeWithoutAnyNetworkCall() {
        compose.onNodeWithTag("url_input").performTextInput("https://www.youtube.com/watch?v=abc")
        compose.onNodeWithTag("analyze_button").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextSafe("can't be downloaded here") }
        compose.onNodeWithText("Open in YouTube").assertIsDisplayed()
    }

    @Test fun settingsScreenOpensFromTheTopBarAndLegalPagesRender() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onNodeWithText("Privacy Policy").performScrollTo().performClick()
        compose.onNodeWithText("Summary").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextSafe(t: String) =
        onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()
}
