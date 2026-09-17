package com.hebrewcal.ui.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import com.hebrewcal.data.TorahNavigation
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.runtime.State
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import com.hebrewcal.R
import com.hebrewcal.data.AliyotPartitioner
import com.hebrewcal.data.CalendarLanguage
import com.hebrewcal.data.HebrewVocalization
import com.hebrewcal.data.ParshaRef
import com.hebrewcal.data.Reading
import com.hebrewcal.data.SefariaText
import com.hebrewcal.data.TikkunLayout
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

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
        vm.load(mode, ref, title, diaspora, elulRef, presetAliyot = aliyot)

        setContent {
            val state by vm.state.collectAsState()
            val persistedSize by vm.textSize.collectAsState()
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

            Box(
                Modifier.fillMaxSize()
                    .background(Color(0xFF2E2E2E).copy(alpha = 0.5f))
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
                        onToggleMode = { vm.toggleMode(hebrewUi) },
                        showHaftarah = state.mode == "parsha" && !week.parsha?.haftarot.isNullOrEmpty(),
                        onHaftarah = { haftarah() },
                        onNav = { showNav = true },
                        onTextMode = { m ->
                            textMode = m
                        },
                        onNikkud = { nikkud = !nikkud; if (!nikkud) teamim = false },
                        onTeamim = { teamim = !teamim; if (teamim) nikkud = true },
                        onAliyot = { byAliyot = !byAliyot },
                        onClose = { finish() }
                    )
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
                    NavDialOverlay(
                        mode = state.mode,
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
    }
}

private val ACTIVE_GOLD = Color(0xFFFFD255)   // vivid warm amber
private val ACTIVE_BG = Color(0x47FFC94D)     // stronger amber pill behind active toggles
private val INACTIVE = Color(0xE6FFFFFF)      // near-white, clearly legible
private val DISABLED = Color(0x66FFFFFF)
private val MINT = Color(0xFF6EE7B7)         // haftarah / supplement accent

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

/** Ember palette for the animated flame chips: deep red → orange → gold and back. */
private val FLAME = listOf(
    Color(0xFFB3160B), Color(0xFFFF4D00), Color(0xFFFFA000), Color(0xFFFFE066),
    Color(0xFFFFA000), Color(0xFFFF4D00), Color(0xFFB3160B)
)
private val FLAME_INK = Color(0xFF1A0A00)

/** Shared flame animation: a linear sweep phase (0→1) and a breathing glow (0.5↔1). */
@Composable
private fun rememberFlame(): Pair<State<Float>, State<Float>> {
    val transition = rememberInfiniteTransition(label = "flame")
    val phase = transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "flamePhase"
    )
    val breath = transition.animateFloat(
        0.5f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flameBreath"
    )
    return phase to breath
}

/** Soft ember halo drawn in expanding translucent layers around the whole bounds. */
private fun DrawScope.emberGlow(breath: Float, cornerPx: Float) {
    for (i in 1..5) {
        val grow = i * 4.dp.toPx()
        drawRoundRect(
            color = Color(0xFFFF6A00).copy(alpha = 0.14f * breath * (6 - i) / 5f),
            topLeft = Offset(-grow, -grow),
            size = Size(size.width + 2 * grow, size.height + 2 * grow),
            cornerRadius = CornerRadius(cornerPx + grow)
        )
    }
}

/** Navigation chip: quiet when inactive; the active one burns with a sweeping gradient and breathing glow. */
@Composable
private fun FlameToggle(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    if (!active) {
        Text(
            label, color = INACTIVE, fontSize = 15.sp,
            modifier = modifier.padding(horizontal = 3.dp).clip(shape).clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 7.dp)
        )
        return
    }
    val (phase, breath) = rememberFlame()
    Text(
        label, color = FLAME_INK, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = 3.dp)
            .drawBehind { emberGlow(breath.value, 14.dp.toPx()) }
            .clip(shape)
            .drawBehind {
                val w = size.width
                val shift = 2 * w * phase.value   // mirror tiling makes a 2w shift loop seamlessly
                drawRect(
                    Brush.linearGradient(
                        FLAME, start = Offset(-w + shift, 0f), end = Offset(shift, size.height), tileMode = TileMode.Mirror
                    )
                )
            }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

/** The dial's "go" control: a 46dp ring of spinning fire with a bold ↵ so it reads as a button. */
@Composable
private fun FlameGoButton(onGo: () -> Unit) {
    val (phase, breath) = rememberFlame()
    Box(
        Modifier.padding(horizontal = 12.dp).size(46.dp)
            .drawBehind {
                val r = size.minDimension / 2f
                for (i in 1..5) drawCircle(
                    Color(0xFFFF6A00).copy(alpha = 0.14f * breath.value * (6 - i) / 5f),
                    radius = r + i * 4.dp.toPx()
                )
                rotate(phase.value * 360f) { drawCircle(Brush.sweepGradient(FLAME), radius = r) }
                drawCircle(Color(0xFFFFB300), radius = r - 5.dp.toPx())
            }
            .clip(CircleShape)
            .clickable { onGo() },
        contentAlignment = Alignment.Center
    ) {
        Text("↵", color = FLAME_INK, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
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
    val (phase, _) = rememberFlame()
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
        // Flame knob: no halo, rests beside the active glyph rather than over it.
        val kx = 23.dp.toPx() + (size.width - 46.dp.toPx()) * knobX
        rotate(phase.value * 360f, pivot = Offset(kx, r)) { drawCircle(Brush.sweepGradient(FLAME, center = Offset(kx, r)), radius = 7.dp.toPx(), center = Offset(kx, r)) }
        drawCircle(Color(0xFFFFB300), radius = 4.5f.dp.toPx(), center = Offset(kx, r))
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
        Modifier.fillMaxWidth().height(18.dp).background(Color(0x59181818)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(parshaText, color = ACTIVE_GOLD, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onParsha() })
        if (haft.isNotBlank()) {
            Text(sep, color = INACTIVE, fontSize = 11.sp)
            Text((if (hebrew) "הפטרה: " else "Haftarah: ") + haft, color = MINT, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onHaftarah() })
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
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -2f && consumed.y == 0f) {
                    val info = listState.layoutInfo
                    if (info.visibleItemsInfo.lastOrNull()?.index == info.totalItemsCount - 1) onExtendDown()
                } else if (available.y > 2f && consumed.y == 0f &&
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    onExtendUp()
                }
                return Offset.Zero
            }
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
    onToggleMode: () -> Unit = {},
    showHaftarah: Boolean = false,
    onHaftarah: () -> Unit = {},
    onNav: () -> Unit = {},
    onTextMode: (String) -> Unit, onNikkud: () -> Unit, onTeamim: () -> Unit,
    onAliyot: () -> Unit, onClose: () -> Unit
) {
    val tikkun = textMode == MODE_TIKKUN
    val haftarahMode = mode == "haftarah"
    Column(Modifier.fillMaxWidth().background(Color(0x73181818))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("✕", color = INACTIVE, fontSize = 14.sp) }
            Text(
                title, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // Tehillim ⇄ Torah, then the week's haftarah when there is one.
            ModeToggle(torah = torahSide, onToggle = onToggleMode)
            if (showHaftarah) TextButton(onClick = onHaftarah) {
                Text("ה", color = MINT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
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
    val gold = Color(0xFFD4AF37)

    // Scroll-spacing markers and source line breaks fold into the flowing text.
    fun layout(t: String): String =
        t.replace("{פ}", "").replace("{ס}", "").replace("\n", " ").trim()

    fun verseBody(heRaw: String, en: String): String = when {
        tikkun -> HebrewVocalization.strip(layout(heRaw), showNikkud = false, showTeamim = false)
        hebrew -> HebrewVocalization.strip(layout(heRaw), nikkud, teamim)
        else -> layout(en)
    }

    // Section list: one per aliyah when partitioning, else one per chapter.
    val sections = remember(text, mode, bookNameEn, aliyot, hebrew, elulText) {
        val main = if (aliyot.isNotEmpty()) {
            val parts = AliyotPartitioner.partition(text.chapters, aliyot)
            if (parts.isNotEmpty()) {
                // Chapters pulled in by scroll extension fall outside every aliyah; keep
                // them as plain chapter sections on the side of the window they came from.
                val covered = parts.flatMap { p -> p.verses.map { it.chapter to it.verse.num } }.toHashSet()
                val touched = text.chapters.indices.filter { i ->
                    val ch = text.chapters[i]
                    ch.verses.any { (ch.number to it.num) in covered }
                }
                val firstTouched = touched.firstOrNull() ?: 0
                val lastTouched = touched.lastOrNull() ?: text.chapters.lastIndex
                val before = text.chapters.subList(0, firstTouched)
                val after = text.chapters.subList((lastTouched + 1).coerceAtMost(text.chapters.size), text.chapters.size)
                chapterSections(SefariaText(text.heRef, before), mode, bookNameEn, hebrew, gematria) +
                parts.map { sec -> ReaderSection(AliyotPartitioner.aliyahName(sec.index, aliyot.size, hebrew), sec.verses, markChapters = true) } +
                chapterSections(SefariaText(text.heRef, after), mode, bookNameEn, hebrew, gematria)
            } else chapterSections(text, mode, bookNameEn, hebrew, gematria)
        } else chapterSections(text, mode, bookNameEn, hebrew, gematria)
        val extra = elulText?.let { et ->
            chapterSections(et, mode, bookNameEn, hebrew, gematria).map { it.copy(extra = true) }
        } ?: emptyList()
        main + extra
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
    val direction = if (hebrew) LayoutDirection.Rtl else LayoutDirection.Ltr
    // Pinch-to-zoom that wins over list scrolling: watch the pointer stream in the
    // Initial pass (before the LazyColumn sees it); with two fingers down, apply the
    // zoom and consume the events so scroll never claims the gesture. One finger
    // passes through untouched.
    val zoomModifier = Modifier.pointerInput(Unit) {
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
    Box(Modifier.fillMaxSize().then(zoomModifier)) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).nestedScroll(edgeScroll)
            ) {
                if (extendingUp) item { EdgeLoading() }
                itemsIndexed(sections) { index, section ->
                    if (section.extra && (index == 0 || !sections[index - 1].extra)) {
                        // Visual separation for the Elul supplement: rule + labeled header.
                        Box(
                            Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 2.dp)
                                .height(1.dp).background(MINT.copy(alpha = 0.45f))
                        ) {}
                        Text(
                            if (hebrew) "תהילים לאלול" else "Elul Tehillim",
                            color = MINT, fontSize = (sizeSp * 0.8f).sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            textAlign = TextAlign.Start
                        )
                    }
                    Text(
                        section.title, color = if (section.extra) MINT else gold, fontSize = (sizeSp + 2).sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
                        textAlign = TextAlign.Start
                    )
                    val paragraph = buildAnnotatedString {
                        var lastChapter = -1
                        section.verses.forEach { tv ->
                            val body = verseBody(tv.verse.he, tv.verse.en)
                            if (body.isBlank()) return@forEach
                            if (section.markChapters && tv.chapter != lastChapter) {
                                if (lastChapter != -1) {
                                    withStyle(SpanStyle(color = gold, fontSize = (sizeSp * 0.7f).sp)) {
                                        append(if (hebrew) " ‹פרק ${gematria.formatHebrewNumber(tv.chapter)}› " else " ‹Ch. ${tv.chapter}› ")
                                    }
                                }
                                lastChapter = tv.chapter
                            }
                            withStyle(SpanStyle(color = gold, fontSize = (sizeSp * 0.62f).sp)) {
                                append(if (hebrew) gematria.formatHebrewNumber(tv.verse.num) else tv.verse.num.toString())
                            }
                            append(" ")
                            append(body)
                            append("  ")
                        }
                    }
                    Text(
                        paragraph,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = sizeSp.sp,
                            lineHeight = (sizeSp * if (tikkun) 1.5f else 1.75f).sp,
                            textAlign = TextAlign.Start,
                            textDirection = TextDirection.Content,
                            fontFamily = if (tikkun) stam else null
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { Text(if (hebrew) "מקור: ספריא" else "Source: Sefaria", color = Color(0xFFAA9977), fontSize = 11.sp, modifier = Modifier.padding(16.dp)) }
                if (extendingDown) item { EdgeLoading() }
            }
        }
        // Fast-scroll thumb: drag maps to section index. (CenterEnd flips to the left
        // edge in RTL, which is where a Hebrew reader expects the scroller.)
        FastScrollThumb(listState, sections.size + 1, Modifier.align(Alignment.CenterEnd))
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
    val gold = Color(0xFFD4AF37)
    // A prepend re-lays the whole range, so the rows gained at the top are the list's growth.
    val edgeScroll = rememberEdgeExtension(
        listState, lines.size, prependedSections, onExtendUp, onExtendDown, onPrependConsumed
    )
    val zoomModifier = Modifier.pointerInput(Unit) {
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
    Box(Modifier.fillMaxSize().then(zoomModifier)) {
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
                                .height(1.dp).background(gold.copy(alpha = 0.35f))
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
 * Semi-transparent navigation overlay. Tehillim: a rotary dial over chapters
 * 1-150. Torah: pick a book, then dial a chapter or choose a parsha.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun NavDialOverlay(
    mode: String,
    hebrew: Boolean,
    parshiyot: Map<String, List<TorahNavigation.Parsha>>,
    ensureParshiyot: (String) -> Unit,
    onPick: (mode: String, ref: String, title: String, aliyot: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val gematria = remember { HebrewDateFormatter() }
    fun num(n: Int) = if (hebrew) gematria.formatHebrewNumber(n) else n.toString()
    val torah = mode == "parsha"
    var bookIdx by rememberSaveable { mutableStateOf(4) }
    var byParsha by rememberSaveable { mutableStateOf(true) }
    val book = TorahNavigation.BOOKS[bookIdx]
    if (torah && byParsha) LaunchedEffect(book.en) { ensureParshiyot(book.en) }

    Box(
        Modifier.fillMaxSize().background(Color(0xB3000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides if (hebrew) LayoutDirection.Rtl else LayoutDirection.Ltr) { Column(
            Modifier.fillMaxWidth(0.9f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (torah) {
                // The five books overflow narrow screens; keep the active one in view.
                val activeBook = remember { BringIntoViewRequester() }
                LaunchedEffect(bookIdx) { activeBook.bringIntoView() }
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    TorahNavigation.BOOKS.forEachIndexed { i, b ->
                        FlameToggle(
                            if (hebrew) b.he else b.en, active = i == bookIdx,
                            modifier = if (i == bookIdx) Modifier.bringIntoViewRequester(activeBook) else Modifier
                        ) { bookIdx = i }
                    }
                }
                Row(Modifier.padding(top = 6.dp)) {
                    FlameToggle(if (hebrew) "פרשה" else "Parsha", active = byParsha) { byParsha = true }
                    FlameToggle(if (hebrew) "פרק" else "Chapter", active = !byParsha) { byParsha = false }
                }
                Spacer(Modifier.height(10.dp))
                if (byParsha) {
                    val list = parshiyot[book.en]
                    if (list == null) {
                        CircularProgressIndicator(color = ACTIVE_GOLD, modifier = Modifier.padding(24.dp))
                    } else {
                        LazyColumn(Modifier.fillMaxHeight(0.55f).fillMaxWidth()) {
                            items(list) { pr ->
                                Text(
                                    if (hebrew) pr.he else pr.en,
                                    color = INACTIVE, fontSize = 17.sp,
                                    textAlign = TextAlign.Start, // layout direction already follows the language
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { onPick("parsha", pr.dottedRef, if (hebrew) "פרשת ${pr.he}" else "Parashat ${pr.en}", pr.aliyot) }
                                        .padding(vertical = 9.dp, horizontal = 12.dp)
                                )
                            }
                        }
                    }
                } else {
                    RotaryDial(min = 1, max = book.chapters, label = { num(it) }) { ch ->
                        onPick("parsha", "${book.en}.$ch", if (hebrew) "${book.he} פרק ${num(ch)}" else "${book.en} $ch", emptyList())
                    }
                }
            } else {
                Text(if (hebrew) "תהילים" else "Tehillim", color = ACTIVE_GOLD, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                RotaryDial(min = 1, max = TorahNavigation.TEHILLIM_CHAPTERS, label = { num(it) }) { ch ->
                    onPick("tehillim", "Psalms.$ch", if (hebrew) "תהילים פרק ${num(ch)}" else "Psalm $ch", emptyList())
                }
            }
        } }
    }
}

/**
 * A rotary dial: drag around the ring to turn (steps per turn scale with the range), tap
 * the center to jump. Tiny ± nudges for precision.
 */
@Composable
private fun RotaryDial(min: Int, max: Int, label: (Int) -> String, onGo: (Int) -> Unit) {
    var value by rememberSaveable { mutableStateOf(min) }
    var acc by remember { mutableStateOf(0f) }
    var lastAngle by remember { mutableStateOf<Float?>(null) }
    val stepsPerTurn = TorahNavigation.dialStepsPerTurn(max - min + 1).toFloat()
    val ringColor = Color.White.copy(alpha = 0.35f)

    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(min, max) {
                detectDragGestures(
                    onDragStart = { lastAngle = null },
                    onDragEnd = { lastAngle = null }
                ) { change, _ ->
                    val cx = size.width / 2f; val cy = size.height / 2f
                    val a = atan2(change.position.y - cy, change.position.x - cx)
                    lastAngle?.let { prev ->
                        var d = a - prev
                        if (d > PI) d -= (2 * PI).toFloat()
                        if (d < -PI) d += (2 * PI).toFloat()
                        acc += d
                        val steps = (acc / (2 * PI).toFloat() * stepsPerTurn).toInt()
                        if (steps != 0) {
                            value = (value + steps).coerceIn(min, max)
                            acc -= steps / stepsPerTurn * (2 * PI).toFloat()
                        }
                    }
                    lastAngle = a
                    change.consume()
                }
            }
        ) {
            val r = size.minDimension / 2f - 14.dp.toPx()
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(ringColor, radius = r, center = c, style = Stroke(width = 10.dp.toPx()))
            // progress arc through the range
            val sweep = 360f * (value - min).toFloat() / (max - min).coerceAtLeast(1)
            drawArc(
                ACTIVE_GOLD, startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                style = Stroke(width = 10.dp.toPx())
            )
            // ticks
            for (i in 0 until 24) {
                val ang = (i / 24f) * 2f * PI.toFloat()
                val inner = r - 16.dp.toPx(); val outer = r - 9.dp.toPx()
                drawLine(
                    Color.White.copy(alpha = 0.25f),
                    Offset(c.x + inner * cos(ang), c.y + inner * sin(ang)),
                    Offset(c.x + outer * cos(ang), c.y + outer * sin(ang)),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label(value), color = Color.White, fontSize = 40.sp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { onGo(value) }
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("−", color = INACTIVE, fontSize = 26.sp, modifier = Modifier.clickable { value = (value - 1).coerceIn(min, max) }.padding(horizontal = 14.dp))
                    FlameGoButton { onGo(value) }
                    Text("+", color = INACTIVE, fontSize = 26.sp, modifier = Modifier.clickable { value = (value + 1).coerceIn(min, max) }.padding(horizontal = 14.dp))
                }
            }
        }
    }
}

private fun chapterSections(
    text: SefariaText,
    mode: String,
    bookNameEn: String?,
    hebrew: Boolean,
    gematria: HebrewDateFormatter
): List<ReaderSection> = text.chapters.map { ch ->
    val header = when {
        hebrew -> "פרק ${gematria.formatHebrewNumber(ch.number)}"
        mode == "parsha" -> "${bookNameEn ?: "Chapter"} ${ch.number}"
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
