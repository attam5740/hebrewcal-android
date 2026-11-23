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
        PillLabelFit.fit(pills, budget, chrome, gap, measure)

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
        assertTrue(out[1].length <= out[0].length + 1) // parsha (wider) gave at least as much
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
}
