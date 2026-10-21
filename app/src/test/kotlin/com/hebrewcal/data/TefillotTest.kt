package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TefillotTest {

    @Test
    fun everyNusachHasAMoonBlessing() {
        val siddur = mapOf(
            Nusach.ASHKENAZ to "Siddur Ashkenaz",
            Nusach.SEFARD to "Siddur Sefard",
            Nusach.EDOT_HAMIZRACH to "Siddur Edot HaMizrach"
        )
        for (n in Nusach.values()) {
            val refs = Tefillot.kiddushLevanaRefs(n)
            assertTrue("$n has refs", refs.isNotEmpty())
            for (r in refs) {
                assertTrue("$n ref '$r' comes from its own siddur", r.startsWith(siddur[n]!!))
                assertTrue("refs must not contain the separator", !r.contains(Tefillot.REF_SEPARATOR))
            }
        }
        // Sefard's Kiddush Levanah already ends with Aleinu; the others append it.
        assertEquals(1, Tefillot.kiddushLevanaRefs(Nusach.SEFARD).size)
        assertEquals(2, Tefillot.kiddushLevanaRefs(Nusach.ASHKENAZ).size)
        assertEquals(2, Tefillot.kiddushLevanaRefs(Nusach.EDOT_HAMIZRACH).size)
    }
}
