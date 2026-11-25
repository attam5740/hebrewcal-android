package com.hebrewcal.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.geometry.CornerRadius
import com.hebrewcal.R
import com.hebrewcal.data.AliyotPartitioner
import com.hebrewcal.data.LegBlock
import com.hebrewcal.data.LegWindow
import com.hebrewcal.data.SectionTitle
import com.hebrewcal.data.Instructions
import com.hebrewcal.data.CalendarLanguage
import com.hebrewcal.data.HebrewVocalization
import com.hebrewcal.data.ParshaRef
import com.hebrewcal.data.Reading
import com.hebrewcal.data.SefariaText
import com.hebrewcal.data.TikkunLayout
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop

/** Text rendering modes: English, Hebrew, or Tikkun (unvocalized STaM sofer script). */
private const val MODE_EN = "EN"
private const val MODE_HE = "HE"
private const val MODE_TIKKUN = "TIKKUN"

class TextReaderActivity : ComponentActivity() {
    private val vm: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: "tehillim"
        val ref = intent.getStringExtra(EXTRA_REF) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val diaspora = intent.getBooleanExtra(EXTRA_DIASPORA, true)
        val textMode0 = if (intent.getStringExtra(EXTRA_LANG) == CalendarLanguage.HEBREW.name) MODE_HE else MODE_EN
        val nikkud0 = intent.getBooleanExtra(EXTRA_NIKKUD, true)
        val teamim0 = intent.getBooleanExtra(EXTRA_TEAMIM, false)
        val elulRef = intent.getStringExtra(EXTRA_ELUL_REF) ?: ""
        val aliyot = intent.getStringArrayListExtra(EXTRA_ALIYOT)?.toList() ?: emptyList()
        val advancedDate = intent.getStringExtra(EXTRA_ADVANCED_DATE) ?: ""
        vm.start(mode, ref, title, diaspora, elulRef, presetAliyot = aliyot, advancedDate = advancedDate)

        setContent {
            val state by vm.state.collectAsState()
            val persistedSize by vm.textSize.collectAsState()
            val opacity by vm.opacity.collectAsState()
            var textMode by rememberSaveable { mutableStateOf(textMode0) }
            var nikkud by rememberSaveable { mutableStateOf(nikkud0) }
            var teamim by rememberSaveable { mutableStateOf(teamim0) }
            var byAliyot by rememberSaveable { mutableStateOf(false) }
            var showNav by rememberSaveable { mutableStateOf(false) }
            var barExpanded by rememberSaveable { mutableStateOf(false) }
            val contentInteractionSource = remember { MutableInteractionSource() }
            val week by vm.week.collectAsState()
            var showHaftarahChooser by rememberSaveable { mutableStateOf(false) }
            val hebrewUi = textMode != MODE_EN
            // One haftarah opens straight away; a festival week with several asks first.
            fun haftarah() {
                val list = week.parsha?.haftarot ?: return
                when (list.size) {
                    0 -> {}
                    1 -> vm.openHaftarah(list[0], hebrewUi)
                    else -> showHaftarahChooser = true
                }
            }

            // A haftarah is chanted, so it wants te'amim; the scroll layout has no haftarot.
            // Force only on the false→true transition (never on activity recreation, where
            // state.mode is already "haftarah" and inHaftarah is already true), and restore
            // the reader's own settings when leaving haftarah mode.
            var inHaftarah by rememberSaveable { mutableStateOf(false) }
            var savedNikkud by rememberSaveable { mutableStateOf(nikkud0) }
            var savedTeamim by rememberSaveable { mutableStateOf(teamim0) }
            var savedTextMode by rememberSaveable { mutableStateOf(textMode0) }
            LaunchedEffect(state.mode) {
                val enteringHaftarah = state.mode == "haftarah"
                if (enteringHaftarah && !inHaftarah) {
                    savedNikkud = nikkud; savedTeamim = teamim; savedTextMode = textMode
                    nikkud = true; teamim = true
                    if (textMode == MODE_TIKKUN) textMode = MODE_HE
                    inHaftarah = true
                } else if (!enteringHaftarah && inHaftarah) {
                    nikkud = savedNikkud; teamim = savedTeamim; textMode = savedTextMode
                    inHaftarah = false
                }
            }

            // Live text size driven by pinch-to-zoom; persisted (debounced) to DataStore.
            var textSize by remember { mutableStateOf(persistedSize) }
            LaunchedEffect(persistedSize) {
                if (textSize == 18f && persistedSize != 18f) textSize = persistedSize
            }
            LaunchedEffect(Unit) {
                snapshotFlow { textSize }.drop(1).collectLatest {
                    delay(500)
                    vm.setTextSize(it)
                }
            }

            val chitasReturn by vm.chitasReturn.collectAsState()
            val tanyaToday by vm.tanyaToday.collectAsState()
            // Chitas: which part (0 Chumash, 1 Tehilim, 2 Tanya) the reader shows.
            var chitasPart by rememberSaveable { mutableStateOf(0) }

            Box(
                Modifier.fillMaxSize()
                    .background(Color(0xFF2E2E2E).copy(alpha = opacity / 100f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { finish() }, // tap scrim to dismiss
                contentAlignment = Alignment.Center
            ) {
                // Content is 92% height so ~4% scrim strips remain tappable top/bottom to dismiss.
                Column(
                    Modifier.fillMaxWidth().fillMaxHeight(0.92f).align(Alignment.Center).clickable(
                        interactionSource = contentInteractionSource,
                        indication = null
                    ) {}
                ) {
                    ReaderBar(
                        title = state.title,
                        expanded = barExpanded,
                        onToggleExpanded = { barExpanded = !barExpanded },
                        textMode = textMode,
                        nikkud = nikkud, teamim = teamim,
                        showAliyotToggle = state.mode == "parsha" && state.aliyot.isNotEmpty(),
                        byAliyot = byAliyot,
                        mode = state.mode,
                        torahSide = state.mode != "tehillim",
                        chitasMode = state.mode == "chitas" || state.mode == "tanya",
                        backLabel = if (chitasReturn != null) (if (textMode != MODE_EN) "‹ חת״ת" else "‹ Chitas") else null,
                        onBack = { vm.returnToChitas() },
                        onToggleMode = { vm.toggleMode(hebrewUi) },
                        onNav = { showNav = true },
                        onTextMode = { m ->
                            textMode = m
                        },
                        onNikkud = { nikkud = !nikkud; if (!nikkud) teamim = false },
                        onTeamim = { teamim = !teamim; if (teamim) nikkud = true },
                        onAliyot = { byAliyot = !byAliyot },
                        onClose = { finish() }
                    )
                    if (state.mode == "chitas") Box(Modifier.fillMaxWidth().background(Color(0x73181818))) {
                        ChitasToggle(selected = chitasPart, hebrew = textMode != MODE_EN, onSelect = { chitasPart = it })
                    }
                    WeekBanner(
                        week.parsha, hebrewUi,
                        onParsha = { vm.load("parsha", "", if (hebrewUi) "פרשת השבוע" else "Weekly Parsha", diaspora) },
                        onHaftarah = { haftarah() }
                    )
                    when {
                        state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                        state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(if (textMode != MODE_EN) "אין חיבור לאינטרנט" else "No internet connection", color = Color.White)
                        }
                        state.text != null -> ReaderBody(
                            text = state.text!!, textMode = textMode, nikkud = nikkud, teamim = teamim,
                            sizeSp = textSize, mode = state.mode, bookNameEn = state.bookNameEn,
                            aliyot = if (byAliyot) state.aliyot else emptyList(),
                            elulText = state.elulText,
                            legs = state.legs, legTitles = state.legTitles, legBlank = state.legBlank,
                            legWindows = state.legWindows, legExtending = state.legExtending,
                            legFailed = state.legFailed,
                            chitasPart = chitasPart,
                            onExtendLeg = { i, down -> vm.extendLeg(i, down) },
                            tikkunLines = state.tikkunLines,
                            extendingUp = state.extendingUp,
                            extendingDown = state.extendingDown,
                            prependedSections = state.prependedSections,
                            onExtendUp = { vm.extendUp() },
                            onExtendDown = { vm.extendDown() },
                            onPrependConsumed = { vm.consumedPrepend() },
                            onZoom = { factor -> textSize = (textSize * factor).coerceIn(14f, 40f) }
                        )
                    }
                }
                if (showNav) {
                    val parshiyotMap by vm.parshiyot.collectAsState()
                    // The dial follows what is being read; in Chitas, the part the toggle is on.
                    val navKind = when (state.mode) {
                        "parsha" -> NavKind.TORAH
                        "haftarah" -> NavKind.PROPHETS
                        "tanya" -> NavKind.TANYA
                        "chitas" -> when (chitasPart) { 0 -> NavKind.TORAH; 2 -> NavKind.TANYA; else -> NavKind.TEHILLIM }
                        else -> NavKind.TEHILLIM
                    }
                    val navStart = when (navKind) {
                        NavKind.PROPHETS -> state.ref.takeIf { state.mode == "haftarah" } ?: week.parsha?.haftarot?.firstOrNull()?.dottedRef
                        NavKind.TANYA -> state.ref.takeIf { state.mode == "tanya" } ?: tanyaToday
                        else -> null
                    }
                    NavDialOverlay(
                        kind = navKind,
                        startRef = navStart,
                        hebrew = textMode != MODE_EN,
                        parshiyot = parshiyotMap,
                        ensureParshiyot = { vm.ensureParshiyot(it) },
                        onPick = { m, ref, title, aliyot -> showNav = false; vm.navigateTo(m, ref, title, aliyot) },
                        onDismiss = { showNav = false }
                    )
                }
                // Guarded rather than !!-ed: the flag is saved, the week is refetched.
                week.parsha?.let { p ->
                    if (showHaftarahChooser) HaftarahChooser(
                        p.haftarot, hebrewUi,
                        onPick = { showHaftarahChooser = false; vm.openHaftarah(it, hebrewUi) },
                        onDismiss = { showHaftarahChooser = false }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_REF = "ref"
        const val EXTRA_TITLE = "title"
        const val EXTRA_DIASPORA = "diaspora"
        const val EXTRA_LANG = "lang"
        const val EXTRA_NIKKUD = "nikkud"
        const val EXTRA_TEAMIM = "teamim"
        const val EXTRA_ELUL_REF = "elul_ref"
        const val EXTRA_ALIYOT = "aliyot"
        /** ISO-8601 nightfall-advanced Gregorian date for mode "chitas"; today when absent. */
        const val EXTRA_ADVANCED_DATE = "advanced_date"
    }
}

/** How far, in dp, the text must be pulled against its end to load the next or previous chapter. */
private const val EDGE_PULL_DP = 72

internal val ACTIVE_GOLD = Color(0xFFFFD255)   // vivid warm amber
private val ACTIVE_BG = Color(0x47FFC94D)     // stronger amber pill behind active toggles
internal val INACTIVE = Color(0xE6FFFFFF)      // near-white, clearly legible
private val DISABLED = Color(0x66FFFFFF)
private val MINT = Color(0xFF6EE7B7)         // haftarah accent (matches the banner's haftarah ref)
private val SKY = Color(0xFF7EC8E3)          // Elul supplement accent (app secondary)
private val INSTRUCTION = Color(0xFFB4B4B4)  // light grey: siddur instructions and notes

@Composable
private fun BarToggle(label: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        color = if (!enabled) DISABLED else if (active) ACTIVE_GOLD else INACTIVE,
        fontSize = 14.sp,
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active && enabled) ACTIVE_BG else Color.Transparent)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

/**
 * 56×22dp Tehillim ⇄ Torah switch: glass track, harp glyph (Tehillim) and scroll glyph
 * (Torah), flame knob. The knob's rest positions (x = 22dp / width−22dp) sit beside the
 * glyph on that side rather than on top of it, so the active glyph (bright) and the
 * inactive one (faint) both stay visible in either state.
 */
@Composable
private fun ModeToggle(torah: Boolean, onToggle: () -> Unit) {
    val knobX by animateFloatAsState(if (torah) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "knob")
    Canvas(
        Modifier.padding(horizontal = 4.dp).size(56.dp, 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggle() }
            .semantics {
                contentDescription = if (torah) "Switch to Tehillim" else "Switch to Torah"
                role = Role.Switch
            }
    ) {
        val h = size.height; val r = h / 2f
        drawRoundRect(Color.White.copy(alpha = 0.16f), cornerRadius = CornerRadius(r))
        drawRoundRect(Color.White.copy(alpha = 0.35f), cornerRadius = CornerRadius(r), style = Stroke(1.dp.toPx()))
        val bright = Color.White.copy(alpha = 0.95f)
        val faint = Color.White.copy(alpha = 0.35f)
        // Harp (left): a curved frame with three strings. Bright while Tehillim is active.
        val harpColor = if (torah) faint else bright
        val hx = 9.dp.toPx(); val hy = r
        drawArc(harpColor, 200f, 200f, false, Offset(hx - 5.dp.toPx(), hy - 5.dp.toPx()), Size(10.dp.toPx(), 10.dp.toPx()), style = Stroke(1.2.dp.toPx()))
        for (i in -1..1) drawLine(harpColor, Offset(hx + i * 2.5f.dp.toPx(), hy - 3.dp.toPx()), Offset(hx + i * 2.5f.dp.toPx(), hy + 4.dp.toPx()), 1.dp.toPx())
        // Scroll (right): two rollers with a sheet between. Bright while Torah is active.
        val scrollColor = if (torah) bright else faint
        val sx = size.width - 9.dp.toPx(); val sy = r
        drawRoundRect(scrollColor, Offset(sx - 5.dp.toPx(), sy - 3.dp.toPx()), Size(10.dp.toPx(), 6.dp.toPx()), CornerRadius(1.dp.toPx()), style = Stroke(1.dp.toPx()))
        drawLine(scrollColor, Offset(sx - 5.dp.toPx(), sy - 4.dp.toPx()), Offset(sx - 5.dp.toPx(), sy + 4.dp.toPx()), 1.6f.dp.toPx())
        drawLine(scrollColor, Offset(sx + 5.dp.toPx(), sy - 4.dp.toPx()), Offset(sx + 5.dp.toPx(), sy + 4.dp.toPx()), 1.6f.dp.toPx())
        // Atzei chaim: thin handle stubs protruding 2dp past each roller end.
        for (dx in listOf(-5f, 5f)) for (dir in listOf(-1f, 1f)) drawLine(
            scrollColor, Offset(sx + dx.dp.toPx(), sy + dir * 4.dp.toPx()), Offset(sx + dx.dp.toPx(), sy + dir * 6.2f.dp.toPx()),
            0.9f.dp.toPx(), cap = StrokeCap.Round
        )
        // Flame knob: no halo, rests beside the active glyph rather than over it.
        val kx = 23.dp.toPx() + (size.width - 46.dp.toPx()) * knobX
        // Subdued knob: a muted amber disc with a soft rim (no flame animation).
        drawCircle(Color(0xFFC9972B).copy(alpha = 0.55f), radius = 7.dp.toPx(), center = Offset(kx, r))
        drawCircle(Color(0xFFE0B84C).copy(alpha = 0.9f), radius = 5.dp.toPx(), center = Offset(kx, r))
    }
}

/** Thin strip under the bar: this week's parsha and haftarah refs; tap a part to open it. */
@Composable
private fun WeekBanner(parsha: ParshaRef?, hebrew: Boolean, onParsha: () -> Unit, onHaftarah: () -> Unit) {
    if (parsha == null) return
    val sep = " · "
    // Sefaria's dotted ref reads as a citation: Deuteronomy.32.1-52 → Deuteronomy 32:1-52.
    val parshaText = if (hebrew) "פרשת ${parsha.nameHe}$sep${parsha.heRef}"
        else "Parashat ${parsha.nameEn}$sep${parsha.sefariaRef.replaceFirst('.', ' ').replace('.', ':')}"
    val haft = parsha.haftarot.joinToString(", ") { if (hebrew) it.nameHe else it.nameEn }
    Row(
        Modifier.fillMaxWidth().height(28.dp).background(Color(0x59181818)).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(parshaText, color = ACTIVE_GOLD, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).fillMaxHeight().clip(RoundedCornerShape(6.dp)).clickable { onParsha() }.padding(horizontal = 4.dp).wrapContentHeight())
        if (haft.isNotBlank()) {
            Text(sep, color = INACTIVE, fontSize = 11.sp)
            Text((if (hebrew) "הפטרה: " else "Haftarah: ") + haft, color = MINT, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).fillMaxHeight().clip(RoundedCornerShape(6.dp)).clickable { onHaftarah() }.padding(horizontal = 4.dp).wrapContentHeight())
        }
    }
}

/** Small popup listing the week's haftarot when there is more than one. */
@Composable
private fun HaftarahChooser(readings: List<Reading>, hebrew: Boolean, onPick: (Reading) -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }, Alignment.Center) {
        Column(Modifier.fillMaxWidth(0.8f).clip(RoundedCornerShape(14.dp)).background(Color(0xE6202020)).padding(8.dp)) {
            Text(if (hebrew) "הפטרה" else "Haftarah", color = ACTIVE_GOLD, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
            readings.forEach { r ->
                Text(if (hebrew) r.nameHe else r.nameEn, color = INACTIVE, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(r) }.padding(horizontal = 12.dp, vertical = 10.dp))
            }
        }
    }
}

/** The spinner shown at whichever edge is fetching the next/previous chapter. */
@Composable
private fun EdgeLoading() {
    Box(Modifier.fillMaxWidth().padding(10.dp), Alignment.Center) {
        CircularProgressIndicator(color = ACTIVE_GOLD, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
    }
}

/**
 * Fires [onExtendUp] / [onExtendDown] when a scroll or fling reaches an edge with travel
 * left over, and keeps the viewport still when new items land on top.
 *
 * [contentCount] counts only the real rows (sections or scroll lines), so the number of
 * rows prepended is its growth since the previous pass — the correction runs in the same
 * pass that brings the rows in, which is what keeps the jump invisible.
 *
 * The top [EdgeLoading] row occupies index 0 while a prepend is in flight and disappears
 * in the very same state update that adds the rows, so an old row moves by `added - 1`.
 * Sitting at index 0 is the only way to ask for a prepend, and there index 0 *was* the
 * spinner, so the row to restore is at `0 + added`; the `else` branch covers the rarer
 * case of the reader scrolling away while the fetch is in flight.
 */
@Composable
private fun rememberEdgeExtension(
    listState: androidx.compose.foundation.lazy.LazyListState,
    contentCount: Int,
    prependSignal: Int,
    onExtendUp: () -> Unit,
    onExtendDown: () -> Unit,
    onPrependConsumed: () -> Unit
): NestedScrollConnection {
    // A deliberate pull is needed to load more: dragging the text against its end by about half an
    // inch, once per touch. Flicks that merely reach the end, and small nudges, load nothing.
    val thresholdPx = with(LocalDensity.current) { EDGE_PULL_DP.dp.toPx() }
    val pull = remember(thresholdPx) { EdgePull(thresholdPx) }
    val latestUp = rememberUpdatedState(onExtendUp)
    val latestDown = rememberUpdatedState(onExtendDown)
    val connection = remember(pull) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.Drag) return Offset.Zero
                val info = listState.layoutInfo
                val atBottom = info.visibleItemsInfo.lastOrNull()?.index == info.totalItemsCount - 1
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                when (pull.onDrag(consumed.y, available.y, atTop, atBottom)) {
                    1 -> latestDown.value()
                    -1 -> latestUp.value()
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity { pull.release(); return Velocity.Zero }
        }
    }
    val previousCount = remember { mutableStateOf(contentCount) }
    LaunchedEffect(prependSignal, contentCount) {
        val added = contentCount - previousCount.value
        previousCount.value = contentCount
        if (prependSignal > 0) {
            if (added > 0) {
                val first = listState.firstVisibleItemIndex
                listState.scrollToItem(first + if (first == 0) added else added - 1, listState.firstVisibleItemScrollOffset)
            }
            onPrependConsumed()
        }
    }
    return connection
}

/**
 * Two slim translucent rows: [✕  title  Tehillim⇄Torah  ה  📖  ⚙] then the toggles —
 * language (EN/עב/תיקון), vocalization (ניקוד/טעמים), and, for a parsha, partitioning
 * (עליות). A haftarah has no aliyot and no scroll layout, so those toggles drop out.
 */
@Composable
private fun ReaderBar(
    title: String, expanded: Boolean, onToggleExpanded: () -> Unit,
    textMode: String, nikkud: Boolean, teamim: Boolean,
    showAliyotToggle: Boolean, byAliyot: Boolean,
    mode: String = "tehillim",
    torahSide: Boolean = false,
    chitasMode: Boolean = false,
    backLabel: String? = null,
    onBack: () -> Unit = {},
    onToggleMode: () -> Unit = {},
    onNav: () -> Unit = {},
    onTextMode: (String) -> Unit, onNikkud: () -> Unit, onTeamim: () -> Unit,
    onAliyot: () -> Unit, onClose: () -> Unit
) {
    val tikkun = textMode == MODE_TIKKUN
    val haftarahMode = mode == "haftarah"
    Column(Modifier.fillMaxWidth().background(Color(0x73181818))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("✕", color = INACTIVE, fontSize = 14.sp) }
            // Left Chitas through its navigator: one tap back to the day's three parts.
            if (backLabel != null) TextButton(onClick = onBack) { Text(backLabel, color = ACTIVE_GOLD, fontSize = 13.sp) }
            Text(
                title, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // Tehillim ⇄ Torah, then the week's haftarah when there is one.
            if (!chitasMode) ModeToggle(torah = torahSide, onToggle = onToggleMode)
            // Navigation dial (jump to any chapter / parsha).
            TextButton(onClick = onNav) {
                Icon(Icons.Default.MenuBook, contentDescription = "Jump to chapter or parsha", tint = ACTIVE_GOLD, modifier = Modifier.size(24.dp))
            }
            // Collapse/expand the settings row — keeps the text front and center.
            TextButton(onClick = onToggleExpanded) {
                Text(if (expanded) "▲" else "⚙", color = if (expanded) ACTIVE_GOLD else INACTIVE, fontSize = 14.sp)
            }
        }
        if (expanded) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarToggle("EN", active = textMode == MODE_EN) { onTextMode(MODE_EN) }
            BarToggle("עב", active = textMode == MODE_HE) { onTextMode(MODE_HE) }
            if (!haftarahMode) BarToggle("תיקון", active = tikkun) { onTextMode(MODE_TIKKUN) }
            Spacer(Modifier.width(10.dp))
            BarToggle("ניקוד", active = nikkud && !tikkun, enabled = !tikkun, onClick = onNikkud)
            BarToggle("טעמים", active = teamim && nikkud && !tikkun, enabled = nikkud && !tikkun, onClick = onTeamim)
            if (showAliyotToggle && !haftarahMode) {
                Spacer(Modifier.width(10.dp))
                BarToggle("עליות", active = byAliyot, onClick = onAliyot)
            }
        }
    }
}

/**
 * Continuous flowing text (like a printed Tanach): verses run inline, separated by
 * small gold numerals (gematria in Hebrew). Hebrew renders in true RTL; תיקון mode
 * renders unvocalized text in STaM sofer script. Partitioned by chapter, or by
 * aliyah when [aliyot] is non-empty. Pinch anywhere on the text to zoom.
 */
@Composable
private fun ReaderBody(
    text: SefariaText,
    textMode: String,
    nikkud: Boolean,
    teamim: Boolean,
    sizeSp: Float,
    mode: String = "tehillim",
    bookNameEn: String? = null,
    aliyot: List<String> = emptyList(),
    elulText: SefariaText? = null,
    legs: List<SefariaText?>? = null,
    legTitles: List<SectionTitle>? = null,
    legBlank: List<Boolean>? = null,
    legWindows: List<LegWindow?>? = null,
    legExtending: List<Pair<Boolean, Boolean>> = emptyList(),
    legFailed: List<Pair<Boolean, Boolean>> = emptyList(),
    chitasPart: Int = 0,
    onExtendLeg: (Int, Boolean) -> Unit = { _, _ -> },
    tikkunLines: List<TikkunLayout.Line>? = null,
    extendingUp: Boolean = false,
    extendingDown: Boolean = false,
    prependedSections: Int = 0,
    onExtendUp: () -> Unit = {},
    onExtendDown: () -> Unit = {},
    onPrependConsumed: () -> Unit = {},
    onZoom: (Float) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val hebrew = textMode != MODE_EN
    val tikkun = textMode == MODE_TIKKUN
    val gematria = remember { HebrewDateFormatter() }
    val stam = remember { FontFamily(Font(R.font.stam_ashkenaz)) }
    // Heading accent follows the source, matching the widget pills: amber for Tehillim, mint for Torah/haftarah.
    // Heading accent follows the banner: amber for Tehillim and Torah, mint only for haftarah.
    val accent = if (mode == "haftarah") MINT else ACTIVE_GOLD

    // Scroll-spacing markers and source line breaks fold into the flowing text.
    fun layout(t: String): String =
        t.replace("{פ}", "").replace("{ס}", "").replace("\n", " ").trim()

    // flip: this verse was tapped, so show it in the other language.
    // Instruction markers are only meaningful in prayers; everywhere else they are dropped.
    // Letter-size markers stay for [appendSized] to draw.
    fun verseBody(heRaw: String, en: String, flip: Boolean = false): String = when {
        tikkun -> HebrewVocalization.strip(layout(dropInstructionMarks(heRaw)), showNikkud = false, showTeamim = false)
        hebrew != flip -> HebrewVocalization.strip(layout(dropInstructionMarks(heRaw)), nikkud, teamim)
        else -> layout(dropInstructionMarks(en))
    }

    /** A prayer paragraph as display segments: instruction or prayer, vowels as the user chose. */
    fun prayerSegments(heRaw: String, en: String, flip: Boolean, vocalized: Boolean): List<Instructions.Segment> {
        val inHebrew = hebrew != flip
        val raw = layout(if (inHebrew) heRaw else en)
        return Instructions.segments(raw, hebrewVocalized = inHebrew && vocalized).map { s ->
            if (inHebrew) s.copy(text = HebrewVocalization.strip(s.text, nikkud && !tikkun, teamim && !tikkun)) else s
        }
    }

    // Verses tapped into the other language; cleared when the text or the language mode changes.
    var flippedVerses by remember(text, textMode) { mutableStateOf(setOf<String>()) }

    // Section list: one per aliyah when partitioning, else one per chapter.
    val sections = remember(text, mode, bookNameEn, aliyot, hebrew, elulText) {
        // `chapterSections` for the whole window: the shape used whenever the reading is
        // not partitioned by aliyah, and the fallback when partitioning cannot be mapped
        // onto the window. Kept as a function so the aliyot path does not build it.
        fun plainSections(): List<ReaderSection> =
            chapterSections(text, mode, bookNameEn, hebrew, gematria)

        // Verses outside every aliyah (scroll extension) stay as chapter sections on their side.
        // Chitas legs are not sections: they are keyed rows of their own (see `legRowList` below).
        val chitas = mode == "chitas"
        val split = if (chitas || aliyot.isEmpty()) null else AliyotPartitioner.layout(text.chapters, aliyot)
        val main: List<ReaderSection> = if (chitas) emptyList() else if (split == null) plainSections() else {
            val leadingSections: List<ReaderSection> =
                chapterSections(SefariaText(text.heRef, split.leading), mode, bookNameEn, hebrew, gematria)
            val aliyahSections: List<ReaderSection> = split.sections.map { sec ->
                ReaderSection(
                    AliyotPartitioner.aliyahName(sec.index, aliyot.size, hebrew),
                    sec.verses,
                    markChapters = true
                )
            }
            val trailingSections: List<ReaderSection> =
                chapterSections(SefariaText(text.heRef, split.trailing), mode, bookNameEn, hebrew, gematria)
            leadingSections + aliyahSections + trailingSections
        }
        val extra = elulText?.let { et ->
            chapterSections(et, mode, bookNameEn, hebrew, gematria).map { it.copy(extra = true) }
        } ?: emptyList()
        main + extra
    }

    // A prayer with several sections opens with every section folded; tapping a heading
    // unfolds it, so the reader can jump between parts of the service.
    val foldable = mode == "tefillah" && sections.size > 1
    var folded by remember(text, mode, sections.size) {
        // A section with no heading has no fold affordance, so it must never start folded.
        mutableStateOf(if (foldable) sections.indices.filter { sections[it].title.isNotBlank() }.toSet() else emptySet())
    }

    // Authentic scroll layout: tikkun mode on a Torah reading with layout data.
    if (tikkun && tikkunLines != null) {
        TikkunScrollBody(
            tikkunLines, sizeSp, stam,
            extendingUp = extendingUp, extendingDown = extendingDown, prependedSections = prependedSections,
            onExtendUp = onExtendUp, onExtendDown = onExtendDown, onPrependConsumed = onPrependConsumed,
            onZoom = onZoom
        )
        return
    }

    val edgeScroll = rememberEdgeExtension(
        listState, sections.size, prependedSections, onExtendUp, onExtendDown, onPrependConsumed
    )

    // Chitas: each leg is its own run of keyed rows — heading, then (expanded) a fixed-height top
    // edge, its blocks and a fixed-height bottom edge — and grows on its own when a real scroll
    // reaches either end. Blocks landing above the text being read are scrolled away (see below).
    val legCount = if (mode == "chitas") legs.orEmpty().size else 0
    // Only the part picked in the toggle is shown; the others are not in the list at all.
    val shownLeg = chitasPart.coerceIn(0, (legCount - 1).coerceAtLeast(0))
    val legTitleText = (0 until legCount).map { i ->
        legTitles?.getOrNull(i)?.let { t -> if (hebrew) t.he.ifBlank { t.en } else t.en } ?: ""
    }
    val legRowList: List<LegRows.Row> = (if (legCount > 0) listOf(shownLeg) else emptyList()).flatMap { i ->
        LegRows.rows(i, folded = false, blocks = legWindows?.getOrNull(i)?.blocks)
    }
    fun legEdgeState(leg: Int, down: Boolean): LegRows.EdgeState {
        val w = legWindows?.getOrNull(leg)
        val ext = legExtending.getOrElse(leg) { false to false }
        val f = legFailed.getOrElse(leg) { false to false }
        return LegRows.edgeState(
            canExtend = if (down) w?.canExtendDown == true else w?.canExtendUp == true,
            loading = if (down) ext.second else ext.first,
            failed = if (down) f.second else f.first
        )
    }
    // Direction of the user's current drag or fling: +1 toward the list's end, -1 toward its start,
    // 0 when no scroll is running; and a tick that counts every movement. Taken from the
    // nested-scroll deltas, which only the list's own gestures dispatch (not `scrollToItem` or
    // `dispatchRawDelta`), and counting travel the list could not follow, so a drag still has a
    // direction when every row already fits on screen or the list is at that end.
    // (This Compose version has no `LazyListState.lastScrolledForward`.)
    var chitasScrollDir by remember { mutableStateOf(0) }
    var chitasScrollTick by remember { mutableStateOf(0) }
    val chitasScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = consumed.y + available.y
                if (delta < -0.5f) { chitasScrollDir = 1; chitasScrollTick++ }
                else if (delta > 0.5f) { chitasScrollDir = -1; chitasScrollTick++ }
                return Offset.Zero
            }
        }
    }
    if (mode == "chitas") {
        // A new part starts at its own top.
        LaunchedEffect(shownLeg) { listState.scrollToItem(0) }
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }.collect { if (!it) chitasScrollDir = 0 }
        }
        legRowList.filterIsInstance<LegRows.Row.Edge>().forEach { e ->
            key(e.key) {
                val w = legWindows?.getOrNull(e.leg)
                LegTriggerEffect(
                    listState = listState,
                    rowKey = e.key,
                    down = e.down,
                    firstBlockRowKey = if (e.down) null else
                        w?.blocks?.firstOrNull { it.verses.isNotEmpty() }?.let { LegRows.blockRowKey(e.leg, it.key) },
                    canExtend = if (e.down) w?.canExtendDown == true else w?.canExtendUp == true,
                    extending = legExtending.getOrElse(e.leg) { false to false },
                    failed = legFailed.getOrElse(e.leg) { false to false }.let { if (e.down) it.second else it.first },
                    scrollDir = { chitasScrollDir },
                    scrollTick = { chitasScrollTick },
                    onFire = { onExtendLeg(e.leg, e.down) }
                )
            }
        }
        // Keep the text being read still when blocks are prepended to a leg. Compose keeps the
        // first visible row still, and the top edge only fires with that edge (and so the leg's
        // heading or edge row, above the insertion point) on screen, unless the early zone fired
        // while the leg's first block was the anchor. A new block that is on screen after the
        // insertion therefore landed below the anchor and pushed the text down by its height, so
        // scroll forward by exactly that much. `dispatchRawDelta` needs no scroll mutex, so a drag
        // in progress cannot cancel it, and it remeasures at once, so a second new block that
        // comes into view is handled in the same pass. A down-extension never changes a leg's
        // first block, and blocks that landed off screen (the reader scrolled away during the
        // fetch, or the early zone) are not on screen, so neither ever scrolls anything.
        (if (legCount > 0) listOf(shownLeg) else emptyList()).forEach { i ->
            val blocks = legWindows?.getOrNull(i)?.blocks.orEmpty()
            val firstKey = blocks.firstOrNull { it.verses.isNotEmpty() }?.key
            key("leg-prepend", i) {
                val lastFirst = remember { arrayOfNulls<String>(1) }
                LaunchedEffect(firstKey) {
                    val newKeys = LegRows.prependedKeys(i, lastFirst[0], blocks)
                    lastFirst[0] = firstKey
                    if (newKeys == null) return@LaunchedEffect
                    withFrameNanos { }   // the frame that brought the new blocks in has been laid out
                    val done = mutableSetOf<String>()
                    while (done.size < newKeys.size) {
                        val (shift, now) = LegRows.prependShift(
                            newKeys, done, listState.layoutInfo.visibleItemsInfo.map { it.key to it.size }
                        )
                        if (shift <= 0) break
                        listState.dispatchRawDelta(shift.toFloat())
                        done += now
                    }
                }
            }
        }
    }
    // An edge row has one height whatever it shows, so nothing next to it moves when it changes.
    val edgeHeight = with(LocalDensity.current) { (sizeSp * 1.4f).sp.toDp() }.coerceAtLeast(24.dp)

    /**
     * One block of a leg as a flowing paragraph, styled as a leg's verses always were. Chapter
     * marks continue from [prevChapter], the previous block's last chapter, so a block without a
     * heading that opens a new chapter is still marked; a headed block's heading names its first.
     */
    fun legParagraph(leg: Int, block: LegBlock, prevChapter: Int, headed: Boolean): AnnotatedString = buildAnnotatedString {
        var lastChapter = prevChapter
        var first = true
        block.verses.forEach { tv ->
            val verseKey = LegRows.verseKey(leg, block.key, tv.chapter, tv.verse.num)
            val flipped = verseKey in flippedVerses
            val body = verseBody(tv.verse.he, tv.verse.en, flipped)
                .ifBlank { verseBody(tv.verse.he, tv.verse.en, !flipped) }
            if (body.isBlank()) return@forEach
            if (LegRows.markBefore(lastChapter, tv.chapter, firstOfHeadedBlock = first && headed)) {
                withStyle(SpanStyle(color = accent, fontSize = (sizeSp * 0.7f).sp)) {
                    append(if (hebrew) " ‹פרק ${gematria.formatHebrewNumber(tv.chapter)}› " else " ‹Ch. ${tv.chapter}› ")
                }
            }
            lastChapter = tv.chapter
            first = false
            pushStringAnnotation("verse", verseKey)
            withStyle(SpanStyle(color = accent, fontSize = (sizeSp * 0.62f).sp)) {
                append(if (hebrew) gematria.formatHebrewNumber(tv.verse.num) else tv.verse.num.toString())
            }
            append(" ")
            appendSized(body, sizeSp)
            append("  ")
            pop()
        }
    }

    val direction = if (hebrew) LayoutDirection.Rtl else LayoutDirection.Ltr
    Box(Modifier.fillMaxSize().pinchZoom(onZoom)) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).nestedScroll(edgeScroll)
                    .then(if (mode == "chitas") Modifier.nestedScroll(chitasScrollConnection) else Modifier)
            ) {
                if (extendingUp) item { EdgeLoading() }
                legRowList.forEach { row ->
                    item(key = row.key, contentType = row.javaClass) {
                        when (row) {
                            is LegRows.Row.Head -> {
                                val title = legTitleText.getOrElse(row.leg) { "" }
                                if (title.isBlank()) Spacer(Modifier.height(12.dp))
                                else Text(
                                    title, color = accent, fontSize = (sizeSp + 2).sp,
                                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
                                    textAlign = TextAlign.Start
                                )
                            }
                            is LegRows.Row.Empty -> Text(
                                if (legBlank?.getOrNull(row.leg) == true) { if (hebrew) "אין חלק להיום" else "Not available for this day" }
                                else if (hebrew) "נדרש חיבור לאינטרנט" else "Needs a connection to load",
                                color = Color.White.copy(alpha = 0.6f), fontSize = sizeSp.sp,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                textAlign = TextAlign.Start
                            )
                            // Fixed height whatever it shows; what fires it is LegTriggerEffect, not its being here.
                            is LegRows.Row.Edge -> {
                                val state = legEdgeState(row.leg, row.down)
                                Box(
                                    Modifier.fillMaxWidth().height(edgeHeight)
                                        .then(if (state == LegRows.EdgeState.RETRY) Modifier.clickable { onExtendLeg(row.leg, row.down) } else Modifier),
                                    contentAlignment = if (state == LegRows.EdgeState.LOADING) Alignment.Center else Alignment.CenterStart
                                ) {
                                    when (state) {
                                        LegRows.EdgeState.LOADING ->
                                            CircularProgressIndicator(color = ACTIVE_GOLD, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        LegRows.EdgeState.RETRY -> Text(
                                            if (hebrew) "נדרש חיבור לאינטרנט — הקש לנסות שוב" else "Needs a connection — tap to retry",
                                            color = Color.White.copy(alpha = 0.6f), fontSize = (sizeSp * 0.8f).sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Start
                                        )
                                        else -> {}
                                    }
                                }
                            }
                            is LegRows.Row.Block -> Column(Modifier.fillMaxWidth()) {
                                val heading = if (hebrew) row.block.headingHe ?: row.block.heading else row.block.heading
                                if (heading != null) Text(
                                    heading, color = accent.copy(alpha = 0.75f), fontSize = (sizeSp * 0.8f).sp,
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                                    textAlign = TextAlign.Start
                                )
                                // Rebuilt only when this block, its own flipped verses or the text settings change.
                                val prefix = LegRows.blockKeyPrefix(row.leg, row.block.key)
                                val blockFlips = flippedVerses.filterTo(HashSet()) { it.startsWith(prefix) }
                                val paragraph = remember(row, heading != null, blockFlips, textMode, nikkud, teamim, sizeSp, accent) {
                                    legParagraph(row.leg, row.block, row.prevChapter, headed = heading != null)
                                }
                                // Tap a verse to read just that verse in the other language; tap again to switch back.
                                ClickableText(
                                    paragraph,
                                    style = TextStyle(
                                        color = Color.White,
                                        fontSize = sizeSp.sp,
                                        lineHeight = (sizeSp * if (tikkun) 1.5f else 1.75f).sp,
                                        textAlign = TextAlign.Start,
                                        textDirection = TextDirection.Content,
                                        fontFamily = if (tikkun) stam else null
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { offset ->
                                        if (!tikkun) paragraph.getStringAnnotations("verse", offset, offset)
                                            .firstOrNull()?.let { a ->
                                                flippedVerses = if (a.item in flippedVerses) flippedVerses - a.item
                                                    else flippedVerses + a.item
                                            }
                                    }
                                )
                            }
                        }
                    }
                }
                itemsIndexed(sections) { index, section ->
                    if (section.extra && (index == 0 || !sections[index - 1].extra)) {
                        // Visual separation for the Elul supplement: rule + labeled header.
                        Box(
                            Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 2.dp)
                                .height(1.dp).background(SKY.copy(alpha = 0.45f))
                        ) {}
                        Text(
                            if (hebrew) "תהילים לאלול" else "Elul Tehillim",
                            color = SKY, fontSize = (sizeSp * 0.8f).sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            textAlign = TextAlign.Start
                        )
                    }
                    val isFolded = foldable && index in folded
                    if (section.title.isBlank()) Spacer(Modifier.height(12.dp))
                    else if (foldable) Row(
                        Modifier.fillMaxWidth()
                            .clickable { folded = if (isFolded) folded - index else folded + index }
                            .padding(top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (!isFolded) "▾" else if (hebrew) "◂" else "▸",
                            color = accent, fontSize = (sizeSp * 0.9f).sp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            section.title, color = accent, fontSize = (sizeSp + 2).sp,
                            modifier = Modifier.weight(1f), textAlign = TextAlign.Start
                        )
                    }
                    else Text(
                        section.title, color = if (section.extra) SKY else accent, fontSize = (sizeSp + 2).sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
                        textAlign = TextAlign.Start
                    )
                    if (!isFolded) {
                    // Prayers: instructions smaller and grey, each on its own line with a half-line
                    // gap from the prayer; mid-paragraph ones stay inline. Decided per section from
                    // whether its Hebrew is vocalized (unvowelled words then mark instructions).
                    val vocalized = mode == "tefillah" &&
                        Instructions.vocalizedShare(section.verses.map { it.verse.he }) >= 0.5f
                    val paragraph = buildAnnotatedString {
                        var lastChapter = -1
                        var lastKind: Boolean? = null   // was the last line an instruction?
                        val instructionSize = (sizeSp - 2f).coerceAtLeast(10f)
                        fun spacer() = withStyle(ParagraphStyle(lineHeight = (sizeSp * 0.5f).sp)) {
                            withStyle(SpanStyle(fontSize = (sizeSp * 0.4f).sp)) { append("\u200B") }
                        }
                        fun line(instruction: Boolean, content: AnnotatedString.Builder.() -> Unit) {
                            if (lastKind != null && lastKind != instruction) spacer()
                            val style = if (instruction) ParagraphStyle(lineHeight = (instructionSize * 1.5f).sp)
                                else ParagraphStyle(lineHeight = (sizeSp * 1.75f).sp)
                            withStyle(style) { content() }
                            lastKind = instruction
                        }
                        section.verses.forEach { tv ->
                            val verseKey = "${if (section.extra) "e" else "m"}:$index:${tv.chapter}:${tv.verse.num}"
                            // If the other language has no text for this verse, keep the original.
                            // A verse with no text in the shown language (e.g. Sefard's Hebrew-only
                            // siddur in English mode) falls back to the other language.
                            val flipped = verseKey in flippedVerses
                            val body = verseBody(tv.verse.he, tv.verse.en, flipped)
                                .ifBlank { verseBody(tv.verse.he, tv.verse.en, !flipped) }
                            if (body.isBlank()) return@forEach
                            if (mode == "tefillah") {
                                val useFlip = if (verseBody(tv.verse.he, tv.verse.en, flipped).isBlank()) !flipped else flipped
                                val segs = prayerSegments(tv.verse.he, tv.verse.en, useFlip, vocalized)
                                pushStringAnnotation("verse", verseKey)
                                // Leading and trailing instructions get their own lines; the rest is one line.
                                val first = segs.indexOfFirst { !it.instruction }
                                val last = segs.indexOfLast { !it.instruction }
                                if (first < 0) {
                                    line(true) { withStyle(SpanStyle(color = INSTRUCTION, fontSize = instructionSize.sp)) { appendSized(segs.joinToString(" ") { it.text }, instructionSize) } }
                                } else {
                                    if (first > 0) line(true) {
                                        withStyle(SpanStyle(color = INSTRUCTION, fontSize = instructionSize.sp)) { appendSized(segs.subList(0, first).joinToString(" ") { it.text }, instructionSize) }
                                    }
                                    line(false) {
                                        segs.subList(first, last + 1).forEachIndexed { k, s ->
                                            if (k > 0) append(" ")
                                            if (s.instruction) withStyle(SpanStyle(color = INSTRUCTION, fontSize = instructionSize.sp)) { appendSized(s.text, instructionSize) }
                                            else appendSized(s.text, sizeSp)
                                        }
                                    }
                                    if (last < segs.lastIndex) line(true) {
                                        withStyle(SpanStyle(color = INSTRUCTION, fontSize = instructionSize.sp)) { appendSized(segs.subList(last + 1, segs.size).joinToString(" ") { it.text }, instructionSize) }
                                    }
                                }
                                pop()
                                return@forEach
                            }
                            if (section.markChapters && tv.chapter != lastChapter) {
                                if (lastChapter != -1) {
                                    withStyle(SpanStyle(color = if (section.extra) SKY else accent, fontSize = (sizeSp * 0.7f).sp)) {
                                        append(if (hebrew) " ‹פרק ${gematria.formatHebrewNumber(tv.chapter)}› " else " ‹Ch. ${tv.chapter}› ")
                                    }
                                }
                                lastChapter = tv.chapter
                            }
                            pushStringAnnotation("verse", verseKey)
                            withStyle(SpanStyle(color = if (section.extra) SKY else accent, fontSize = (sizeSp * 0.62f).sp)) {
                                append(if (hebrew) gematria.formatHebrewNumber(tv.verse.num) else tv.verse.num.toString())
                            }
                            append(" ")
                            appendSized(body, sizeSp)
                            append("  ")
                            pop()
                        }
                    }
                    // Tap a verse to read just that verse in the other language; tap again to
                    // switch back. The scroll layout has no translation, so tikkun ignores taps.
                    ClickableText(
                        paragraph,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = sizeSp.sp,
                            lineHeight = (sizeSp * if (tikkun) 1.5f else 1.75f).sp,
                            textAlign = TextAlign.Start,
                            textDirection = TextDirection.Content,
                            fontFamily = if (tikkun) stam else null
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { offset ->
                            if (!tikkun) paragraph.getStringAnnotations("verse", offset, offset)
                                .firstOrNull()?.let { a ->
                                    flippedVerses = if (a.item in flippedVerses) flippedVerses - a.item
                                        else flippedVerses + a.item
                                }
                        }
                    )
                    }
                }
                item { Text(if (hebrew) "מקור: ספריא" else "Source: Sefaria", color = Color(0xFFAA9977), fontSize = 11.sp, modifier = Modifier.padding(16.dp)) }
                if (extendingDown) item { EdgeLoading() }
            }
        }
        // Fast-scroll thumb: drag maps to section index. (CenterEnd flips to the left
        // edge in RTL, which is where a Hebrew reader expects the scroller.)
        FastScrollThumb(listState, legRowList.size + sections.size + 1, Modifier.align(Alignment.CenterEnd))
    }
}

/**
 * Chitas: asks [onFire] to extend one end of a leg when that end is on screen during a real
 * scroll toward it (see [LegRows.shouldFire]). Being on screen alone never fires it, so unfolding
 * a short leg whose ends are already visible loads nothing. "On screen" is the edge row [rowKey]
 * being laid out in the viewport, or, for the top edge, the leg's first block ([firstBlockRowKey])
 * being the first row with its top less than a screen away ([LegRows.inTopZone]).
 *
 * The effect restarts whenever that visibility, the leg's [extending] pair, [failed] or
 * [canExtend] change, and each run fires at most once, and only after the scroll has moved
 * ([scrollTick] changed) since the run began, so a finger held still does not fire it. The
 * ViewModel ignores a request for a leg that is busy in either direction, so an edge still on
 * screen gets a fresh run when the leg frees up and fires again if the user keeps scrolling its way.
 * [scrollDir] is the direction of the user's current drag or fling (+1 forward, -1 back, 0 none).
 */
@Composable
private fun LegTriggerEffect(
    listState: androidx.compose.foundation.lazy.LazyListState,
    rowKey: String,
    down: Boolean,
    firstBlockRowKey: String?,
    canExtend: Boolean,
    extending: Pair<Boolean, Boolean>,
    failed: Boolean,
    scrollDir: () -> Int,
    scrollTick: () -> Int,
    onFire: () -> Unit
) {
    val visible by remember(listState, rowKey, firstBlockRowKey) {
        derivedStateOf {
            val info = listState.layoutInfo
            val edgeVisible = info.visibleItemsInfo.any { it.key == rowKey }
            if (down) edgeVisible else {
                val first = info.visibleItemsInfo.firstOrNull()
                LegRows.inTopZone(
                    edgeVisible = edgeVisible,
                    firstVisibleKey = first?.key,
                    firstVisibleOffset = maxOf(0, info.viewportStartOffset - (first?.offset ?: 0)),
                    viewportHeight = info.viewportEndOffset - info.viewportStartOffset,
                    firstBlockRowKey = firstBlockRowKey
                )
            }
        }
    }
    val fire by rememberUpdatedState(onFire)
    val readDir by rememberUpdatedState(scrollDir)
    val readTick by rememberUpdatedState(scrollTick)
    LaunchedEffect(visible, extending, failed, canExtend) {
        if (!visible) return@LaunchedEffect
        val busy = extending.first || extending.second
        val startTick = readTick()
        snapshotFlow {
            LegRows.shouldFire(
                visible = visible,
                scrollInProgress = listState.isScrollInProgress,
                scrolledForward = readDir() > 0,
                scrolledBackward = readDir() < 0,
                down = down, canExtend = canExtend, busy = busy, failed = failed,
                movedSinceStart = readTick() != startTick
            )
        }.first { it }
        fire()
    }
}

/**
 * Line-for-line Torah scroll rendering (layout data from the MIT-licensed
 * tikkun.io project): each row is one line of the standard 42-line column, in
 * unvocalized STaM script. Petucha lines end their paragraph with a gap; new
 * columns are marked by a thin rule. Verse numbers are absent — as on a scroll.
 */
@Composable
private fun TikkunScrollBody(
    lines: List<TikkunLayout.Line>,
    sizeSp: Float,
    stam: FontFamily,
    extendingUp: Boolean = false,
    extendingDown: Boolean = false,
    prependedSections: Int = 0,
    onExtendUp: () -> Unit = {},
    onExtendDown: () -> Unit = {},
    onPrependConsumed: () -> Unit = {},
    onZoom: (Float) -> Unit
) {
    val listState = rememberLazyListState()
    val accent = ACTIVE_GOLD   // tikkun is Torah-only; rule matches the Torah reference amber
    // A prepend re-lays the whole range, so the rows gained at the top are the list's growth.
    val edgeScroll = rememberEdgeExtension(
        listState, lines.size, prependedSections, onExtendUp, onExtendDown, onPrependConsumed
    )
    Box(Modifier.fillMaxSize().pinchZoom(onZoom)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).nestedScroll(edgeScroll)
            ) {
                if (extendingUp) item { EdgeLoading() }
                itemsIndexed(lines) { i, line ->
                    if (line.firstLineOfPage && i != 0) {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp)
                                .height(1.dp).background(accent.copy(alpha = 0.35f))
                        ) {}
                    }
                    Text(
                        HebrewVocalization.strip(line.text, showNikkud = false, showTeamim = false),
                        style = TextStyle(
                            color = Color.White,
                            fontSize = sizeSp.sp,
                            lineHeight = (sizeSp * 1.45f).sp,
                            textAlign = TextAlign.Start,
                            textDirection = TextDirection.Rtl,
                            fontFamily = stam
                        ),
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                            .padding(bottom = if (line.isPetucha) (sizeSp * 0.9f).dp else 0.dp)
                    )
                }
                item {
                    Text(
                        "פריסת עמודים: tikkun.io · מקור: ספריא",
                        color = Color(0xFFAA9977), fontSize = 11.sp, modifier = Modifier.padding(16.dp)
                    )
                }
                if (extendingDown) item { EdgeLoading() }
            }
        }
        FastScrollThumb(listState, lines.size + 1, Modifier.align(Alignment.CenterEnd))
    }
}

/**
 * Pinch-to-zoom that wins over list scrolling: watch the pointer stream in the Initial pass
 * (before the LazyColumn sees it); with two fingers down, apply the zoom and consume the
 * events so scroll never claims the gesture. One finger passes through untouched.
 */
private fun Modifier.pinchZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) onZoom(zoom)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

/** The text without siddur instruction markers; letter-size markers stay. */
private fun dropInstructionMarks(t: String): String =
    t.filter { it != Instructions.OPEN && it != Instructions.CLOSE }

/** Appends [text], drawing marked small / large letters 5sp smaller / larger than [sizeSp]. */
private fun AnnotatedString.Builder.appendSized(text: String, sizeSp: Float) {
    var depth = 0
    for (c in text) when (c) {
        Instructions.SMALL_OPEN -> { pushStyle(SpanStyle(fontSize = (sizeSp - 5f).coerceAtLeast(1f).sp)); depth++ }
        Instructions.BIG_OPEN -> { pushStyle(SpanStyle(fontSize = (sizeSp + 5f).sp)); depth++ }
        Instructions.SMALL_CLOSE, Instructions.BIG_CLOSE -> if (depth > 0) { pop(); depth-- }
        Instructions.OPEN, Instructions.CLOSE -> {}
        else -> append(c)
    }
    while (depth > 0) { pop(); depth-- }
}

private fun chapterSections(
    text: SefariaText,
    mode: String,
    bookNameEn: String?,
    hebrew: Boolean,
    gematria: HebrewDateFormatter
): List<ReaderSection> = if (mode == "tefillah") {
    // A prayer: one section per siddur section, titled in the reader's language, with
    // paragraphs instead of numbered verses.
    text.chapters.mapIndexed { i, ch ->
        val t = text.sectionTitles?.getOrNull(i)
        val title = if (t == null) "" else if (hebrew) t.he.ifBlank { t.en } else t.en
        ReaderSection(title, ch.verses.map { AliyotPartitioner.TaggedVerse(ch.number, it) }, markChapters = false)
    }
} else text.chapters.map { ch ->
    val header = when {
        hebrew -> "פרק ${gematria.formatHebrewNumber(ch.number)}"
        mode == "parsha" || mode == "haftarah" -> "${(bookNameEn ?: "Chapter").replace('_', ' ')} ${ch.number}"
        mode == "tanya" -> "Chapter ${ch.number}"
        else -> "Psalm ${ch.number}"
    }
    ReaderSection(header, ch.verses.map { AliyotPartitioner.TaggedVerse(ch.number, it) }, markChapters = false)
}

private data class ReaderSection(
    val title: String,
    val verses: List<AliyotPartitioner.TaggedVerse>,
    val markChapters: Boolean,
    val extra: Boolean = false
)

@Composable
private fun FastScrollThumb(
    listState: androidx.compose.foundation.lazy.LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var trackHeight by remember { mutableStateOf(1f) }
    Box(
        modifier
            .fillMaxHeight()
            .width(28.dp)
            .onGloballyPositioned { trackHeight = it.size.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(itemCount, trackHeight) {
                detectVerticalDragGestures { change, _ ->
                    val fraction = (change.position.y / trackHeight).coerceIn(0f, 1f)
                    val target = (fraction * (itemCount - 1)).toInt().coerceAtLeast(0)
                    scope.launch { listState.scrollToItem(target) }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight(0.4f).background(Color.White.copy(alpha = 0.4f)))
    }
}
