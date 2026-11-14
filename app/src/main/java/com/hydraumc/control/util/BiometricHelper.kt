// =============================================================================
// HYDRA-UMC CONTROL - Utility helper for biometric authentication
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import com.hydraumc.control.R
import javax.crypto.Cipher

/**
 * Helper class to manage Biometric (Fingerprint/Face) authentication.
 */
object BiometricHelper {

    /**
     * Checks if biometric authentication is available on the device.
     */
    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                           BiometricManager.Authenticators.BIOMETRIC_WEAK
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /** True when the phone has a strong biometric enrolled (fingerprint or strong face scan), the only kind that opens a Keystore key. */
    fun isStrongBiometricAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows a strong-only biometric prompt bound to [cipher] (CryptoObject): [onSuccess] gets the cipher the Keystore
     * unlocked, and a success without it is an error.
     */
    fun showStrongBiometricPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val unlocked = result.cryptoObject?.cipher
                    if (unlocked == null) onError(activity.getString(R.string.biometric_auth_failed)) else onSuccess(unlocked)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError(activity.getString(R.string.biometric_auth_failed))
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(activity.getString(R.string.cancel_button))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    /**
     * Shows the biometric prompt to the user.
     */
    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError(activity.getString(R.string.biometric_auth_failed))
                }
            },
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(activity.getString(R.string.cancel_button))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                                     BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
