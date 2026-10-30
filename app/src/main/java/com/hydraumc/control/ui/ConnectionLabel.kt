package com.hydraumc.control.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hydraumc.control.R
import com.hydraumc.control.network.WsStatus

/** The localized text of a connection state; the state itself is what the code compares, never this text. */
@Composable
fun WsStatus.label(): String = when (this) {
    WsStatus.CONNECTING -> stringResource(R.string.status_connecting)
    WsStatus.CONNECTED -> stringResource(R.string.status_connected)
    WsStatus.DISCONNECTED -> stringResource(R.string.status_disconnected)
}
