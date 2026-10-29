package ai.devcodex.agent.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** 校验六套 strings.xml 的 key 集合、格式占位符与 plurals 完整性一致。 */
class StringsParityTest {

    private val resDir: File = listOf("src/main/res", "app/src/main/res")
        .map { File(it) }
        .first { it.isDirectory }

    private val dirs = linkedMapOf(
        "en" to "values",
        "zh-CN" to "values-zh-rCN",
        "zh-TW" to "values-zh-rTW",
        "zh-HK" to "values-zh-rHK",
        "ja" to "values-ja",
        "ko" to "values-ko",
    )

    private val placeholder = Regex("%(\\d+)\\$([sd])")

    private class Parsed(val strings: Map<String, String>, val plurals: Map<String, Set<String>>)

    private fun parse(dir: String): Parsed {
        val f = File(resDir, "$dir/strings.xml")
        assertTrue("missing file: ${f.path}", f.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val strings = linkedMapOf<String, String>()
        val nodes = doc.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as Element
            strings[e.getAttribute("name")] = e.textContent
        }
        val plurals = linkedMapOf<String, Set<String>>()
        val pn = doc.getElementsByTagName("plurals")
        for (i in 0 until pn.length) {
            val e = pn.item(i) as Element
            val items = e.getElementsByTagName("item")
            plurals[e.getAttribute("name")] = (0 until items.length)
                .map { (items.item(it) as Element).getAttribute("quantity") }.toSet()
        }
        return Parsed(strings, plurals)
    }

    private fun placeholders(text: String): List<String> =
        placeholder.findAll(text).map { it.value }.sorted().toList()

    @Test
    fun allLocalesHaveSameKeys() {
        val base = parse("values")
        for ((tag, dir) in dirs) {
            val p = parse(dir)
            assertEquals("string keys differ in $tag", base.strings.keys, p.strings.keys)
            assertEquals("plurals keys differ in $tag", base.plurals.keys, p.plurals.keys)
        }
    }

    @Test
    fun placeholdersMatchBase() {
        val base = parse("values")
        for ((tag, dir) in dirs) {
            val p = parse(dir)
            for ((k, v) in base.strings) {
                assertEquals("placeholders differ: $k in $tag", placeholders(v), placeholders(p.strings[k] ?: ""))
            }
        }
    }

    @Test
    fun noEmptyValues() {
        for ((tag, dir) in dirs) {
            for ((k, v) in parse(dir).strings) {
                assertTrue("empty value: $k in $tag", v.isNotBlank())
            }
        }
    }

    @Test
    fun cjkLocalesUseOtherQuantityAndEnglishHasOneAndOther() {
        val en = parse("values").plurals
        for ((k, q) in en) assertTrue("en plurals $k needs one+other", q.containsAll(setOf("one", "other")))
        for ((tag, dir) in dirs) {
            if (tag == "en") continue
            for ((k, q) in parse(dir).plurals) assertTrue("$tag plurals $k needs other", "other" in q)
        }
    }
}
