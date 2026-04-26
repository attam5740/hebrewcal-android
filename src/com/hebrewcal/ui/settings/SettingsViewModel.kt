package com.hebrewcal.ui.settings

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hebrewcal.data.*
import com.hebrewcal.service.CalendarNotificationService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefsRepo = UserPreferencesRepository(app)
    private val locationHelper = LocationHelper(app)

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val hasLocationPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            getApplication(), android.Manifest.permission.ACCESS_FINE_LOCATION
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

    fun setShowParsha(show: Boolean) = viewModelScope.launch {
        prefsRepo.updateShowParsha(show)
        notifyServiceRefresh()
    }

    fun setShowGregorian(show: Boolean) = viewModelScope.launch {
        prefsRepo.updateShowGregorianDate(show)
        notifyServiceRefresh()
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

    fun geocodeAndSaveCity(city: String) = viewModelScope.launch {
        _geocodeStatus.value = GeoCodeStatus.Loading
        val result = locationHelper.geocodeCity(city)
        if (result != null) {
            prefsRepo.updateZmanimManualLocation(city, result.latitude, result.longitude)
            _geocodeStatus.value = GeoCodeStatus.Success(city)
            notifyServiceRefresh()
        } else {
            _geocodeStatus.value = GeoCodeStatus.Error("City not found. Try a different spelling.")
        }
    }

    fun toggleZman(key: String, selected: Boolean) = viewModelScope.launch {
        val current = prefsRepo.preferences.first().selectedZmanim.toMutableSet()
        if (selected) current.add(key) else current.remove(key)
        prefsRepo.updateSelectedZmanim(current)
        notifyServiceRefresh()
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
