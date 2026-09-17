package com.hebrewcal.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hebrewcal.data.ParshaRef
import com.hebrewcal.data.Reading
import com.hebrewcal.data.ReaderButtons
import com.hebrewcal.data.ReaderWindow
import com.hebrewcal.data.ReaderWindows
import com.hebrewcal.data.SefariaRepository
import com.hebrewcal.data.TehillimSchedule
import com.hebrewcal.data.TikkunLayout
import com.hebrewcal.data.TikkunLayoutRepository
import com.hebrewcal.data.TorahNavigation
import com.hebrewcal.data.TorahNavigationRepository
import com.hebrewcal.data.SefariaText
import com.hebrewcal.data.UserPreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReaderViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SefariaRepository(app)
    private val tikkunRepo = TikkunLayoutRepository(app)
    private val navRepo = TorahNavigationRepository(app)

    /** Parsha lists per Torah book for the navigation dial (loaded on demand). */
    val parshiyot = MutableStateFlow<Map<String, List<TorahNavigation.Parsha>>>(emptyMap())
    fun ensureParshiyot(bookEn: String) {
        if (parshiyot.value.containsKey(bookEn)) return
        viewModelScope.launch {
            val list = navRepo.parshiyot(bookEn)
            parshiyot.value = parshiyot.value + (bookEn to list)
        }
    }

    private val prefsRepo = UserPreferencesRepository(app)

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val title: String = "",
        val text: SefariaText? = null,
        val mode: String = "tehillim",
        val bookNameEn: String? = null,
        val aliyot: List<String> = emptyList(),
        val elulText: SefariaText? = null,
        val tikkunLines: List<TikkunLayout.Line>? = null,
        val extendingUp: Boolean = false,
        val extendingDown: Boolean = false,
        /** Sections added at the top by the last prepend; the UI consumes this to keep its scroll still. */
        val prependedSections: Int = 0
    )
    data class WeekInfo(val parsha: ParshaRef? = null)
    private data class Position(val ref: String, val title: String, val aliyot: List<String>)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private val _week = MutableStateFlow(WeekInfo())
    val week: StateFlow<WeekInfo> = _week

    val textSize = prefsRepo.preferences.map { it.readerTextSizeSp }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 18f)

    private var window: ReaderWindow? = null
    private var loadedKey: String? = null
    private var diasporaPref = true
    private var elulRefPref = ""
    private var lastTehillim: Position? = null
    private var lastTorah: Position? = null
    private var extendJob: Job? = null
    private var generation = 0

    fun load(mode: String, ref: String, title: String, diaspora: Boolean, elulRef: String = "",
             presetAliyot: List<String> = emptyList()) {
        val key = "$mode|$ref|$diaspora|$elulRef"
        if (loadedKey == key) return
        loadedKey = key
        diasporaPref = diaspora
        if (mode == "tehillim") elulRefPref = elulRef
        extendJob?.cancel()
        generation++
        window = null
        _state.value = UiState(loading = true, title = title, mode = mode)
        ensureWeek()
        viewModelScope.launch {
            val resolvedRef: String
            var resolvedTitle = title
            var aliyot: List<String> = presetAliyot
            if (mode == "parsha" && ref.isBlank()) {
                val p = repo.currentParsha(diaspora).getOrNull()
                if (p == null) { _state.value = UiState(loading = false, error = OFFLINE, title = title, mode = mode); return@launch }
                resolvedRef = p.sefariaRef; aliyot = p.aliyot
                if (resolvedTitle.isBlank()) resolvedTitle = p.heRef
            } else resolvedRef = ref
            val bookNameEn = if (mode != "tehillim") resolvedRef.substringBefore('.', "").ifBlank { null } else null
            repo.fetchText(resolvedRef).fold(
                onSuccess = { text ->
                    val w = ReaderWindows.fromRef(resolvedRef, text); window = w
                    val elulText = if (mode == "tehillim" && elulRef.isNotBlank()) repo.fetchText(elulRef).getOrNull() else null
                    val tikkunLines = if (mode == "parsha") TikkunLayout.parseTorahRef(resolvedRef)?.let { tikkunRepo.linesFor(it) } else null
                    val finalTitle = resolvedTitle.ifBlank { text.heRef }
                    val pos = Position(resolvedRef, finalTitle, aliyot)
                    if (mode == "tehillim") lastTehillim = pos else if (mode == "parsha") lastTorah = pos
                    _state.value = UiState(false, null, finalTitle, text, mode, bookNameEn, aliyot, elulText, tikkunLines)
                },
                onFailure = { _state.value = UiState(false, OFFLINE, resolvedTitle, null, mode, bookNameEn, aliyot) }
            )
        }
    }

    /** Jump to any Tehillim chapter or Torah chapter/parsha from the navigation dial. */
    fun navigateTo(mode: String, ref: String, title: String, aliyot: List<String> = emptyList()) =
        load(mode, ref, title, diaspora = diasporaPref, elulRef = if (mode == "tehillim") elulRefPref else "", presetAliyot = aliyot)

    /** Tehillim ⇄ Torah: the last place visited in that mode this session, else today's / this week's. */
    fun toggleMode(hebrew: Boolean) {
        val s = _state.value
        if (s.mode == "tehillim") {
            val p = lastTorah
            if (p != null) navigateTo("parsha", p.ref, p.title, p.aliyot)
            else load("parsha", "", if (hebrew) "פרשת השבוע" else "Weekly Parsha", diasporaPref)
        } else {
            val p = lastTehillim
            if (p != null) navigateTo("tehillim", p.ref, p.title)
            else {
                val jd = com.kosherjava.zmanim.hebrewcalendar.JewishDate()
                val portion = TehillimSchedule.portionFor(jd.jewishDayOfMonth, jd.daysInJewishMonth)
                navigateTo("tehillim", portion.sefariaRef, ReaderButtons.tehillimLabel(portion, hebrew))
            }
        }
    }

    fun openHaftarah(r: Reading, hebrew: Boolean) =
        load("haftarah", r.dottedRef, (if (hebrew) "הפטרה · " else "Haftarah · ") + (if (hebrew) r.nameHe else r.nameEn), diasporaPref)

    private fun ensureWeek() {
        if (_week.value.parsha != null) return
        viewModelScope.launch { repo.currentParsha(diasporaPref).getOrNull()?.let { _week.value = WeekInfo(it) } }
    }

    fun extendDown() = extend(down = true)
    fun extendUp() = extend(down = false)

    private fun extend(down: Boolean) {
        val s = _state.value
        val w = window ?: return
        if (s.loading || s.error != null || extendJob?.isActive == true) return
        val target = (if (down) ReaderWindows.nextFetch(w) else ReaderWindows.prevFetch(w)) ?: return
        val gen = generation
        _state.value = s.copy(extendingDown = down, extendingUp = !down, prependedSections = 0)
        extendJob = viewModelScope.launch {
            val (book, chNum) = target
            val fetched = repo.fetchText("$book.$chNum").getOrNull()?.chapters?.firstOrNull()
            if (gen != generation) return@launch
            val cur = _state.value
            if (fetched == null || fetched.verses.isEmpty()) {
                _state.value = cur.copy(extendingDown = false, extendingUp = false); return@launch
            }
            val grown = if (down) ReaderWindows.appended(w, book, fetched) else ReaderWindows.prepended(w, book, fetched)
            window = grown
            val text = SefariaText(cur.text?.heRef ?: "", grown.chapters)
            val tikkun = if (cur.mode == "parsha") ReaderWindows.torahRange(grown)?.let { TikkunLayout.parseTorahRef(it) }?.let { tikkunRepo.linesFor(it) } else cur.tikkunLines
            val added = grown.chapters.size - w.chapters.size
            _state.value = cur.copy(
                text = text, tikkunLines = tikkun, bookNameEn = if (down) grown.bookEn else cur.bookNameEn,
                extendingDown = false, extendingUp = false, prependedSections = if (down) 0 else added
            )
        }
    }

    fun consumedPrepend() { _state.value = _state.value.copy(prependedSections = 0) }

    fun setTextSize(sp: Float) = viewModelScope.launch { prefsRepo.updateReaderTextSize(sp) }

    companion object {
        const val OFFLINE = "offline"
    }
}
