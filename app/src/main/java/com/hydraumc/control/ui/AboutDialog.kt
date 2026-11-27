// =============================================================================
// HYDRA-UMC CONTROL - About dialog with application metadata
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydraumc.control.BuildConfig
import com.hydraumc.control.R
import com.hydraumc.control.ui.theme.HydraColors
import com.hydraumc.control.util.DiagnosticReport

/**
 * Dialog displaying information about the application, version, and author, and the button that prepares a report for
 * whoever helps (see [DiagnosticReport]: the app sends nothing by itself).
 * @param onDismiss Callback to close the dialog.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val shareLabel = stringResource(R.string.about_share)
    StopAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_button)) } },
        dismissButton = { TextButton(onClick = { shareDiagnostic(context, shareLabel) }) { Text(shareLabel) } },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(painter = painterResource(R.drawable.ic_hydra_logo), contentDescription = null, modifier = Modifier.size(72.dp))
                Text("HYDRA-UMC", fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                Text(stringResource(R.string.about_title), style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted, textAlign = TextAlign.Center)
                // BuildConfig.VERSION_NAME is the number of the build that is running.
                Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelLarge, color = HydraColors.Accent)
                Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyMedium, color = HydraColors.Muted, textAlign = TextAlign.Center)
                HorizontalDivider(color = HydraColors.Outline)
                Text(stringResource(R.string.developed_by), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.company_name), color = HydraColors.Accent)
                Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted)
                Text(stringResource(R.string.about_share_hint), style = MaterialTheme.typography.labelSmall, color = HydraColors.Muted, textAlign = TextAlign.Center)
            }
        },
        containerColor = HydraColors.SurfaceRaised,
    )
}

/** Builds the report and hands it to the sharing sheet of the phone. */
private fun shareDiagnostic(context: Context, title: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "HYDRA-UMC CONTROL diagnostic report")
        putExtra(Intent.EXTRA_TEXT, DiagnosticReport.ofThisPhone(context))
    }
    context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
