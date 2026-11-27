// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/ui/StopWindowsTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.network.AuthStore
import com.hydraumc.control.network.StopAllStatus
import com.hydraumc.control.network.UserProfile
import com.hydraumc.control.viewmodel.RobotViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** With a dialog or a menu open, the global STOP is inside that window, one tap reaches it and the window stays. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class StopWindowsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val emptyStore = object : AuthStore {
        override suspend fun loadAuth() = UserProfile("", "", "", rememberMe = false, isLoggedIn = false)
        override suspend fun saveAuth(profile: UserProfile) {}
    }

    private fun viewModel(): RobotViewModel {
        val model = RobotViewModel(ApplicationProvider.getApplicationContext<Application>(), emptyStore)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        return model
    }

    private fun show(model: RobotViewModel, content: @Composable () -> Unit) {
        composeTestRule.setContent { CompositionLocalProvider(LocalStopViewModel provides model) { content() } }
        composeTestRule.waitForIdle()
    }

    private fun pressStopAndCheck(model: RobotViewModel, stillShown: String) {
        composeTestRule.onNodeWithTag(WINDOW_STOP_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(WINDOW_STOP_TAG).performClick()
        composeTestRule.waitForIdle()
        // No server is connected here: the stop must say so instead of looking done.
        assertTrue(model.stopAllStatus.value is StopAllStatus.NotConfirmed)
        composeTestRule.onNodeWithText(stillShown).assertExists()
    }

    @Test
    fun anAlertDialogCarriesTheStopAndStaysOpen() {
        val model = viewModel()
        var dismissed = false
        show(model) { StopAlertDialog(onDismissRequest = { dismissed = true }, confirmButton = {}, title = { Text("question") }) }
        pressStopAndCheck(model, "question")
        assertEquals(false, dismissed)
    }

    @Test
    fun aDialogOfFreeContentCarriesTheStop() {
        val model = viewModel()
        show(model) { StopDialog(onDismissRequest = {}) { Text("notes") } }
        pressStopAndCheck(model, "notes")
    }

    @Test
    fun aMenuCarriesTheStop() {
        val model = viewModel()
        show(model) { StopDropdownMenu(expanded = true, onDismissRequest = {}) { Text("entry") } }
        pressStopAndCheck(model, "entry")
    }

    @Test
    fun aRealDialogOfTheAppCarriesTheStop() {
        val model = viewModel()
        var closed = false
        show(model) { AboutDialog { closed = true } }
        composeTestRule.onNodeWithTag(WINDOW_STOP_TAG).performClick()
        composeTestRule.waitForIdle()
        assertTrue(model.stopAllStatus.value is StopAllStatus.NotConfirmed)
        assertEquals(false, closed)
    }

    @Test
    fun withoutASessionNoStopIsDrawn() {
        composeTestRule.setContent { StopAlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("question") }) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(WINDOW_STOP_TAG).assertDoesNotExist()
    }

    /** A new dialog or menu must not bring the defect back: only StopWindows.kt opens the bare ones. */
    @Test
    fun noScreenOpensAWindowWithoutTheStop() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val bare = Regex("""(?<![A-Za-z])(AlertDialog|BasicAlertDialog|Dialog|DropdownMenu|ExposedDropdownMenu|ModalBottomSheet|Popup|DatePickerDialog)\s*\(""")
        val sources = root.walkTopDown().filter { it.extension == "kt" && it.name != "StopWindows.kt" }.toList()
        assertTrue("no sources under ${root.absolutePath}", sources.size > 20)
        val found = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (line.trimStart().startsWith("import ") || line.trimStart().startsWith("//") || line.trimStart().startsWith("*")) null
                else bare.find(line)?.let { "${file.name}:${index + 1}: ${line.trim()}" }
            }
        }
        assertEquals(emptyList<String>(), found)
    }
}
