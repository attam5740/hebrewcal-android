package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElulTehillimTest {
    @Test fun firstOfElulIsChapters1to3() {
        assertEquals("Psalms.1-3", ElulTehillim.portionFor(6, 1)!!.sefariaRef)
    }

    @Test fun nineteenthOfElulIsChapters55to57() {
        val p = ElulTehillim.portionFor(6, 19)!!
        assertEquals(55, p.firstChapter); assertEquals(57, p.lastChapter)
    }

    @Test fun lastOfElulIsChapters85to87() {
        assertEquals("Psalms.85-87", ElulTehillim.portionFor(6, 29)!!.sefariaRef)
    }

    @Test fun fifthOfTishreiContinuesSequence() {
        // day 34 of the cycle -> chapters 100-102
        assertEquals("Psalms.100-102", ElulTehillim.portionFor(7, 5)!!.sefariaRef)
    }

    @Test fun ninthOfTishreiEndsAt114() {
        assertEquals("Psalms.112-114", ElulTehillim.portionFor(7, 9)!!.sefariaRef)
    }

    @Test fun yomKippurCompletesTheBook() {
        assertEquals("Psalms.115-150", ElulTehillim.portionFor(7, 10)!!.sefariaRef)
    }

    @Test fun outOfSeasonIsNull() {
        assertNull(ElulTehillim.portionFor(7, 11))  // after Yom Kippur
        assertNull(ElulTehillim.portionFor(8, 1))   // Cheshvan
        assertNull(ElulTehillim.portionFor(5, 29))  // Av
    }
}
