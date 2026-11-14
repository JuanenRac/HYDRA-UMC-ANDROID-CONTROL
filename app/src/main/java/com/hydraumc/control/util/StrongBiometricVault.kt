// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - util/StrongBiometricVault.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.hydraumc.control.network.PasswordSealer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * The saved password locked in an RSA key of the Android Keystore that needs a strong biometric for every use. Sealing
 * (public key) needs no prompt, so the password can be saved whenever the person edits the profile; opening
 * (private key) is possible only through a [javax.crypto.Cipher] handed to a BiometricPrompt as its CryptoObject, so
 * a bare "success" callback is not enough to read the password. A new fingerprint or face enrolled on the phone
 * invalidates the key and the person signs in with the password again.
 */
object StrongBiometricVault : PasswordSealer {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "hydra_umc_strong_biometric_v1"
    private const val TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun ensureKey() {
        val store = keyStore()
        if (store.containsAlias(ALIAS)) return
        val builder = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
        generator.initialize(builder.build())
        generator.generateKeyPair()
    }

    /** The password encrypted for this phone (Base64), or null when the key cannot be made: nothing is saved then. */
    override fun seal(plain: String): String? = try {
        ensureKey()
        val publicKey = keyStore().getCertificate(ALIAS).publicKey
        // The key object of the Keystore is bound to its purposes; a plain copy encrypts without a prompt.
        val open = KeyFactory.getInstance(publicKey.algorithm).generatePublic(X509EncodedKeySpec(publicKey.encoded))
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            open,
            OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT),
        )
        Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }

    /** The cipher to give to the biometric prompt, or null when the key is gone or was invalidated by a new enrolment. */
    fun decryptCipher(): Cipher? = try {
        val key = keyStore().getKey(ALIAS, null) as? PrivateKey
        if (key == null) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key)
            cipher
        }
    } catch (e: Exception) {
        null
    }

    /** The password, from the cipher the prompt authorised; null when it does not open. */
    fun open(cipher: Cipher, sealed: String): String? = try {
        String(cipher.doFinal(Base64.decode(sealed, Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    override fun forget() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }
}
