// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Strict semantic version parser for updates
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.update

/**
 * A release version accepted by the HYDRA-UMC Android distribution channel.
 * Pre-release labels are intentionally rejected: the stable application must
 * never treat a draft, nightly, or malformed GitHub tag as an update.
 */
data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int = compareValuesBy(
        this,
        other,
        SemanticVersion::major,
        SemanticVersion::minor,
        SemanticVersion::patch,
    )

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val stablePattern = Regex("^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$")

        /** Returns null instead of guessing when a GitHub tag is not stable semver. */
        fun parseStable(value: String): SemanticVersion? {
            val match = stablePattern.matchEntire(value.trim()) ?: return null
            return SemanticVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt(),
            )
        }
    }
}
