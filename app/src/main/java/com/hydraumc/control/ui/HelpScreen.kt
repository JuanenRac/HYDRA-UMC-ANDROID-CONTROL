// =============================================================================
// HYDRA-UMC CONTROL - Help by screen and by level
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// The texts travel inside the app, so the help works without a connection and before signing in. The levels are the
// ones of HYDRA-UMC STUDIO: basic, advanced, technical or all. The level chosen is remembered.
// =============================================================================
package com.hydraumc.control.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hydraumc.control.R
import com.hydraumc.control.ui.theme.HydraColors
import com.hydraumc.control.ui.theme.MenuRow
import com.hydraumc.control.ui.theme.Panel
import com.hydraumc.control.ui.theme.ScreenTitle

enum class HelpTopic(
    val icon: ImageVector,
    @param:StringRes val title: Int,
    @param:StringRes val basic: Int,
    @param:StringRes val advanced: Int,
    @param:StringRes val technical: Int,
) {
    START(Icons.Filled.Wifi, R.string.help_start_title, R.string.help_start_basic, R.string.help_start_advanced, R.string.help_start_technical),
    HOME(Icons.Filled.Home, R.string.tab_home, R.string.help_home_basic, R.string.help_home_advanced, R.string.help_home_technical),
    CONTROL(Icons.Filled.ControlCamera, R.string.tab_control, R.string.help_control_basic, R.string.help_control_advanced, R.string.help_control_technical),
    CAMERAS(Icons.Filled.Videocam, R.string.tab_cameras, R.string.help_cameras_basic, R.string.help_cameras_advanced, R.string.help_cameras_technical),
    NOTICES(Icons.Filled.Notifications, R.string.tab_notices, R.string.help_notices_basic, R.string.help_notices_advanced, R.string.help_notices_technical),
    ACCOUNT(Icons.Filled.Person, R.string.common_account, R.string.help_account_basic, R.string.help_account_advanced, R.string.help_account_technical),
    SAFETY(Icons.Filled.Shield, R.string.help_safety_title, R.string.help_safety_basic, R.string.help_safety_advanced, R.string.help_safety_technical),
}

enum class HelpLevel(@param:StringRes val label: Int) {
    BASIC(R.string.common_level_basic),
    ADVANCED(R.string.common_level_advanced),
    TECHNICAL(R.string.common_level_technical),
    ALL(R.string.notices_filter_all);

    fun texts(topic: HelpTopic): List<Int> = when (this) {
        BASIC -> listOf(topic.basic)
        ADVANCED -> listOf(topic.advanced)
        TECHNICAL -> listOf(topic.technical)
        ALL -> listOf(topic.basic, topic.advanced, topic.technical)
    }
}

/** The topics that hold every word of [query] in their title or in any of their texts. */
fun helpSearch(query: String, text: (Int) -> String): List<HelpTopic> {
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return HelpTopic.entries.filter { topic ->
        val all = listOf(topic.title, topic.basic, topic.advanced, topic.technical).joinToString(" ") { text(it) }
        words.all { all.contains(it, ignoreCase = true) }
    }
}

private const val HELP_FILE = "hydra_help"

/** [start] is the topic of the screen the help was asked from; null shows the list of topics. */
@Composable
fun HelpScreen(start: HelpTopic?, onOpenScreen: ((HelpTopic) -> Unit)? = null) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val prefs = remember { context.getSharedPreferences(HELP_FILE, Context.MODE_PRIVATE) }
    var level by remember { mutableStateOf(HelpLevel.entries.firstOrNull { it.name == prefs.getString("level", null) } ?: HelpLevel.BASIC) }
    var topic by rememberSaveable { mutableStateOf(start) }
    var query by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val shown = topic
        ScreenTitle(
            Icons.AutoMirrored.Filled.HelpOutline,
            if (shown == null) stringResource(R.string.help_title) else stringResource(shown.title),
            stringResource(R.string.help_subtitle),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HelpLevel.entries.forEach { option ->
                FilterChip(
                    selected = level == option,
                    onClick = {
                        level = option
                        prefs.edit().putString("level", option.name).apply()
                    },
                    label = { Text(stringResource(option.label)) },
                    modifier = Modifier.testTag("help-level-" + option.name),
                )
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("help-search"),
            leadingIcon = { Icon(Icons.Filled.Search, null) }, label = { Text(stringResource(R.string.help_search)) },
        )
        if (shown != null && query.isBlank()) {
            level.texts(shown).forEach { text ->
                Panel(Modifier.fillMaxWidth()) { Text(stringResource(text), Modifier.padding(14.dp)) }
            }
            if (shown != HelpTopic.SAFETY) {
                Panel(Modifier.fillMaxWidth(), tint = HydraColors.Alert) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.help_safety_title), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.help_safety_basic), style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted)
                    }
                }
            }
            if (onOpenScreen != null && shown != HelpTopic.SAFETY && shown != HelpTopic.START) {
                MenuRow(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.help_open_screen), null) { onOpenScreen(shown) }
            }
            MenuRow(Icons.Filled.Apps, stringResource(R.string.help_all_topics), null) { topic = null }
        } else {
            val found = helpSearch(query) { resources.getString(it) }
            if (found.isEmpty()) Text(stringResource(R.string.help_nothing), color = HydraColors.Muted)
            found.forEach { entry ->
                MenuRow(entry.icon, stringResource(entry.title), null) {
                    topic = entry
                    query = ""
                }
            }
        }
    }
}
