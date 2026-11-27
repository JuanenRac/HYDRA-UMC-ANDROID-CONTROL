// =============================================================================
// HYDRA-UMC CONTROL - The few pieces every screen shares
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// The same pieces as the sister app of the family (A.R.M.O.R.), with the colours of HYDRA-UMC: a raised card with a thin
// border, an icon in a soft disc, a status light, the title of a screen, a figure and a menu row.
// =============================================================================
package com.hydraumc.control.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A raised card with a thin border: the container of nearly everything. [tint] colours the border and a faint wash (attention). */
@Composable
fun Panel(modifier: Modifier = Modifier, tint: Color? = null, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val base = modifier.clip(shape)
    Surface(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
        shape = shape,
        color = HydraColors.SurfaceRaised,
        contentColor = HydraColors.Text,
        border = BorderStroke(1.dp, (tint ?: HydraColors.Outline).copy(alpha = if (tint != null) 0.7f else 1f)),
    ) {
        Column(
            Modifier.background(Brush.verticalGradient(listOf((tint ?: HydraColors.Accent).copy(alpha = if (tint != null) 0.10f else 0.04f), Color.Transparent))),
            content = content,
        )
    }
}

/** An icon in a soft coloured disc: what the screens use in place of words. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color = HydraColors.Accent, size: Dp = 44.dp, description: String? = null) {
    Box(Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** A small round light: green when all is well, red or amber when not, grey when unknown. */
@Composable
fun StatusDot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color).border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape))
}

/** The title of a screen: an icon in a disc, the words, and whatever buttons the screen has on its right. */
@Composable
fun ScreenTitle(icon: ImageVector, title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted)
        }
        actions()
    }
}

/** One big figure with its icon and what it counts. */
@Composable
fun StatTile(icon: ImageVector, value: String, label: String, tint: Color = HydraColors.Accent, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Panel(modifier, tint = tint.takeIf { it == HydraColors.Amber || it == HydraColors.Alert }, onClick = onClick) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted)
        }
    }
}

/** A row of a menu: an icon, a title, one line that explains it and an arrow. */
@Composable
fun MenuRow(icon: ImageVector, title: String, subtitle: String?, modifier: Modifier = Modifier, tint: Color = HydraColors.Accent, onClick: () -> Unit) {
    Panel(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(icon, tint)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = HydraColors.Muted)
        }
    }
}
