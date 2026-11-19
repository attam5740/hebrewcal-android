package com.hebrewcal.data

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class SefariaTextUrlTest {
    @Test fun tanakhRefAsksForTheMasorahEdition() {
        val u = SefariaRefs.textUrl("Genesis.30.14-27")
        assertTrue(u.contains("vhe=Miqra%20according%20to%20the%20Masorah"))
    }

    private val kehot = "&vhe=Kehot%20Publication%20Society" +
        "&ven=Kehot%20Publication%20Society%20%28English%20Translation%29"

    private fun assertKehotPinned(ref: String) {
        val u = SefariaRefs.textUrl(ref)
        assertFalse("Masorah must not be requested for $ref", u.contains("Masorah"))
        // Whole query fragment, so the vhe check cannot be satisfied by the ven value's prefix.
        assertTrue("$ref -> $u", u.endsWith(kehot))
    }

    @Test fun tanyaRefPinsKehotOnBothSides() =
        assertKehotPinned("Tanya, Part V; Kuntres Acharon 4:35")

    @Test fun dottedTanyaRefPinsKehot() = assertKehotPinned("Tanya.1.2")

    @Test fun commaLessTanyaRefPinsKehot() = assertKehotPinned("Tanya 5")

    private val masorah = "vhe=Miqra%20according%20to%20the%20Masorah"

    @Test fun multiWordAndRomanNumeralBooksAreTanakh() {
        for (ref in listOf("I Samuel 3:1-10", "II Kings 4", "Song of Songs 1")) {
            assertTrue(ref, SefariaRefs.textUrl(ref).contains(masorah))
        }
    }

    @Test fun everyTanakhBookAsksForTheMasorahEdition() {
        val books = listOf(
            "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy",
            "Joshua", "Judges", "I Samuel", "II Samuel", "I Kings", "II Kings",
            "Isaiah", "Jeremiah", "Ezekiel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah",
            "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi",
            "Psalms", "Proverbs", "Job", "Song of Songs", "Ruth", "Lamentations", "Ecclesiastes",
            "Esther", "Daniel", "Ezra", "Nehemiah", "I Chronicles", "II Chronicles"
        )
        assertTrue(books.size == 39)
        for (b in books) {
            assertTrue("$b spaced", SefariaRefs.textUrl("$b 1").contains(masorah))
            assertTrue("$b dotted", SefariaRefs.textUrl("$b.1").contains(masorah))
        }
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
