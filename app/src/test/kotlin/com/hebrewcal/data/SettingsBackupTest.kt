package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {

    @Test
    fun roundTripsEveryValueType() {
        val values = mapOf<String, Any>(
            "language" to "HEBREW",
            "show_zmanim" to true,
            "widget_opacity" to 60,
            "a_long" to 3_000_000_000L,
            "reader_text_size" to 18.5f,
            "zmanim_manual_lat" to 43.6532,
            "selected_zmanim" to setOf("alot_hashachar", "chatzot", "shkiah")
        )
        val restored = SettingsBackup.fromJson(SettingsBackup.toJson(values))
        assertEquals(values, restored)
        // The types must survive exactly: DataStore keys are typed.
        assertTrue(restored["widget_opacity"] is Int)
        assertTrue(restored["a_long"] is Long)
        assertTrue(restored["reader_text_size"] is Float)
        assertTrue(restored["zmanim_manual_lat"] is Double)
        assertTrue(restored["selected_zmanim"] is Set<*>)
    }

    @Test
    fun rejectsForeignFiles() {
        for (json in listOf("""{"settings":{}}""", """{"app":"other.app","settings":{}}""", """[1,2]""", "not json")) {
            try {
                SettingsBackup.fromJson(json)
                throw AssertionError("accepted: $json")
            } catch (e: Exception) {
                // expected
            }
        }
    }

    @Test
    fun emptySettingsAreValid() {
        assertEquals(emptyMap<String, Any>(), SettingsBackup.fromJson(SettingsBackup.toJson(emptyMap())))
    }
}
