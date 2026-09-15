// =============================================================================
// HYDRA-UMC CONTROL - Real regression test for a live layout bug
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Real live report: on a phone-width screen, the About icon in
 * MainScreen.kt's own "Sub-header Row with Server Selector and Icons"
 * looked visibly smaller than its 4 neighbors (Profile/Voice/Telemetry/
 * Settings). A first fix (an explicit 28dp size, matching this file's own
 * git history) did not actually fix it - the owner's own follow-up report
 * correctly traced it to the server-selector label's own real width: a
 * longer translated string (Spanish "Servidores (N)" is measurably wider
 * than English "Servers (N)") pushed the trailing icon Row partly off the
 * right edge of the screen. The About IconButton's own measured size never
 * actually changed - only how much of it stayed on-screen, which reads
 * exactly like "the About icon is tiny", worst for whichever icon is last.
 *
 * Root cause: the outer Row (`Arrangement.SpaceBetween`, no weights at
 * all) let the server-selector `Box` claim its own full, unbounded
 * natural width - a long enough label had nothing capping it before it
 * started eating into the icon Row's own space.
 *
 * This mirrors MainScreen.kt's own real Sub-header Row structure directly
 * (same Row/Arrangement/weight shape, same 5 trailing icons) rather than
 * rendering the whole screen, which needs a real RobotViewModel/
 * AppUpdateViewModel this test has no interest in faking - kept in sync
 * BY HAND with that file, the same "same real structure, no shared code"
 * tradeoff already accepted elsewhere in this ecosystem (e.g. Studio's own
 * Parol6/Faze4 kinematics chains). If MainScreen.kt's own Sub-header Row
 * ever changes, update this mirror to match.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A real phone-width device, not whatever Robolectric's own default screen
// size happens to be - the whole bug is width-dependent, so an unrealistic
// (e.g. tablet-wide) default would silently hide it again. Same device
// qualifier this repo's own GreetingScreenshotTest.kt already uses.
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class SubHeaderRowLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Composable
    private fun SubHeaderRow(serverLabel: String) {
        MaterialTheme {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Server Selector (Left) - weight(1f, fill = false) caps this
                // at its fair share of the row instead of its full unbounded
                // natural width, the real fix under test.
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.height(40.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = serverLabel,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }

                // Icons (Right) - real fixed content, must always keep its
                // own full natural width and stay fully on-screen.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {}) { Icon(Icons.Default.Person, contentDescription = "profile") }
                    IconButton(onClick = {}) { Icon(Icons.Default.Mic, contentDescription = "voice") }
                    IconButton(onClick = {}) { Icon(Icons.Default.Terminal, contentDescription = "telemetry") }
                    IconButton(onClick = {}) { Icon(Icons.Default.Settings, contentDescription = "settings") }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Info, contentDescription = "about", modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }

    @Test
    fun `a long real translated label does not push the About icon off screen`() {
        // The real Spanish string for a realistic worst case (double-digit
        // server count), measurably wider than English's own "Servers (12)".
        composeTestRule.setContent { SubHeaderRow(serverLabel = "Servidores (12)") }

        val root = composeTestRule.onRoot().fetchSemanticsNode()
        val about = composeTestRule.onNodeWithContentDescription("about").fetchSemanticsNode()

        assertTrue(
            "About icon's right edge (${about.boundsInRoot.right}) is past the screen's own right edge (${root.boundsInRoot.right})",
            about.boundsInRoot.right <= root.boundsInRoot.right,
        )
        composeTestRule.onNodeWithContentDescription("about").assertIsDisplayed()
    }

    @Test
    fun `an unrealistically long label still keeps every trailing icon fully on screen`() {
        // Deliberately extreme - proves the fix holds however long a real
        // translated string ever gets, not just for today's actual Spanish
        // one, and that ellipsis (not overflow) is what absorbs it.
        composeTestRule.setContent {
            SubHeaderRow(serverLabel = "Un nombre de servidor extremadamente largo y descriptivo (99)")
        }

        val root = composeTestRule.onRoot().fetchSemanticsNode()
        for (label in listOf("profile", "voice", "telemetry", "settings", "about")) {
            val node = composeTestRule.onNodeWithContentDescription(label).fetchSemanticsNode()
            assertTrue(
                "$label icon's right edge (${node.boundsInRoot.right}) is past the screen's own right edge (${root.boundsInRoot.right})",
                node.boundsInRoot.right <= root.boundsInRoot.right,
            )
        }
    }

    @Test
    fun `a short label leaves the About icon at its real full 28dp size`() {
        // Guards the OTHER real regression risk: the weight-based fix must
        // never shrink the About icon below its own real intended size when
        // there is plenty of room (e.g. English's own short "Servers (1)").
        composeTestRule.setContent { SubHeaderRow(serverLabel = "Servers (1)") }

        val about = composeTestRule.onNodeWithContentDescription("about").fetchSemanticsNode()
        val widthDp = about.boundsInRoot.width / composeTestRule.density.density
        assertTrue(
            "About icon's own width (${widthDp}dp) is smaller than its real intended 28dp",
            widthDp >= 27.5f,
        )
    }
}
