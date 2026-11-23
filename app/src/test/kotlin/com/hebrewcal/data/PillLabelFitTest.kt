package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PillLabelFitTest {

    private val measure = { s: String -> s.length * 10f }

    private val tehillim = PillSpec("Daily Tehillim", "49–54")   // 22 chars
    private val parsha = PillSpec("Weekly Parsha", "Bereshit")   // 24 chars
    private val chitas = PillSpec("Chitas", null)                // 6 chars

    private fun fit(pills: List<PillSpec>, budget: Float, chrome: Float = 0f, gap: Float = 0f) =
        PillLabelFit.fit(pills, budget, chrome, gap, measure = measure)

    @Test fun `all fit returns labels unchanged`() {
        val out = fit(listOf(tehillim, parsha, chitas), budget = 1000f)
        assertEquals(listOf("Daily Tehillim · 49–54", "Weekly Parsha · Bereshit", "Chitas"), out)
    }

    @Test fun `chrome and gap count against the budget`() {
        // text = 220 + 60 = 280; chrome 2*10 + gap 10 = 30 -> needs 310
        val pills = listOf(tehillim, chitas)
        assertEquals(listOf("Daily Tehillim · 49–54", "Chitas"), fit(pills, 310f, 10f, 10f))
        val out = fit(pills, 309f, 10f, 10f)
        assertTrue(out[0].endsWith(" · 49–54") && out[0].contains("…"))
        assertEquals("Chitas", out[1])
    }

    @Test fun `one too wide pill changes alone and keeps its detail`() {
        // 22 + 6 = 28 chars = 280px; budget 250 -> must lose 3+ chars
        val out = fit(listOf(tehillim, chitas), 250f)
        assertTrue(out[0].endsWith(" · 49–54"))
        assertTrue(out[0].substringBefore(" · ").endsWith("…"))
        assertEquals("Chitas", out[1])
        assertTrue(out.sumOf { measure(it).toDouble() } <= 250.0)
    }

    @Test fun `widest shrinks first and chitas is untouched`() {
        val out = fit(listOf(tehillim, parsha, chitas), 450f) // full = 520
        assertEquals("Chitas", out[2])
        assertTrue(out.sumOf { measure(it).toDouble() } <= 450.0)
        // widest-first: 520 -> 450 needs 70px (7 chars); the pair ends within 1 char of each other
        assertTrue(Math.abs(out[0].length - out[1].length) <= 1)
        assertEquals(450, out.sumOf { it.length } * 10)
        assertTrue(out[1].endsWith(" · Bereshit"))
    }

    @Test fun `budget smaller than details falls back to whole string ellipsis`() {
        val out = fit(listOf(tehillim, parsha, chitas), 60f)
        assertEquals(3, out.size)
        assertTrue(out.sumOf { measure(it).toDouble() } <= 60.0)
    }

    @Test fun `zero budget does not throw`() {
        val out = fit(listOf(tehillim, parsha, chitas), 0f)
        assertEquals(3, out.size)
    }

    @Test fun `middle ellipsis keeps both ends`() {
        val out = fit(listOf(PillSpec("A", "123456789")), 80f)
        assertTrue(out[0].length <= 8)
        assertTrue(out[0].startsWith("A"))
        assertTrue(out[0].endsWith("9"))
        assertTrue(out[0].contains("…"))
    }

    @Test fun `hebrew labels keep their detail suffix`() {
        val pills = listOf(PillSpec("תהילים יומי", "מט–נד"), PillSpec("פרשת השבוע", "בראשית"), PillSpec("חת״ת", null))
        val out = fit(pills, 280f)
        assertTrue(out[0].endsWith(" · מט–נד"))
        assertTrue(out[1].endsWith(" · בראשית"))
        assertEquals("חת״ת", out[2])
        assertTrue(out.sumOf { measure(it).toDouble() } <= 280.0)
    }

    @Test fun `empty list gives empty list`() {
        assertEquals(emptyList<String>(), fit(emptyList(), 100f))
    }

    @Test fun `single pill that fits is unchanged`() {
        assertEquals(listOf("Daily Tehillim · 49–54"), fit(listOf(tehillim), 500f))
    }

    @Test fun `spec full joins title and detail`() {
        assertEquals("Daily Tehillim · 49–54", tehillim.full)
        assertEquals("Chitas", chitas.full)
    }

    @Test fun `parsha plus chitas budget two margins`() {
        assertEquals(2, PillLabelFit.leadingMarginCount(false, true, true))
        assertEquals(2, PillLabelFit.leadingMarginCount(true, true, true))
        assertEquals(1, PillLabelFit.leadingMarginCount(true, false, true))
        assertEquals(0, PillLabelFit.leadingMarginCount(true, false, false))
        val pills = listOf(parsha, chitas) // 300px of text
        // gap 10: with two real margins, 300 + 2*10 = 320 is needed
        assertEquals(listOf("Weekly Parsha · Bereshit", "Chitas"), fit0(pills, 320f, gapCount = 2))
        val out = fit0(pills, 319f, gapCount = 2)
        assertTrue(out[0] != "Weekly Parsha · Bereshit")
        // the default n-1 count would have wrongly accepted 319
        assertEquals(listOf("Weekly Parsha · Bereshit", "Chitas"), fit0(pills, 319f, gapCount = 1))
    }

    private fun fit0(pills: List<PillSpec>, budget: Float, gapCount: Int) =
        PillLabelFit.fit(pills, budget, 0f, 10f, gapCount, measure)

    @Test fun `detail pill reaches lone ellipsis before chitas is shortened`() {
        // Only Chitas (60px) is wide enough to matter; budget 70 forces tehillim down to "…" (10px)
        val out = fit(listOf(tehillim, chitas), 70f)
        assertEquals("…", out[0])
        assertEquals("Chitas", out[1])
    }

    @Test fun `chitas shortens only once detail pills are exhausted`() {
        val out = fit(listOf(tehillim, chitas), 40f)
        assertEquals("…", out[0])
        assertTrue(out[1].length < "Chitas".length)
        assertTrue(out.sumOf { measure(it).toDouble() } <= 40.0)
    }

    @Test fun `stage one to stage two transition keeps both ends`() {
        val spec = PillSpec("Weekly", "Bereshit") // "Weekly · Bereshit" = 17 chars
        // stage 1 bottoms out at "W… · Bereshit" (13 chars); 100px forces stage 2
        val out = fit(listOf(spec), 100f)[0]
        assertEquals(10, out.length)
        assertTrue(out.startsWith("Wee"))
        assertTrue(out.endsWith("shit"))
        assertTrue(out.contains("…"))
        // 130px is still reachable in stage 1: detail intact
        val stage1 = fit(listOf(spec), 130f)[0]
        assertEquals("W… · Bereshit", stage1)
    }

    @Test fun `single pill with tiny achievable budget ends within budget`() {
        for (b in listOf(10f, 20f, 35f, 50f)) {
            val out = fit(listOf(tehillim), b)[0]
            assertTrue("$b -> $out", measure(out) <= b)
        }
    }
}
