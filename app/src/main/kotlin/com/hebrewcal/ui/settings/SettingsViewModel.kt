package com.hebrewcal.ui.settings

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hebrewcal.data.*
import com.hebrewcal.service.CalendarNotificationService
import com.hebrewcal.service.TextPrefetch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefsRepo = UserPreferencesRepository(app)
    private val locationHelper by lazy { LocationHelper(getApplication()) }

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val hasLocationPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            getApplication(), android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private val _geocodeStatus = MutableStateFlow<GeoCodeStatus>(GeoCodeStatus.Idle)
    val geocodeStatus: StateFlow<GeoCodeStatus> = _geocodeStatus

    fun setLanguage(lang: CalendarLanguage) = viewModelScope.launch {
        prefsRepo.updateLanguage(lang)
        notifyServiceRefresh()
    }

    fun setLocation(loc: CalendarLocation) = viewModelScope.launch {
        prefsRepo.updateLocation(loc)
        notifyServiceRefresh()
    }

    fun setNusach(nusach: Nusach) = viewModelScope.launch {
        prefsRepo.updateNusach(nusach)
        notifyServiceRefresh()
    }

    fun setPrayerButtonsOn(on: Boolean) = viewModelScope.launch {
        prefsRepo.updatePrayerButtonsOn(on)
        notifyServiceRefresh()
    }

    fun setPrayerButton(key: String, on: Boolean) = viewModelScope.launch {
        prefsRepo.updatePrayerButton(key, on)
        notifyServiceRefresh()
    }

    fun setShowParsha(show: Boolean) = viewModelScope.launch {
        prefsRepo.updateShowParsha(show)
        notifyServiceRefresh()
    }

    fun setShowParshaOnWeekdays(show: Boolean) = viewModelScope.launch {
        prefsRepo.updateShowParshaOnWeekdays(show)
        notifyServiceRefresh()
    }

    fun setShowOmer(show: Boolean) = viewModelScope.launch {
        prefsRepo.updateShowOmer(show)
        notifyServiceRefresh()
    }

    fun setShowGregorian(show: Boolean) = viewModelScope.launch {
        prefsRepo.updateShowGregorianDate(show)
        notifyServiceRefresh()
    }

    fun setShowTehillimButton(show: Boolean) = viewModelScope.launch {
        prefsRepo.updateShowTehillimButton(show)
        notifyServiceRefresh()
    }

    fun setShowParshaButton(show: Boolean) = viewModelScope.launch {
        prefsRepo.updateShowParshaButton(show)
        notifyServiceRefresh()
    }

    fun setShowElulTehillim(show: Boolean) = viewModelScope.launch {
        prefsRepo.updateShowElulTehillim(show)
        notifyServiceRefresh()
    }

    fun setPrefetchNetwork(network: PrefetchNetwork) = viewModelScope.launch {
        prefsRepo.updatePrefetchNetwork(network)
        TextPrefetch.schedule(getApplication(), network, runNow = true)
    }

    fun setPrefetchTehillimDays(days: Int) = viewModelScope.launch {
        prefsRepo.updatePrefetchTehillimDays(days)
        reschedulePrefetch()
    }

    fun setPrefetchParshaWeeks(weeks: Int) = viewModelScope.launch {
        prefsRepo.updatePrefetchParshaWeeks(weeks)
        reschedulePrefetch()
    }

    fun setWidgetOpacity(percent: Int) = viewModelScope.launch {
        prefsRepo.updateWidgetOpacity(percent)
        notifyServiceRefresh()   // the service refresh also redraws the widget
    }

    private suspend fun reschedulePrefetch() {
        val network = prefsRepo.preferences.first().prefetchNetwork
        TextPrefetch.schedule(getApplication(), network, runNow = true)
    }

    fun setShowZmanim(show: Boolean) = viewModelScope.launch {
        prefsRepo.updateShowZmanim(show)
        notifyServiceRefresh()
    }

    fun setZmanimTimeFormat(format: ZmanimTimeFormat) = viewModelScope.launch {
        prefsRepo.updateZmanimTimeFormat(format)
        notifyServiceRefresh()
    }

    fun setZmanimLocationSource(source: ZmanimLocationSource) = viewModelScope.launch {
        prefsRepo.updateZmanimLocationSource(source)
        notifyServiceRefresh()
    }

    fun selectCity(city: CityData) = viewModelScope.launch {
        prefsRepo.updateZmanimManualLocation(city.name, city.latitude, city.longitude)
        _geocodeStatus.value = GeoCodeStatus.Success(city.displayName)
        notifyServiceRefresh()
    }

    fun detectNearestCity() = viewModelScope.launch {
        _geocodeStatus.value = GeoCodeStatus.Loading
        try {
            val loc = locationHelper.getCurrentLocation()
            val nearest = CityDatabase.findNearest(loc.latitude, loc.longitude)
            prefsRepo.updateZmanimManualLocation(nearest.name, nearest.latitude, nearest.longitude)
            _geocodeStatus.value = GeoCodeStatus.Success(nearest.displayName)
            notifyServiceRefresh()
        } catch (e: Exception) {
            _geocodeStatus.value = GeoCodeStatus.Error("Could not get GPS location.")
        }
    }

    fun toggleZman(key: String, selected: Boolean) = viewModelScope.launch {
        val current = prefsRepo.preferences.first().selectedZmanim.toMutableSet()
        if (selected) current.add(key) else current.remove(key)
        prefsRepo.updateSelectedZmanim(current)
        notifyServiceRefresh()
    }

    fun setCandleLightingOffset(offsetMinutes: Int) = viewModelScope.launch {
        prefsRepo.updateCandleLightingOffset(offsetMinutes)
        notifyServiceRefresh()
    }

    fun setHavdalahOpinion(opinion: HavdalahOpinion) = viewModelScope.launch {
        prefsRepo.updateHavdalahOpinion(opinion)
        notifyServiceRefresh()
    }

    fun setShowShabbatBanner(show: Boolean) = viewModelScope.launch {
        prefsRepo.updateShowShabbatBanner(show)
        notifyServiceRefresh()
    }

    fun setShowCelestialArc(show: Boolean) = viewModelScope.launch {
        prefsRepo.updateShowCelestialArc(show)
        notifyServiceRefresh()
    }

    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus

    fun exportSettings(uri: Uri) = viewModelScope.launch {
        _backupStatus.value = try {
            withContext(Dispatchers.IO) {
                val json = prefsRepo.exportSettingsJson()
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!
                    .use { it.write(json.toByteArray()) }
            }
            "Settings saved"
        } catch (e: Exception) {
            "Couldn't save settings"
        }
    }

    fun importSettings(uri: Uri) = viewModelScope.launch {
        _backupStatus.value = try {
            val n = withContext(Dispatchers.IO) {
                val json = getApplication<Application>().contentResolver.openInputStream(uri)!!
                    .use { it.readBytes().decodeToString() }
                prefsRepo.importSettingsJson(json)
            }
            notifyServiceRefresh()
            "Restored $n settings"
        } catch (e: Exception) {
            "Couldn't restore: not a Hebrew Calendar settings file"
        }
    }

    private fun notifyServiceRefresh() {
        CalendarNotificationService.refresh(getApplication())
    }

    sealed class GeoCodeStatus {
        object Idle : GeoCodeStatus()
        object Loading : GeoCodeStatus()
        data class Success(val city: String) : GeoCodeStatus()
        data class Error(val message: String) : GeoCodeStatus()
    }
}
