package com.hebrewcal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hebrew_cal_prefs")

data class UserPreferences(
    val language: CalendarLanguage = CalendarLanguage.ENGLISH,
    val location: CalendarLocation = CalendarLocation.DIASPORA,
    val showParsha: Boolean = true,
    val showGregorianDate: Boolean = true,
    val showZmanim: Boolean = false,
    val zmanimTimeFormat: ZmanimTimeFormat = ZmanimTimeFormat.TWELVE_HOUR,
    val zmanimLocationSource: ZmanimLocationSource = ZmanimLocationSource.GPS,
    val zmanimManualCity: String = "",
    val zmanimManualLat: Double = 0.0,
    val zmanimManualLng: Double = 0.0,
    val selectedZmanim: Set<String> = DEFAULT_SELECTED_ZMANIM
)

enum class CalendarLanguage { HEBREW, ENGLISH }
enum class CalendarLocation { ISRAEL, DIASPORA }
enum class ZmanimTimeFormat { TWELVE_HOUR, TWENTY_FOUR_HOUR }
enum class ZmanimLocationSource { GPS, MANUAL }

// All supported zmanim keys
object ZmanimKeys {
    const val ALOT_HASHACHAR = "alot_hashachar"
    const val MISHEYAKIR = "misheyakir"
    const val HANETZ = "hanetz"
    const val SOF_ZMAN_SHEMA_GRA = "sof_zman_shema_gra"
    const val SOF_ZMAN_SHEMA_MGA = "sof_zman_shema_mga"
    const val SOF_ZMAN_TEFILLA_GRA = "sof_zman_tefilla_gra"
    const val SOF_ZMAN_TEFILLA_MGA = "sof_zman_tefilla_mga"
    const val CHATZOT = "chatzot"
    const val MINCHA_GEDOLA = "mincha_gedola"
    const val MINCHA_KETANA = "mincha_ketana"
    const val PLAG_HAMINCHA = "plag_hamincha"
    const val SHKIAH = "shkiah"
    const val TZET_HAKOCHAVIM = "tzet_hakochavim"
    const val TZET_RABBEINU_TAM = "tzet_rabbeinu_tam"

    val ALL = listOf(
        ALOT_HASHACHAR, MISHEYAKIR, HANETZ,
        SOF_ZMAN_SHEMA_GRA, SOF_ZMAN_SHEMA_MGA,
        SOF_ZMAN_TEFILLA_GRA, SOF_ZMAN_TEFILLA_MGA,
        CHATZOT, MINCHA_GEDOLA, MINCHA_KETANA,
        PLAG_HAMINCHA, SHKIAH, TZET_HAKOCHAVIM, TZET_RABBEINU_TAM
    )

    fun displayName(key: String): String = when (key) {
        ALOT_HASHACHAR    -> "Alot HaShachar (Dawn)"
        MISHEYAKIR        -> "Misheyakir (Earliest Tallit)"
        HANETZ            -> "HaNetz (Sunrise)"
        SOF_ZMAN_SHEMA_GRA -> "Sof Zman Shema — GRA"
        SOF_ZMAN_SHEMA_MGA -> "Sof Zman Shema — MGA"
        SOF_ZMAN_TEFILLA_GRA -> "Sof Zman Tefilla — GRA"
        SOF_ZMAN_TEFILLA_MGA -> "Sof Zman Tefilla — MGA"
        CHATZOT           -> "Chatzot (Halachic Midday)"
        MINCHA_GEDOLA     -> "Mincha Gedola"
        MINCHA_KETANA     -> "Mincha Ketana"
        PLAG_HAMINCHA     -> "Plag HaMincha"
        SHKIAH            -> "Shkiah (Sunset)"
        TZET_HAKOCHAVIM   -> "Tzet HaKochavim (Nightfall)"
        TZET_RABBEINU_TAM -> "Tzet HaKochavim (Rabbeinu Tam)"
        else              -> key
    }
}

val DEFAULT_SELECTED_ZMANIM = setOf(
    ZmanimKeys.ALOT_HASHACHAR,
    ZmanimKeys.HANETZ,
    ZmanimKeys.SOF_ZMAN_SHEMA_GRA,
    ZmanimKeys.SOF_ZMAN_TEFILLA_GRA,
    ZmanimKeys.CHATZOT,
    ZmanimKeys.PLAG_HAMINCHA,
    ZmanimKeys.SHKIAH,
    ZmanimKeys.TZET_HAKOCHAVIM
)

object PreferenceKeys {
    val LANGUAGE = stringPreferencesKey("language")
    val LOCATION = stringPreferencesKey("location")
    val SHOW_PARSHA = booleanPreferencesKey("show_parsha")
    val SHOW_GREGORIAN = booleanPreferencesKey("show_gregorian")
    val SHOW_ZMANIM = booleanPreferencesKey("show_zmanim")
    val ZMANIM_TIME_FORMAT = stringPreferencesKey("zmanim_time_format")
    val ZMANIM_LOCATION_SOURCE = stringPreferencesKey("zmanim_location_source")
    val ZMANIM_MANUAL_CITY = stringPreferencesKey("zmanim_manual_city")
    val ZMANIM_MANUAL_LAT = doublePreferencesKey("zmanim_manual_lat")
    val ZMANIM_MANUAL_LNG = doublePreferencesKey("zmanim_manual_lng")
    val SELECTED_ZMANIM = stringSetPreferencesKey("selected_zmanim")
}

class UserPreferencesRepository(private val context: Context) {

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            language = CalendarLanguage.valueOf(
                prefs[PreferenceKeys.LANGUAGE] ?: CalendarLanguage.ENGLISH.name
            ),
            location = CalendarLocation.valueOf(
                prefs[PreferenceKeys.LOCATION] ?: CalendarLocation.DIASPORA.name
            ),
            showParsha = prefs[PreferenceKeys.SHOW_PARSHA] ?: true,
            showGregorianDate = prefs[PreferenceKeys.SHOW_GREGORIAN] ?: true,
            showZmanim = prefs[PreferenceKeys.SHOW_ZMANIM] ?: false,
            zmanimTimeFormat = ZmanimTimeFormat.valueOf(
                prefs[PreferenceKeys.ZMANIM_TIME_FORMAT] ?: ZmanimTimeFormat.TWELVE_HOUR.name
            ),
            zmanimLocationSource = ZmanimLocationSource.valueOf(
                prefs[PreferenceKeys.ZMANIM_LOCATION_SOURCE] ?: ZmanimLocationSource.GPS.name
            ),
            zmanimManualCity = prefs[PreferenceKeys.ZMANIM_MANUAL_CITY] ?: "",
            zmanimManualLat = prefs[PreferenceKeys.ZMANIM_MANUAL_LAT] ?: 0.0,
            zmanimManualLng = prefs[PreferenceKeys.ZMANIM_MANUAL_LNG] ?: 0.0,
            selectedZmanim = prefs[PreferenceKeys.SELECTED_ZMANIM] ?: DEFAULT_SELECTED_ZMANIM
        )
    }

    suspend fun updateLanguage(language: CalendarLanguage) {
        context.dataStore.edit { it[PreferenceKeys.LANGUAGE] = language.name }
    }

    suspend fun updateLocation(location: CalendarLocation) {
        context.dataStore.edit { it[PreferenceKeys.LOCATION] = location.name }
    }

    suspend fun updateShowParsha(show: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.SHOW_PARSHA] = show }
    }

    suspend fun updateShowGregorianDate(show: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.SHOW_GREGORIAN] = show }
    }

    suspend fun updateShowZmanim(show: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.SHOW_ZMANIM] = show }
    }

    suspend fun updateZmanimTimeFormat(format: ZmanimTimeFormat) {
        context.dataStore.edit { it[PreferenceKeys.ZMANIM_TIME_FORMAT] = format.name }
    }

    suspend fun updateZmanimLocationSource(source: ZmanimLocationSource) {
        context.dataStore.edit { it[PreferenceKeys.ZMANIM_LOCATION_SOURCE] = source.name }
    }

    suspend fun updateZmanimManualLocation(city: String, lat: Double, lng: Double) {
        context.dataStore.edit {
            it[PreferenceKeys.ZMANIM_MANUAL_CITY] = city
            it[PreferenceKeys.ZMANIM_MANUAL_LAT] = lat
            it[PreferenceKeys.ZMANIM_MANUAL_LNG] = lng
        }
    }

    suspend fun updateSelectedZmanim(selected: Set<String>) {
        context.dataStore.edit { it[PreferenceKeys.SELECTED_ZMANIM] = selected }
    }
}
