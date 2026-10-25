package com.hebrewcal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hebrew_cal_prefs")

data class UserPreferences(
    val language: CalendarLanguage = CalendarLanguage.ENGLISH,
    val location: CalendarLocation = CalendarLocation.DIASPORA,
    val showParsha: Boolean = true,
    val showParshaOnWeekdays: Boolean = false,
    val showOmer: Boolean = true,
    val showGregorianDate: Boolean = true,
    val showZmanim: Boolean = false,
    val zmanimTimeFormat: ZmanimTimeFormat = ZmanimTimeFormat.TWELVE_HOUR,
    val zmanimLocationSource: ZmanimLocationSource = ZmanimLocationSource.GPS,
    val zmanimManualCity: String = "",
    val zmanimManualLat: Double = 0.0,
    val zmanimManualLng: Double = 0.0,
    val selectedZmanim: Set<String> = DEFAULT_SELECTED_ZMANIM,
    val readerTextSizeSp: Float = 18f,
    val showTehillimButton: Boolean = true,
    val showParshaButton: Boolean = true,
    val showElulTehillim: Boolean = true,
    val prefetchNetwork: PrefetchNetwork = PrefetchNetwork.WIFI,
    val prefetchTehillimDays: Int = 7,
    val prefetchParshaWeeks: Int = 3,
    /** Home-screen widget background opacity, 10..100 percent. */
    val widgetOpacity: Int = 60,
    val candleLightingOffsetMinutes: Int = 18,
    val havdalahOpinion: HavdalahOpinion = HavdalahOpinion.STANDARD_8_5,
    val showShabbatBanner: Boolean = true,
    val showCelestialArc: Boolean = true,
    /** The first-run permissions checklist has been seen and closed. */
    val setupDone: Boolean = false,
    val nusach: Nusach = Nusach.ASHKENAZ,
    /** Master switch for prayer buttons; the checklist below applies only when on. */
    val prayerButtonsOn: Boolean = true,
    /** Keys of the [PrayerButton]s to offer in the notification (all by default). */
    val prayerButtons: Set<String> = PrayerButton.ALL_KEYS
)

enum class CalendarLanguage { HEBREW, ENGLISH }
enum class CalendarLocation { ISRAEL, DIASPORA }
enum class ZmanimTimeFormat { TWELVE_HOUR, TWENTY_FOUR_HOUR }
enum class ZmanimLocationSource { GPS, MANUAL }

/** Which siddur the app's prayer texts follow (e.g. Birkat HaLevana and its Aleinu). */
enum class Nusach { ASHKENAZ, SEFARD, EDOT_HAMIZRACH }

/** Background download policy for upcoming reader texts. */
enum class PrefetchNetwork { OFF, WIFI, ANY }

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
    const val CANDLE_LIGHTING = "candle_lighting"
    const val HAVDALAH = "havdalah"
    const val HAVDALAH_RT = "havdalah_rt"

    val ALL = listOf(
        ALOT_HASHACHAR, MISHEYAKIR, HANETZ,
        SOF_ZMAN_SHEMA_GRA, SOF_ZMAN_SHEMA_MGA,
        SOF_ZMAN_TEFILLA_GRA, SOF_ZMAN_TEFILLA_MGA,
        CHATZOT, MINCHA_GEDOLA, MINCHA_KETANA,
        PLAG_HAMINCHA, SHKIAH, TZET_HAKOCHAVIM, TZET_RABBEINU_TAM,
        CANDLE_LIGHTING, HAVDALAH, HAVDALAH_RT
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
        CANDLE_LIGHTING   -> "Candle Lighting (Erev Shabbat/Chag)"
        HAVDALAH          -> "Havdalah (Motzei Shabbat/Chag)"
        HAVDALAH_RT       -> "Havdalah (Rabbeinu Tam 72m)"
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
    val SHOW_PARSHA_WEEKDAYS = booleanPreferencesKey("show_parsha_weekdays")
    val SHOW_OMER = booleanPreferencesKey("show_omer")
    val SHOW_GREGORIAN = booleanPreferencesKey("show_gregorian")
    val SHOW_ZMANIM = booleanPreferencesKey("show_zmanim")
    val ZMANIM_TIME_FORMAT = stringPreferencesKey("zmanim_time_format")
    val ZMANIM_LOCATION_SOURCE = stringPreferencesKey("zmanim_location_source")
    val ZMANIM_MANUAL_CITY = stringPreferencesKey("zmanim_manual_city")
    val ZMANIM_MANUAL_LAT = doublePreferencesKey("zmanim_manual_lat")
    val ZMANIM_MANUAL_LNG = doublePreferencesKey("zmanim_manual_lng")
    val SELECTED_ZMANIM = stringSetPreferencesKey("selected_zmanim")
    val READER_TEXT_SIZE = floatPreferencesKey("reader_text_size")
    val SHOW_TEHILLIM_BUTTON = booleanPreferencesKey("show_tehillim_button")
    val SHOW_PARSHA_BUTTON = booleanPreferencesKey("show_parsha_button")
    val SHOW_ELUL_TEHILLIM = booleanPreferencesKey("show_elul_tehillim")
    val PREFETCH_NETWORK = stringPreferencesKey("prefetch_network")
    val PREFETCH_TEHILLIM_DAYS = intPreferencesKey("prefetch_tehillim_days")
    val PREFETCH_PARSHA_WEEKS = intPreferencesKey("prefetch_parsha_weeks")
    val WIDGET_OPACITY = intPreferencesKey("widget_opacity")
    val CANDLE_LIGHTING_OFFSET = intPreferencesKey("candle_lighting_offset")
    val HAVDALAH_OPINION = stringPreferencesKey("havdalah_opinion")
    val SHOW_SHABBAT_BANNER = booleanPreferencesKey("show_shabbat_banner")
    val SHOW_CELESTIAL_ARC = booleanPreferencesKey("show_celestial_arc")
    val SETUP_DONE = booleanPreferencesKey("setup_done")
    val NUSACH = stringPreferencesKey("nusach")
    // The buttons turned off, so buttons added later start on.
    val PRAYER_BUTTONS_OFF = stringSetPreferencesKey("prayer_buttons_off")
    val PRAYER_BUTTONS_ON = booleanPreferencesKey("prayer_buttons_on")
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
            showParshaOnWeekdays = prefs[PreferenceKeys.SHOW_PARSHA_WEEKDAYS] ?: false,
            showOmer = prefs[PreferenceKeys.SHOW_OMER] ?: true,
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
            selectedZmanim = prefs[PreferenceKeys.SELECTED_ZMANIM] ?: DEFAULT_SELECTED_ZMANIM,
            readerTextSizeSp = prefs[PreferenceKeys.READER_TEXT_SIZE] ?: 18f,
            showTehillimButton = prefs[PreferenceKeys.SHOW_TEHILLIM_BUTTON] ?: true,
            showParshaButton = prefs[PreferenceKeys.SHOW_PARSHA_BUTTON] ?: true,
            showElulTehillim = prefs[PreferenceKeys.SHOW_ELUL_TEHILLIM] ?: true,
            prefetchNetwork = PrefetchNetwork.valueOf(
                prefs[PreferenceKeys.PREFETCH_NETWORK] ?: PrefetchNetwork.WIFI.name
            ),
            prefetchTehillimDays = prefs[PreferenceKeys.PREFETCH_TEHILLIM_DAYS] ?: 7,
            prefetchParshaWeeks = prefs[PreferenceKeys.PREFETCH_PARSHA_WEEKS] ?: 3,
            widgetOpacity = prefs[PreferenceKeys.WIDGET_OPACITY] ?: 60,
            candleLightingOffsetMinutes = prefs[PreferenceKeys.CANDLE_LIGHTING_OFFSET] ?: 18,
            havdalahOpinion = HavdalahOpinion.valueOf(
                prefs[PreferenceKeys.HAVDALAH_OPINION] ?: HavdalahOpinion.STANDARD_8_5.name
            ),
            showShabbatBanner = prefs[PreferenceKeys.SHOW_SHABBAT_BANNER] ?: true,
            showCelestialArc = prefs[PreferenceKeys.SHOW_CELESTIAL_ARC] ?: true,
            setupDone = prefs[PreferenceKeys.SETUP_DONE] ?: false,
            nusach = Nusach.valueOf(prefs[PreferenceKeys.NUSACH] ?: Nusach.ASHKENAZ.name),
            prayerButtonsOn = prefs[PreferenceKeys.PRAYER_BUTTONS_ON] ?: true,
            prayerButtons = PrayerButton.ALL_KEYS - (prefs[PreferenceKeys.PRAYER_BUTTONS_OFF] ?: emptySet())
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

    suspend fun updateShowParshaOnWeekdays(show: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.SHOW_PARSHA_WEEKDAYS] = show }
    }

    suspend fun updateShowOmer(show: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.SHOW_OMER] = show }
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

    suspend fun updateReaderTextSize(sp: Float) {
        context.dataStore.edit { it[PreferenceKeys.READER_TEXT_SIZE] = sp }
    }

    suspend fun updateShowTehillimButton(show: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.SHOW_TEHILLIM_BUTTON] = show }
    }

    suspend fun updateShowParshaButton(show: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.SHOW_PARSHA_BUTTON] = show }
    }

    suspend fun updateShowElulTehillim(show: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.SHOW_ELUL_TEHILLIM] = show }
    }

    suspend fun updatePrefetchNetwork(network: PrefetchNetwork) {
        context.dataStore.edit { it[PreferenceKeys.PREFETCH_NETWORK] = network.name }
    }

    suspend fun updatePrefetchTehillimDays(days: Int) {
        context.dataStore.edit { it[PreferenceKeys.PREFETCH_TEHILLIM_DAYS] = days }
    }

    suspend fun updatePrefetchParshaWeeks(weeks: Int) {
        context.dataStore.edit { it[PreferenceKeys.PREFETCH_PARSHA_WEEKS] = weeks }
    }

    suspend fun updateWidgetOpacity(percent: Int) {
        context.dataStore.edit { it[PreferenceKeys.WIDGET_OPACITY] = percent.coerceIn(10, 100) }
    }

    suspend fun updateCandleLightingOffset(offsetMinutes: Int) {
        context.dataStore.edit { it[PreferenceKeys.CANDLE_LIGHTING_OFFSET] = offsetMinutes }
    }

    suspend fun updateHavdalahOpinion(opinion: HavdalahOpinion) {
        context.dataStore.edit { it[PreferenceKeys.HAVDALAH_OPINION] = opinion.name }
    }

    suspend fun updateShowShabbatBanner(show: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.SHOW_SHABBAT_BANNER] = show }
    }

    suspend fun updateShowCelestialArc(show: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.SHOW_CELESTIAL_ARC] = show }
    }

    suspend fun updateSetupDone(done: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.SETUP_DONE] = done }
    }

    suspend fun updatePrayerButtonsOn(on: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.PRAYER_BUTTONS_ON] = on }
    }

    suspend fun updatePrayerButton(key: String, on: Boolean) {
        context.dataStore.edit {
            val off = it[PreferenceKeys.PRAYER_BUTTONS_OFF] ?: emptySet()
            it[PreferenceKeys.PRAYER_BUTTONS_OFF] = if (on) off - key else off + key
        }
    }

    suspend fun updateNusach(nusach: Nusach) {
        context.dataStore.edit { it[PreferenceKeys.NUSACH] = nusach.name }
    }

    /** All settings as a [SettingsBackup] JSON document. */
    suspend fun exportSettingsJson(): String =
        SettingsBackup.toJson(context.dataStore.data.first().asMap().mapKeys { it.key.name })

    /** Replaces all settings with the file's contents. Returns how many were restored. */
    suspend fun importSettingsJson(json: String): Int {
        val values = SettingsBackup.fromJson(json)
        context.dataStore.edit { prefs ->
            prefs.clear()
            values.forEach { (name, v) ->
                when (v) {
                    is Boolean -> prefs[booleanPreferencesKey(name)] = v
                    is Int -> prefs[intPreferencesKey(name)] = v
                    is Long -> prefs[longPreferencesKey(name)] = v
                    is Float -> prefs[floatPreferencesKey(name)] = v
                    is Double -> prefs[doublePreferencesKey(name)] = v
                    is String -> prefs[stringPreferencesKey(name)] = v
                    is Set<*> -> prefs[stringSetPreferencesKey(name)] = v.map { it as String }.toSet()
                }
            }
        }
        return values.size
    }
}
