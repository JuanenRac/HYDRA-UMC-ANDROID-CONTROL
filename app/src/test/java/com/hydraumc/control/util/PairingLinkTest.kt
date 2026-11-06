// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/util/PairingLinkTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import com.hydraumc.control.network.RobotCommandContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading the text of a pairing QR code: what is accepted, and every way a link can be refused. */
class PairingLinkTest {
    private val fp = "ab".repeat(32)

    private fun ok(text: String): PairingLink.Offer = (PairingLink.parse(text) as PairingLink.Result.Ok).offer

    private fun problem(text: String?): PairingLink.Problem = (PairingLink.parse(text) as PairingLink.Result.Refused).problem

    @Test
    fun aCompleteLinkIsRead() {
        val offer = ok("hydra-umc://pair?host=192.168.0.180&port=3000&fp=$fp&name=Workshop%20CM5")
        assertEquals("192.168.0.180", offer.host)
        assertEquals(3000, offer.port)
        assertEquals(fp, offer.fingerprint)
        assertEquals("Workshop CM5", offer.name)
        assertTrue(offer.usesPinning)
    }

    @Test
    fun theFingerprintMayCarryColonsAndCapitals() {
        val spelled = fp.uppercase().chunked(2).joinToString(":")
        assertEquals(fp, ok("hydra-umc://pair?host=hydra.local&port=443&fp=$spelled").fingerprint)
    }

    @Test
    fun aLinkWithoutFingerprintIsRefused() {
        assertEquals(PairingLink.Problem.MISSING_FINGERPRINT, problem("hydra-umc://pair?host=hydra.local&port=3000"))
        assertEquals(PairingLink.Problem.MISSING_FINGERPRINT, problem("hydra-umc://pair?host=hydra.local&port=3000&fp="))
        assertEquals(PairingLink.Problem.MISSING_FINGERPRINT, problem("hydra-umc://pair?host=hydra.local&port=3000&fp=%20%20"))
    }

    @Test
    fun theNameDefaultsToTheHostWhenTheLinkHasNone() {
        val offer = ok("hydra-umc://pair?host=hydra.local&port=3000&fp=$fp")
        assertEquals("hydra.local", offer.name)
        assertTrue(offer.usesPinning)
    }

    @Test
    fun theSchemeAndHostAreNotCaseSensitiveAndSpacesAroundAreIgnored() {
        assertEquals(3000, ok("  HYDRA-UMC://PAIR?HOST=10.0.0.5&PORT=3000&FP=$fp  ").port)
    }

    @Test
    fun anIpv6AddressInBracketsIsRead() {
        assertEquals("fe80::1", ok("hydra-umc://pair?host=%5Bfe80::1%5D&port=3000&fp=$fp").host)
    }

    @Test
    fun otherTextIsNotAPairingLink() {
        assertEquals(PairingLink.Problem.EMPTY, problem(null))
        assertEquals(PairingLink.Problem.EMPTY, problem("   "))
        assertEquals(PairingLink.Problem.NOT_A_PAIRING_LINK, problem("https://example.com/?host=a&port=1"))
        assertEquals(PairingLink.Problem.NOT_A_PAIRING_LINK, problem("hydra-umc://other?host=a&port=1"))
        assertFalse(PairingLink.looksLikePairing("WIFI:S:x;T:WPA;P:y;;"))
        assertTrue(PairingLink.looksLikePairing("hydra-umc://pair?host=a&port=1"))
    }

    @Test
    fun aHostThatIsNotAHostIsRefused() {
        assertEquals(PairingLink.Problem.MISSING_HOST, problem("hydra-umc://pair?port=3000"))
        assertEquals(PairingLink.Problem.BAD_HOST, problem("hydra-umc://pair?host=300.1.1.1&port=3000"))
        assertEquals(PairingLink.Problem.BAD_HOST, problem("hydra-umc://pair?host=a%2Fb&port=3000"))
        assertEquals(PairingLink.Problem.BAD_HOST, problem("hydra-umc://pair?host=user%40evil.com&port=3000"))
        assertEquals(PairingLink.Problem.BAD_HOST, problem("hydra-umc://pair?host=-bad.local&port=3000"))
    }

    @Test
    fun aPortOutsideTheRangeIsRefused() {
        assertEquals(PairingLink.Problem.BAD_PORT, problem("hydra-umc://pair?host=a.local"))
        assertEquals(PairingLink.Problem.BAD_PORT, problem("hydra-umc://pair?host=a.local&port=0"))
        assertEquals(PairingLink.Problem.BAD_PORT, problem("hydra-umc://pair?host=a.local&port=65536"))
        assertEquals(PairingLink.Problem.BAD_PORT, problem("hydra-umc://pair?host=a.local&port=-1"))
        assertEquals(PairingLink.Problem.BAD_PORT, problem("hydra-umc://pair?host=a.local&port=80x"))
        assertEquals(65535, ok("hydra-umc://pair?host=a.local&port=65535&fp=$fp").port)
    }

    @Test
    fun aFingerprintThatIsNotASha256IsRefused() {
        assertEquals(PairingLink.Problem.BAD_FINGERPRINT, problem("hydra-umc://pair?host=a.local&port=1&fp=abcd"))
        assertEquals(PairingLink.Problem.BAD_FINGERPRINT, problem("hydra-umc://pair?host=a.local&port=1&fp=${"zz".repeat(32)}"))
    }

    @Test
    fun aRepeatedFieldIsRefusedSoALinkCannotSayTwoThings() {
        assertEquals(PairingLink.Problem.REPEATED_FIELD, problem("hydra-umc://pair?host=a.local&host=b.local&port=1"))
    }

    @Test
    fun unknownFieldsAreIgnoredAndNeverKept() {
        val offer = ok("hydra-umc://pair?host=a.local&port=1&fp=$fp&token=secret&password=x")
        assertEquals("a.local", offer.host)
        assertFalse(offer.toString().contains("secret"))
    }

    @Test
    fun theNameIsBoundedAndHasNoControlCharacters() {
        assertEquals(PairingLink.Problem.BAD_NAME, problem("hydra-umc://pair?host=a.local&port=1&fp=$fp&name=${"n".repeat(RobotCommandContract.PAIRING_NAME_MAX + 1)}"))
        assertEquals(PairingLink.Problem.BAD_NAME, problem("hydra-umc://pair?host=a.local&port=1&fp=$fp&name=a%0Ab"))
        assertEquals(RobotCommandContract.PAIRING_NAME_MAX, ok("hydra-umc://pair?host=a.local&port=1&fp=$fp&name=${"n".repeat(RobotCommandContract.PAIRING_NAME_MAX)}").name.length)
    }

    @Test
    fun aBrokenPercentEncodingAndATooLongTextAreRefused() {
        assertEquals(PairingLink.Problem.BAD_ENCODING, problem("hydra-umc://pair?host=a%zz&port=1"))
        assertEquals(PairingLink.Problem.TOO_LONG, problem("hydra-umc://pair?host=a.local&port=1&name=" + "x".repeat(RobotCommandContract.PAIRING_MAX_PAYLOAD_CHARS)))
    }

    @Test
    fun pairingIsOffUntilThePersonTurnsItOn() {
        assertEquals(false, RobotCommandContract.PAIRING_ENABLED_BY_DEFAULT)
    }
}
