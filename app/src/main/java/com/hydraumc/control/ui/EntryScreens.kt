// =============================================================================
// HYDRA-UMC CONTROL - The screens before the app: first use, finding the server, signing in
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// The first time on a phone: a welcome with the three steps, the search for the server, the sign-in and, once in, the
// notices. Nothing here changes how the app connects: choosing a server only fills in its address, and every switch is
// the same one Settings shows, with its value.
// =============================================================================
package com.hydraumc.control.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydraumc.control.R
import com.hydraumc.control.model.ServerInfo
import com.hydraumc.control.ui.theme.HydraColors
import com.hydraumc.control.ui.theme.IconBadge
import com.hydraumc.control.ui.theme.Panel
import com.hydraumc.control.ui.theme.ScreenTitle
import com.hydraumc.control.util.FirstRun
import com.hydraumc.control.viewmodel.RobotViewModel

/** Where somebody not signed in is. */
enum class EntryStep { WELCOME, DISCOVERY, SIGN_IN, HELP }

/** The shape of the big buttons of these screens. */
internal val BigButtonShape = RoundedCornerShape(14.dp)

/** The backdrop of the screens before the app: a metallic blue that fades into the background. */
internal fun entryBackdrop() = Brush.verticalGradient(listOf(Color(0xFF0D2338), HydraColors.Background, HydraColors.Background))

/**
 * Everything shown while nobody is signed in. A phone that never signed in starts at the welcome; any other one at the sign-in.
 */
@Composable
fun SignInFlow(viewModel: RobotViewModel, firstRun: Boolean) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(if (firstRun) EntryStep.WELCOME else EntryStep.SIGN_IN) }
    var about by rememberSaveable { mutableStateOf(false) }
    if (about) AboutDialog { about = false }
    val search = {
        viewModel.scanNetwork()
        step = EntryStep.DISCOVERY
    }
    when (step) {
        EntryStep.WELCOME -> WelcomeScreen(onSearch = search, onManual = { step = EntryStep.SIGN_IN })
        EntryStep.DISCOVERY -> {
            BackHandler { step = EntryStep.SIGN_IN }
            DiscoveryScreen(
                servers = viewModel.discoveredServers.value,
                searching = viewModel.isScanning.value,
                onChoose = { server ->
                    viewModel.ipAddress.value = server.host
                    viewModel.port.value = server.port.toString()
                    step = EntryStep.SIGN_IN
                },
                onAgain = { viewModel.scanNetwork() },
                onManual = { step = EntryStep.SIGN_IN },
            )
        }
        EntryStep.SIGN_IN -> LoginScreen(viewModel, onSearch = search, onAbout = { about = true }, onHelp = { step = EntryStep.HELP })
        EntryStep.HELP -> {
            BackHandler { step = EntryStep.SIGN_IN }
            Column(Modifier.fillMaxSize().background(HydraColors.Background).statusBarsPadding().navigationBarsPadding()) {
                IconButton(onClick = { step = EntryStep.SIGN_IN }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_previous), tint = HydraColors.Accent)
                }
                HelpScreen(HelpTopic.START)
            }
        }
    }
    // Remembered once somebody gets in, so the welcome is not shown again after signing out.
    val signedIn = viewModel.isLoggedIn.value
    LaunchedEffect(signedIn) { if (signedIn) FirstRun.markSeen(context) }
}

@Composable
private fun EntryColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(entryBackdrop())) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** The welcome of a phone that never signed in. */
@Composable
fun WelcomeScreen(onSearch: () -> Unit, onManual: () -> Unit) {
    EntryColumn {
        Image(painter = painterResource(R.drawable.ic_hydra_logo), contentDescription = null, modifier = Modifier.size(96.dp))
        Text("HYDRA-UMC", fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp, color = HydraColors.Text)
        Text(stringResource(R.string.welcome_subtitle), color = HydraColors.Muted, style = MaterialTheme.typography.bodyMedium)
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Step(Icons.Filled.Wifi, stringResource(R.string.welcome_step1), stringResource(R.string.welcome_step1_hint))
                Step(Icons.Filled.Person, stringResource(R.string.welcome_step2), stringResource(R.string.welcome_step2_hint))
                Step(Icons.Filled.Notifications, stringResource(R.string.welcome_step3), stringResource(R.string.welcome_step3_hint))
            }
        }
        Button(onClick = onSearch, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("welcome-search"), shape = BigButtonShape) {
            Icon(Icons.Filled.Radar, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.welcome_search), fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onManual, modifier = Modifier.testTag("welcome-manual")) { Text(stringResource(R.string.welcome_manual), color = HydraColors.Accent) }
    }
}

@Composable
private fun Step(icon: ImageVector, title: String, hint: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(icon, size = 40.dp)
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted)
        }
    }
}

/** The servers found on this network; choosing one only fills in its address. */
@Composable
fun DiscoveryScreen(servers: List<ServerInfo>, searching: Boolean, onChoose: (ServerInfo) -> Unit, onAgain: () -> Unit, onManual: () -> Unit) {
    EntryColumn {
        ScreenTitle(Icons.Filled.Radar, stringResource(R.string.discovery_title), stringResource(R.string.discovery_step, 1)) {
            IconButton(onClick = onManual) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_previous), tint = HydraColors.Accent) }
        }
        servers.forEach { server ->
            Panel(Modifier.fillMaxWidth(), tint = HydraColors.Ok, onClick = { onChoose(server) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(Icons.Filled.Dns, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(server.displayName, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.server_info, server.host, server.port, server.controllerCount, server.robotCount),
                            style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted,
                        )
                    }
                    Text(stringResource(R.string.discovery_choose), color = HydraColors.Accent, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (searching) {
            Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp, color = HydraColors.Accent)
                    Text(stringResource(R.string.discovery_searching))
                }
            }
        } else {
            OutlinedButton(onClick = onAgain, modifier = Modifier.fillMaxWidth(), shape = BigButtonShape) { Text(stringResource(R.string.discovery_again)) }
        }
        Panel(Modifier.fillMaxWidth(), tint = HydraColors.Amber) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.discovery_missing_title), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.discovery_missing_text), style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted)
            }
        }
        FilledTonalButton(onClick = onManual, modifier = Modifier.fillMaxWidth().height(50.dp), shape = BigButtonShape) {
            Text(stringResource(R.string.welcome_manual))
        }
    }
}

/**
 * The last step of the first use, already signed in: it therefore carries the global STOP. The switches are the ones of
 * Settings, shown with their value; nothing is switched on for the person.
 */
@Composable
fun NoticesStep(viewModel: RobotViewModel, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    EntryColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { GlobalStopButton(viewModel) }
        StopAllBanner(viewModel)
        ScreenTitle(Icons.Filled.Notifications, stringResource(R.string.notices_title), stringResource(R.string.discovery_step, 3))
        NoticeSwitch(
            stringResource(R.string.notifications_enable_toggle), stringResource(R.string.notifications_enable_description),
            viewModel.notificationsEnabled.value, viewModel::setNotificationsEnabled,
        )
        NoticeSwitch(
            stringResource(R.string.watch_alerts_switch_label), stringResource(R.string.watch_alerts_switch_hint),
            viewModel.watchAlertsEnabled.value, viewModel::setWatchAlertsEnabled,
        )
        Panel(Modifier.fillMaxWidth(), tint = HydraColors.Alert) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.notices_stop_title), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.notices_stop_text), style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted)
            }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("notices-start"), shape = BigButtonShape) {
            Text(stringResource(R.string.notices_start), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NoticeSwitch(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

/** How to reach the server: from the workshop and from outside. */
@Composable
fun ReachHelpDialog(onDismiss: () -> Unit) {
    StopAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_button)) } },
        title = { Text(stringResource(R.string.reach_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(stringResource(R.string.reach_text)) } },
        containerColor = HydraColors.SurfaceRaised,
    )
}
