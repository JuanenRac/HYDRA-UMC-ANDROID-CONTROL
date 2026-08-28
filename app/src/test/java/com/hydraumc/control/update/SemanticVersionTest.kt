// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Stable update-version parser unit tests
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {
    @Test
    fun `accepts stable release tags with or without v prefix`() {
        assertEquals(SemanticVersion(1, 2, 3), SemanticVersion.parseStable("v1.2.3"))
        assertEquals(SemanticVersion(0, 1, 0), SemanticVersion.parseStable("0.1.0"))
    }

    @Test
    fun `rejects draft style non-stable tags instead of guessing`() {
        assertNull(SemanticVersion.parseStable("v1.2.3-beta.1"))
        assertNull(SemanticVersion.parseStable("release-1.2.3"))
        assertNull(SemanticVersion.parseStable("1.2"))
    }

    @Test
    fun `orders versions by major minor patch`() {
        assertTrue(SemanticVersion(0, 1, 9) < SemanticVersion(0, 2, 0))
        assertTrue(SemanticVersion(1, 0, 0) > SemanticVersion(0, 9, 9))
    }
}
