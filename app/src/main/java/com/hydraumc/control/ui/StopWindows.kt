// =============================================================================
// HYDRA-UMC CONTROL - the global STOP inside dialogs and menus
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// A dialog or a menu of Android is a window of its own above the screen: the STOP of the top bar cannot be pressed while one is open (the first
// press only closes it, or does nothing). So every window of the app carries its own global STOP: the dialogs next to their title, the menus in
// their first row. Every dialog and menu of the app is opened through the functions of this file (a test checks the sources).
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hydraumc.control.viewmodel.RobotViewModel

/** The view model whose global STOP the windows of this part of the screen carry; null (nothing signed in) draws none. */
val LocalStopViewModel = staticCompositionLocalOf<RobotViewModel?> { null }

/** The test tag of the STOP of a dialog or a menu. */
const val WINDOW_STOP_TAG = "window-stop"

/** The STOP of a window, with what the last one did below it. Draws nothing without [LocalStopViewModel]. */
@Composable
fun WindowStop(modifier: Modifier = Modifier) {
    val viewModel = LocalStopViewModel.current ?: return
    // The title of a dialog has a tall line height that would cut the two lines of the button.
    CompositionLocalProvider(LocalTextStyle provides TextStyle.Default) {
        Column(modifier = modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.End) {
            // No touch zone: the window of a dialog has its own coordinates, and the filter of obscured touches belongs to the screen.
            GlobalStopButton(viewModel, Modifier.testTag(WINDOW_STOP_TAG), touchZone = false)
            StopAllBanner(viewModel)
        }
    }
}

/** An AlertDialog with the global STOP above its title. */
@Composable
fun StopAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = {
            Column(verticalArrangement = Arrangement.Top) {
                WindowStop()
                title?.invoke()
            }
        },
        text = text,
    )
}

/** A Dialog of free content with the global STOP above it. [content] gets what is left of the height. */
@Composable
fun StopDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismissRequest) {
        Column {
            WindowStop()
            Box(modifier = Modifier.weight(1f, fill = false)) { content() }
        }
    }
}

/** A DropdownMenu whose first row is the global STOP. */
@Composable
fun StopDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        WindowStopRow()
        content()
    }
}

/** An ExposedDropdownMenu whose first row is the global STOP. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdownMenuBoxScope.StopExposedDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ExposedDropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        WindowStopRow()
        content()
    }
}

/** The STOP of a menu: no banner, a menu measures its rows by their own width. */
@Composable
private fun WindowStopRow() {
    val viewModel = LocalStopViewModel.current ?: return
    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        GlobalStopButton(viewModel, Modifier.testTag(WINDOW_STOP_TAG), touchZone = false)
    }
}
