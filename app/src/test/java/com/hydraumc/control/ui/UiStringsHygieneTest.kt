package com.hydraumc.control.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the texts of the interface in the string resources, in the eight languages: no `Text("a literal")` in the
 * screens, no string nobody uses, and the same keys in every language.
 */
class UiStringsHygieneTest {
    private val main: File = listOf("src/main", "app/src/main").map { File(it) }.first { it.isDirectory }

    private fun kotlinSources() = main.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun keysOf(dir: String): Set<String> =
        Regex("""<string name="([^"]+)"""").findAll(File(main, "res/$dir/strings.xml").readText()).map { it.groupValues[1] }.toSet()

    @Test
    fun noScreenTextIsALiteralWord() {
        // A literal is allowed when it holds no word of three letters (a unit, a coordinate, an axis) or is the product name.
        val literal = Regex("""\bText\(\s*"([^"$]*)"""")
        val offenders = kotlinSources().flatMap { file ->
            literal.findAll(file.readText()).map { it.groupValues[1] to file.name }.toList()
        }.filter { (text, _) -> text != "HYDRA-UMC" && Regex("[A-Za-z]{3,}").containsMatchIn(text) }
        assertTrue("Text(\"literal\") in: $offenders", offenders.isEmpty())
    }

    @Test
    fun everyStringIsUsed() {
        val used = buildString {
            kotlinSources().forEach { append(it.readText()) }
            main.walkTopDown().filter { it.isFile && it.extension == "xml" && !it.invariantSeparatorsPath.contains("/res/values") }
                .forEach { append(it.readText()) }
        }
        val dead = keysOf("values").filter { key -> !Regex("""R\.string\.$key\b|@string/$key\b""").containsMatchIn(used) }
        assertTrue("strings no code uses: $dead", dead.isEmpty())
    }

    @Test
    fun everyLanguageHasTheSameKeys() {
        val english = keysOf("values")
        for (dir in listOf("values-de", "values-es", "values-fr", "values-it", "values-ja", "values-pt", "values-zh")) {
            val keys = keysOf(dir)
            assertEquals("$dir lacks", emptySet<String>(), english - keys)
            assertEquals("$dir has extra", emptySet<String>(), keys - english)
        }
    }
}
