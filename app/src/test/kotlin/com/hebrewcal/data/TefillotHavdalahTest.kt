package com.hebrewcal.data

import org.junit.Assert.assertTrue
import org.junit.Test

class TefillotHavdalahTest {
    @Test
    fun everyNusachHasItsOwnHavdalah() {
        for (n in Nusach.values()) {
            val refs = Tefillot.havdalahRefs(n)
            assertTrue("$n has a Havdalah ref", refs.isNotEmpty())
            assertTrue("$n refs mention Havdal", refs.all { it.contains("Havdal") })
            assertTrue(refs.none { it.contains(Tefillot.REF_SEPARATOR) })
        }
    }
}
