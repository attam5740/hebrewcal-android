package com.hebrewcal.data

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class SefariaTextUrlTest {
    @Test fun tanakhRefAsksForTheMasorahEdition() {
        val u = SefariaRefs.textUrl("Genesis.30.14-27")
        assertTrue(u.contains("vhe=Miqra%20according%20to%20the%20Masorah"))
    }

    @Test fun tanyaRefPinsKehotOnBothSides() {
        val u = SefariaRefs.textUrl("Tanya, Part V; Kuntres Acharon 4:35")
        assertFalse("Masorah must not be requested for Tanya", u.contains("Masorah"))
        assertTrue(u.contains("vhe=Kehot%20Publication%20Society"))
        // Do not assert on the parenthesised part: URLEncoder percent-encodes
        // ( and ) as %28/%29, so match only the stable prefix.
        assertTrue(u.contains("ven=Kehot%20Publication%20Society"))
        assertTrue(u.contains("English"))
    }

    @Test fun siddurRefAsksForNoHebrewVersion() {
        assertFalse(SefariaRefs.textUrl("Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Modeh Ani").contains("vhe="))
    }

    @Test fun haftarahRefIsStillTanakh() {
        assertTrue(SefariaRefs.textUrl("Hosea 12:13-14:10").contains("Masorah"))
    }

    @Test fun unknownNonTanakhRefAsksForNoHebrewVersion() {
        assertFalse(SefariaRefs.textUrl("Mishnah Berakhot 1").contains("vhe="))
    }
}
