// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - localized string resources parity tests
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every `values-<lang>/strings.xml` carries exactly the keys of the English `values/strings.xml`
 * and the same format specifiers (`%s`, `%d`, `%1$s`, `%%`): a missing key shows English, and a
 * changed specifier crashes `String.format` at run time. Pure JVM, reads the resource files.
 */
class LocalizedStringsTest {
    private val res = File("src/main/res")
    private val entry = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val specifier = Regex("""%(?:\d+\$)?[sd]|%%""")

    private fun load(dir: String): Map<String, String> =
        entry.findAll(File(res, "$dir/strings.xml").readText(Charsets.UTF_8))
            .associate { it.groupValues[1] to it.groupValues[2] }

    private val english get() = load("values")
    private val languages get() = res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").isFile }!!
        .map { it.name }.sorted()

    @Test fun `the eight languages are all present`() {
        assertEquals(
            listOf("values-de", "values-es", "values-fr", "values-it", "values-ja", "values-pt", "values-zh"),
            languages,
        )
    }

    @Test fun `every language has exactly the English keys`() {
        val base = english
        assertTrue(base.size > 150)
        for (lang in languages) {
            val table = load(lang)
            assertEquals("$lang is missing keys", emptySet<String>(), base.keys - table.keys)
            assertEquals("$lang has extra keys", emptySet<String>(), table.keys - base.keys)
        }
    }

    @Test fun `every language keeps the English format specifiers`() {
        val base = english
        for (lang in languages) {
            val table = load(lang)
            for ((key, value) in base) {
                assertEquals(
                    "$lang/$key changed its format specifiers",
                    specifier.findAll(value).map { it.value }.sorted().toList(),
                    specifier.findAll(table.getValue(key)).map { it.value }.sorted().toList(),
                )
            }
        }
    }
}
