package com.hebrewcal.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hebrewcal.data.SefariaRepository
import com.hebrewcal.data.SefariaText
import com.hebrewcal.data.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReaderViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SefariaRepository(app)
    private val prefsRepo = UserPreferencesRepository(app)

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val title: String = "",
        val text: SefariaText? = null,
        val mode: String = "tehillim",
        val bookNameEn: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    val textSize = prefsRepo.preferences.map { it.readerTextSizeSp }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 18f)

    private var loadedKey: String? = null

    fun load(mode: String, ref: String, title: String, diaspora: Boolean) {
        val key = "$mode|$ref|$diaspora"
        if (loadedKey == key) return
        loadedKey = key
        _state.value = UiState(loading = true, title = title, mode = mode)
        viewModelScope.launch {
            val resolvedRef: String
            var resolvedTitle = title
            if (mode == "parsha" && ref.isBlank()) {
                val pr = repo.currentParsha(diaspora)
                val p = pr.getOrNull()
                if (p == null) {
                    _state.value = UiState(loading = false, error = OFFLINE, title = title)
                    return@launch
                }
                resolvedRef = p.sefariaRef
                if (resolvedTitle.isBlank()) resolvedTitle = p.heRef
            } else {
                resolvedRef = ref
            }
            val bookNameEn = if (mode == "parsha") resolvedRef.substringBefore('.', "").ifBlank { null } else null
            val res = repo.fetchText(resolvedRef)
            res.fold(
                onSuccess = { text ->
                    _state.value = UiState(false, null, resolvedTitle.ifBlank { text.heRef }, text, mode, bookNameEn)
                },
                onFailure = { _state.value = UiState(false, OFFLINE, resolvedTitle, null, mode, bookNameEn) }
            )
        }
    }

    fun setTextSize(sp: Float) = viewModelScope.launch { prefsRepo.updateReaderTextSize(sp) }

    companion object {
        const val OFFLINE = "offline"
    }
}
