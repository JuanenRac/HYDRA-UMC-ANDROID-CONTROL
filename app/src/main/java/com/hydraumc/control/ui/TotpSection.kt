// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - ui/TotpSection.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hydraumc.control.R
import com.hydraumc.control.network.TotpFailure
import com.hydraumc.control.network.TotpRules
import com.hydraumc.control.ui.theme.HydraButton
import com.hydraumc.control.util.TotpPrefs
import com.hydraumc.control.viewmodel.TotpFlow
import com.hydraumc.control.viewmodel.TotpNotice
import com.hydraumc.control.viewmodel.TotpStep
import kotlinx.coroutines.launch

/** The text of a refusal of one of the second-factor calls: its string and, when it has one, the number it names. */
internal fun totpFailureText(failure: TotpFailure): Pair<Int, Int?> = when (failure) {
    TotpFailure.Unsupported -> R.string.totp_error_unsupported to null
    TotpFailure.Password -> R.string.totp_error_password to null
    TotpFailure.Code -> R.string.totp_error_code to null
    is TotpFailure.Wait -> R.string.totp_error_wait to failure.seconds
    TotpFailure.Already -> R.string.totp_error_already to null
    TotpFailure.Forbidden -> R.string.totp_error_forbidden to null
    is TotpFailure.Http -> R.string.totp_error_http to failure.status
    TotpFailure.Network -> R.string.totp_error_network to null
}

@Composable
private fun failureString(failure: TotpFailure): String {
    val (id, number) = totpFailureText(failure)
    return if (number == null) stringResource(id) else stringResource(id, number)
}

/**
 * The second sign-in factor of the signed-in account, under Settings > Security: turn it on (password, then the key to put in an authenticator
 * app, then the first code, then the recovery codes, shown once), see how many recovery codes are left, make new ones and turn it off. It is off for
 * every account until its owner turns it on. The key and the codes are hidden until the person asks to see them, are never put in the saved state of
 * the screen, and stay out of screenshots while shown unless the person turned that switch off.
 */
@Composable
fun TotpSection(flow: TotpFlow) {
    val context = LocalContext.current
    val state by flow.state.collectAsState()
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { TotpPrefs(context.applicationContext) }
    var blockCaptures by remember { mutableStateOf(TotpPrefs.DEFAULT_BLOCK_CAPTURES) }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    var appMissing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        blockCaptures = runCatching { prefs.blockCaptures() }.getOrDefault(TotpPrefs.DEFAULT_BLOCK_CAPTURES)
    }
    LaunchedEffect(flow) { flow.load() }
    // what the person typed or was shown goes with the screen
    DisposableEffect(flow) { onDispose { flow.clear() } }
    // every change of step starts the fields and the reveal over
    LaunchedEffect(state.step) {
        password = ""
        code = ""
        revealed = false
        appMissing = false
    }
    // keep the screen out of captures while a key or codes are shown
    val window = (context as? Activity)?.window
    DisposableEffect(window, blockCaptures, state.showsSecret) {
        val on = window != null && blockCaptures && state.showsSecret
        if (on) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (on) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.totp_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.totp_intro), style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        state.failure?.let {
            Text(
                failureString(it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
        state.notice?.let {
            Text(
                stringResource(
                    when (it) {
                        TotpNotice.TURNED_ON -> R.string.totp_done_on
                        TotpNotice.TURNED_OFF -> R.string.totp_done_off
                        TotpNotice.NEW_CODES -> R.string.totp_done_new_codes
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        when (state.step) {
            TotpStep.LOADING -> {
                Text(stringResource(R.string.totp_loading), style = MaterialTheme.typography.bodySmall)
                if (state.failure != null && !state.busy) TextButton(onClick = { flow.load() }) { Text(stringResource(R.string.totp_retry)) }
            }

            TotpStep.UNSUPPORTED -> Unit // the refusal above already says it

            TotpStep.OFF -> {
                Text(stringResource(R.string.totp_status_off), style = MaterialTheme.typography.bodyMedium)
                SecretField(password, { password = it }, R.string.totp_password_label, state.busy)
                HydraButton(
                    text = stringResource(R.string.totp_start),
                    onClick = { flow.begin(password) },
                    enabled = !state.busy && password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            TotpStep.ENROLLING -> {
                Text(stringResource(R.string.totp_key_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.totp_scan_hint), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                val hiddenDescription = stringResource(R.string.totp_key_hidden_desc)
                Text(
                    text = if (revealed) TotpRules.groupedKey(state.secret) else TotpRules.maskedKey(state.secret),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = if (revealed) Modifier else Modifier.semantics { contentDescription = hiddenDescription },
                )
                TextButton(onClick = { revealed = !revealed }) {
                    Text(stringResource(if (revealed) R.string.totp_key_hide else R.string.totp_key_show))
                }
                HydraButton(
                    text = stringResource(R.string.totp_open_app),
                    onClick = { appMissing = !openInAuthenticator(context, state.uri) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (appMissing) Text(stringResource(R.string.totp_open_app_missing), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { copyKey(context, state.secret) }) { Text(stringResource(R.string.totp_copy_key)) }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.totp_code_label)) },
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    HydraButton(
                        text = stringResource(R.string.totp_confirm),
                        onClick = { flow.confirm(code) },
                        enabled = !state.busy && code.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { flow.cancelEnrolment() }, enabled = !state.busy) { Text(stringResource(R.string.totp_cancel)) }
                }
            }

            TotpStep.RECOVERY -> {
                Text(stringResource(R.string.totp_recovery_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.totp_recovery_hint), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                val hiddenDescription = stringResource(R.string.totp_recovery_hidden_desc)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = if (revealed) Modifier else Modifier.semantics { contentDescription = hiddenDescription },
                ) {
                    state.recoveryCodes.forEach { item ->
                        Text(if (revealed) item else MASKED_RECOVERY_CODE, style = MaterialTheme.typography.titleMedium)
                    }
                }
                TextButton(onClick = { revealed = !revealed }) {
                    Text(stringResource(if (revealed) R.string.totp_recovery_hide else R.string.totp_recovery_show))
                }
                HydraButton(
                    text = stringResource(R.string.totp_recovery_saved),
                    onClick = { flow.acknowledgeRecovery() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            TotpStep.ON -> {
                Text(stringResource(R.string.totp_status_on, state.recoveryLeft.toInt()), style = MaterialTheme.typography.bodyMedium)
                if (state.recoveryLow) {
                    Text(
                        stringResource(R.string.totp_low_warning, state.recoveryLeft.toInt()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                SecretField(password, { password = it }, R.string.totp_password_label, state.busy)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.totp_turn_off_code_label)) },
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                val ready = !state.busy && password.isNotEmpty() && code.isNotBlank()
                HydraButton(
                    text = stringResource(R.string.totp_new_codes),
                    onClick = { flow.newRecoveryCodes(password, code) },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.totp_new_codes_hint), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                HydraButton(
                    text = stringResource(R.string.totp_turn_off),
                    onClick = { flow.disable(password, code) },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = blockCaptures,
                onCheckedChange = {
                    blockCaptures = it
                    scope.launch { runCatching { prefs.setBlockCaptures(it) } }
                },
            )
            Column {
                Text(stringResource(R.string.totp_block_captures_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.totp_block_captures_hint), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

/** What stands for a recovery code while the codes are hidden (the same shape, no characters). */
private const val MASKED_RECOVERY_CODE = "•••••-•••••"

/** A password or code field with the eye to see what was typed. */
@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, label: Int, busy: Boolean) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        enabled = !busy,
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(if (visible) R.string.hide_password else R.string.show_password),
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Hands the key to an authenticator app on this phone; false when no app takes `otpauth:` links. The address holds the key and is not logged. */
internal fun openInAuthenticator(context: Context, uri: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}

/** Puts the key on the clipboard marked as sensitive, so the system keeps it out of the clipboard preview (Android 13 and later). */
internal fun copyKey(context: Context, secret: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText("", secret)
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    clipboard.setPrimaryClip(clip)
}
