package com.hebrewcal.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hebrewcal.data.ChitasSchedule
import com.hebrewcal.data.ChitasSections
import com.hebrewcal.data.LegWindow
import com.hebrewcal.data.ParshaRef
import com.hebrewcal.data.SectionTitle
import com.hebrewcal.data.Reading
import com.hebrewcal.data.ReaderButtons
import com.hebrewcal.data.ReaderWindow
import com.hebrewcal.data.ReaderWindows
import com.hebrewcal.data.SefariaRepository
import com.hebrewcal.data.TehillimSchedule
import com.hebrewcal.data.Tefillot
import com.hebrewcal.data.TikkunLayout
import com.hebrewcal.data.TikkunLayoutRepository
import com.hebrewcal.data.TorahNavigation
import com.hebrewcal.data.TorahNavigationRepository
import com.hebrewcal.data.SefariaText
import com.hebrewcal.data.UserPreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.withPermit
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
        val prependedSections: Int = 0,
        /** Chitas only: one parsed text per leg (null = not fetched), and the legs' en/he headings. */
        val legs: List<SefariaText?>? = null,
        val legTitles: List<SectionTitle>? = null,
        /** Chitas only: per leg, true when the day genuinely has no portion (blank ref, not a failed fetch). */
        val legBlank: List<Boolean>? = null,
        /**
         * Chitas only, index-aligned with [legs]: each leg's growing window (null = no portion that
         * day or not fetched, so it cannot extend). Supersedes [legs] for rendering.
         */
        val legWindows: List<LegWindow?>? = null,
        /** Chitas only, per leg: (extending up, extending down). */
        val legExtending: List<Pair<Boolean, Boolean>> = emptyList(),
        /** Chitas only, per leg: (last up attempt failed, last down attempt failed), for a retry line. */
        val legFailed: List<Pair<Boolean, Boolean>> = emptyList()
    )
    data class WeekInfo(val parsha: ParshaRef? = null)
    private data class Position(val ref: String, val title: String, val aliyot: List<String>)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private val _week = MutableStateFlow(WeekInfo())
    val week: StateFlow<WeekInfo> = _week

    val textSize = prefsRepo.preferences.map { it.readerTextSizeSp }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 18f)
    val opacity = prefsRepo.preferences.map { it.readerOpacity }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 50)

    private var window: ReaderWindow? = null
    private var loadedKey: String? = null
    private var diasporaPref = true
    private var elulRefPref = ""
    private var lastTehillim: Position? = null
    private var lastTorah: Position? = null
    private var extendJob: Job? = null
    /** Chitas: one in-flight extension per leg, so the legs never wait on each other. */
    private val legJobs = HashMap<Int, Job>()
    private var loadJob: Job? = null
    private var generation = 0
    private var started = false

    /**
     * The activity's opening load, once per ViewModel: after a rotation the reader keeps
     * whatever it has navigated or scrolled to instead of reloading the intent's ref.
     */
    fun start(mode: String, ref: String, title: String, diaspora: Boolean, elulRef: String = "",
              presetAliyot: List<String> = emptyList(), advancedDate: String = "") {
        if (started) return
        started = true
        load(mode, ref, title, diaspora, elulRef, presetAliyot, advancedDate)
    }

    fun load(mode: String, ref: String, title: String, diaspora: Boolean, elulRef: String = "",
             presetAliyot: List<String> = emptyList(), advancedDate: String = "") {
        val key = "$mode|$ref|$diaspora|$elulRef|$advancedDate"
        if (loadedKey == key) return
        loadedKey = key
        diasporaPref = diaspora
        if (mode == "tehillim") elulRefPref = elulRef
        extendJob?.cancel()
        legJobs.values.forEach { it.cancel() }; legJobs.clear()
        generation++
        window = null
        _state.value = UiState(loading = true, title = title, mode = mode)
        ensureWeek()
        // A newer load supersedes one still in flight, so a slow earlier fetch cannot land last.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val resolvedRef: String
            var resolvedTitle = title
            var aliyot: List<String> = presetAliyot
            var chitasTitles: List<SectionTitle>? = null
            var tanyaFetchFailed = false
            var trimTanya = false
            var tanyaStartRef = ""
            var tanyaFromStart = false
            if (mode == "parsha" && ref.isBlank()) {
                val p = repo.currentParsha(diaspora).getOrNull()
                if (p == null) { _state.value = UiState(loading = false, error = OFFLINE, title = title, mode = mode); return@launch }
                resolvedRef = p.sefariaRef; aliyot = p.aliyot
                if (resolvedTitle.isBlank()) resolvedTitle = p.heRef
            } else if (mode == "chitas") {
                val advanced = runCatching { java.time.LocalDate.parse(advancedDate) }.getOrElse { java.time.LocalDate.now() }
                val weekly = repo.parshaFor(ChitasSchedule.parshaDateFor(advanced), diaspora).getOrNull()
                val daily = repo.parshaFor(advanced, diaspora).getOrNull()
                // Tomorrow's start ends today's lesson; if unavailable the leg shows just the start segment.
                val tomorrow = if (ref.isBlank() && !daily?.tanyaRef.isNullOrBlank())
                    repo.parshaFor(advanced.plusDays(1), diaspora).getOrNull() else null
                // Without the day's own response the Tanya leg is blanked (the Shabbat's Tanya would be the wrong day's).
                tanyaFetchFailed = weekly != null && daily == null
                val p = weekly?.let { w ->
                    daily?.let { ChitasSchedule.withDailyTanya(w, it) } ?: w.copy(tanyaRef = "", tanyaLabel = "", tanyaLabelHe = "")
                }
                if (p == null && ref.isBlank()) { _state.value = UiState(loading = false, error = OFFLINE, title = title, mode = mode); return@launch }
                val portion = p?.let {
                    val jd = com.kosherjava.zmanim.hebrewcalendar.JewishDate(advanced)
                    ChitasSchedule.portionFor(advanced, jd.jewishDayOfMonth, jd.daysInJewishMonth, it)
                }
                // Headings are built however the ref was obtained; a section without one could not be unfolded.
                chitasTitles = if (portion != null) {
                    val en = ChitasSchedule.legTitles(portion, false)
                    val he = ChitasSchedule.legTitles(portion, true)
                    en.indices.map { SectionTitle(en[it], he[it]) }
                } else listOf(SectionTitle("Chumash", "חומש"), SectionTitle("Tehillim", "תהילים"), SectionTitle("Tanya", "תניא"))
                val tanyaLeg = p?.let { ChitasSchedule.tanyaLegRef(it, tomorrow) }
                tanyaStartRef = p?.tanyaRef.orEmpty()
                trimTanya = ref.isBlank() && tanyaLeg?.trimLast == true
                resolvedRef = if (ref.isNotBlank()) ref else listOf(portion!!.chumashRef, portion.tehillimRef, tanyaLeg!!.ref)
                    .joinToString(Tefillot.REF_SEPARATOR)
                if (resolvedTitle.isBlank()) resolvedTitle = "Chitas"
            } else resolvedRef = ref
            val bookNameEn = if (mode != "tehillim" && mode != "chitas") resolvedRef.substringBefore('.', "").ifBlank { null } else null
            val chitas = mode == "chitas"
            if (chitas) {
                var legs = fetchLegs(resolvedRef, cacheFirst = false)
                // The range could not be had (offline with only the start segment cached, a failed
                // request): fall back to the start segment, untrimmed, as before the range existed.
                if (trimTanya && legs != null && legs.getOrNull(2) == null && tanyaStartRef.isNotBlank()) {
                    val start = repo.fetchText(tanyaStartRef).getOrNull()
                        ?.takeIf { t -> t.chapters.any { it.verses.isNotEmpty() } }
                    if (start != null) { legs = legs.toMutableList().also { it[2] = start }; tanyaFromStart = true }
                    trimTanya = false
                }
                legs = legs?.let { ChitasSections.legsAfterTrim(it, trimTanya) }
                if (legs == null) {
                    _state.value = UiState(false, OFFLINE, resolvedTitle, null, mode)
                } else {
                    window = null // three separate texts, not one range to keep reading through
                    val legRefs = ChitasSections.splitRefs(resolvedRef)
                    // Each leg grows on its own; the start-segment fallback is windowed by its own ref, untrimmed.
                    val windows = legs.mapIndexed { i, t ->
                        val r = if (i == 2 && tanyaFromStart) tanyaStartRef else legRefs.getOrElse(i) { "" }
                        if (t == null || r.isBlank()) null else LegWindow.start(r, t, trimmed = i == 2 && trimTanya)
                    }
                    _state.value = UiState(false, null, resolvedTitle, SefariaText(resolvedTitle, emptyList()), mode,
                        legs = legs, legTitles = chitasTitles,
                        legBlank = legRefs.mapIndexed { i, r ->
                            r.isEmpty() && !(i == 2 && tanyaFetchFailed)
                        },
                        legWindows = windows,
                        legExtending = legs.map { false to false },
                        legFailed = legs.map { false to false })
                }
                return@launch
            }
            fetchCombined(resolvedRef, cacheFirst = mode == "tefillah").fold(
                onSuccess = { text ->
                    // A Chitas day is three separate texts, not one range to keep reading through.
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

    /**
     * A ref may be several Sefaria refs joined with [Tefillot.REF_SEPARATOR] (a prayer
     * service and its conclusion); they are fetched in order and shown as one text,
     * sections renumbered so every verse key stays unique.
     */
    private suspend fun fetchCombined(ref: String, cacheFirst: Boolean): Result<SefariaText> {
        val refs = repo.expandRefs(ref).getOrElse { return Result.failure(it) }
        if (refs.size == 1 && !cacheFirst) return repo.fetchText(refs[0])
        // A service can be ~100 sections; fetch a few at a time and keep the order. Prayers are
        // read cache first, so once downloaded they open offline.
        val gate = kotlinx.coroutines.sync.Semaphore(8)
        val texts = kotlinx.coroutines.coroutineScope {
            refs.map { r ->
                async { gate.withPermit { if (cacheFirst) repo.fetchTextCacheFirst(r) else repo.fetchText(r) } }
            }.map { it.await().getOrElse { e -> return@coroutineScope Result.failure<List<SefariaText>>(e) } }
                .let { Result.success(it) }
        }.getOrElse { return Result.failure(it) }
        // One chapter per part of the service (consecutive siddur sections merged into its main
        // parts), so the reader can fold and title each one.
        val units = com.hebrewcal.data.PrayerSections.units(refs, texts.map { t -> t.chapters.flatMap { it.verses } })
        val chapters = units.mapIndexed { i, u ->
            com.hebrewcal.data.Chapter(i + 1, u.verses.mapIndexed { k, v -> com.hebrewcal.data.Verse(k + 1, v.he, v.en) })
        }
        val titles = units.map { u ->
            u.title ?: com.hebrewcal.data.SefariaRefs.sectionTitle(refs[u.firstRef], texts[u.firstRef].heRef)
        }
        return Result.success(SefariaText(texts.first().heRef, chapters, titles))
    }

    /**
     * Refs joined with [Tefillot.REF_SEPARATOR], each kept as its own leg (verses and chapter
     * numbers untouched). A blank or failed leg is null; null overall when every leg failed.
     */
    private suspend fun fetchLegs(ref: String, cacheFirst: Boolean): List<SefariaText?>? {
        val legs = kotlinx.coroutines.coroutineScope {
            ChitasSections.splitRefs(ref).map { r ->
                async {
                    if (r.isEmpty()) null
                    else (if (cacheFirst) repo.fetchTextCacheFirst(r) else repo.fetchText(r)).getOrNull()
                        ?.takeIf { t -> t.chapters.any { it.verses.isNotEmpty() } }
                }
            }.map { it.await() }
        }
        return legs.takeIf { l -> l.any { it != null } }
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
        val start = window ?: return
        // A prayer is one complete text, not a chapter in a book to keep reading through.
        if (s.mode == "tefillah") return
        if (s.loading || s.error != null || extendJob?.isActive == true) return
        if ((if (down) ReaderWindows.nextFetch(start) else ReaderWindows.prevFetch(start)) == null) return
        val gen = generation
        _state.value = s.copy(extendingDown = down, extendingUp = !down, prependedSections = 0)
        extendJob = viewModelScope.launch {
            var w = start
            var fetchFailed = false
            // ReaderWindows marks any verse-bounded edge partial, because a chapter's true
            // length is unknown offline. When the ref happened to stop on the chapter's last
            // verse (Genesis.6.9-11.32 — chapter 11 really does end at 32), the refetch
            // replaces that chapter with identical content: nothing grows and the gesture
            // would leave a spinner and no new text. The replace still clears the partial
            // flag, so we immediately continue to the genuinely next chapter in the same
            // job — at most one extra round, so one gesture always yields new text.
            var rounds = 0
            while (rounds < 2) {
                rounds++
                val (book, chNum) = (if (down) ReaderWindows.nextFetch(w) else ReaderWindows.prevFetch(w)) ?: break
                val fetched = repo.fetchText("$book.$chNum").getOrNull()?.chapters?.firstOrNull()
                if (gen != generation) return@launch
                if (fetched == null || fetched.verses.isEmpty()) { fetchFailed = true; break }
                val prevChapters = w.chapters.size
                val prevEdgeVerses = (if (down) w.chapters.lastOrNull() else w.chapters.firstOrNull())?.verses?.size ?: 0
                val grown = if (down) ReaderWindows.appended(w, book, fetched) else ReaderWindows.prepended(w, book, fetched)
                if (gen != generation) return@launch
                window = grown
                val newEdgeVerses = (if (down) grown.chapters.lastOrNull() else grown.chapters.firstOrNull())?.verses?.size ?: 0
                val grewNothing = grown.chapters.size == prevChapters && newEdgeVerses <= prevEdgeVerses
                w = grown
                if (grewNothing) continue
                // The window really changed; let an explicit reload of the same key (e.g. the
                // banner's "this week's parsha" tap) rebuild it instead of short-circuiting.
                loadedKey = null
                break
            }
            if (gen != generation) return@launch
            val cur = _state.value
            if (fetchFailed && w === start) {
                _state.value = cur.copy(extendingDown = false, extendingUp = false); return@launch
            }
            val text = SefariaText(cur.text?.heRef ?: "", w.chapters)
            val tikkun = if (cur.mode == "parsha") ReaderWindows.torahRange(w)?.let { TikkunLayout.parseTorahRef(it) }?.let { tikkunRepo.linesFor(it) } else cur.tikkunLines
            val added = w.chapters.size - start.chapters.size
            _state.value = cur.copy(
                text = text, tikkunLines = tikkun, bookNameEn = if (down) w.bookEn else cur.bookNameEn,
                extendingDown = false, extendingUp = false, prependedSections = if (down) 0 else added
            )
        }
    }

    /**
     * Chitas: grows leg [index] by one step up or down. A step may need two fetches (completing
     * an edge chapter that turns out already complete, then the neighbouring chapter), so it keeps
     * going until the leg has gained a block, the book ends, or [MAX_LEG_FETCHES] is reached. A
     * failed fetch stops it and marks that leg and direction failed; nothing is retried here.
     */
    fun extendLeg(index: Int, down: Boolean) {
        val s = _state.value
        if (s.mode != "chitas" || s.loading) return
        val start = s.legWindows?.getOrNull(index) ?: return
        if (legJobs[index]?.isActive == true) return
        if (start.nextFetch(down) == null) return
        val gen = generation
        _state.value = s.copy(
            legExtending = s.legExtending.withSide(index, down, true),
            legFailed = s.legFailed.withSide(index, down, false)
        )
        legJobs[index] = viewModelScope.launch {
            var w = start
            var failed = false
            var fetches = 0
            while (fetches < MAX_LEG_FETCHES) {
                val ref = w.nextFetch(down) ?: break
                fetches++
                val fetched = repo.fetchText(ref).getOrNull()?.takeIf { t -> t.chapters.any { it.verses.isNotEmpty() } }
                if (gen != generation) return@launch
                if (fetched == null) { failed = true; break }
                val next = w.applied(down, ref, fetched)
                // The response lacked the chapter asked for: report it rather than stop silently,
                // or the trigger stays live and refetches on every scroll for nothing.
                if (next === w) { failed = true; break }
                val grew = next.blocksAdded(w) > 0
                w = next
                if (grew) break
            }
            if (gen != generation) return@launch
            val cur = _state.value
            // Only this leg's job writes this leg's window, so replacing it cannot lose another update.
            // Edge facts learnt before a failure (a chapter found complete, its neighbour) are kept;
            // they add no visible text and spare the retry a fetch.
            val windows = cur.legWindows?.toMutableList()?.also { it[index] = w }
            _state.value = cur.copy(
                legWindows = windows,
                legExtending = cur.legExtending.withSide(index, down, false),
                legFailed = cur.legFailed.withSide(index, down, failed)
            )
        }
    }

    private fun List<Pair<Boolean, Boolean>>.withSide(i: Int, down: Boolean, v: Boolean) =
        mapIndexed { k, p -> if (k != i) p else if (down) p.first to v else v to p.second }

    fun consumedPrepend() { _state.value = _state.value.copy(prependedSections = 0) }

    fun setTextSize(sp: Float) = viewModelScope.launch { prefsRepo.updateReaderTextSize(sp) }

    companion object {
        const val OFFLINE = "offline"
        /** Per extension gesture: complete the edge chapter, then its neighbour, with slack. */
        private const val MAX_LEG_FETCHES = 4
    }
}
