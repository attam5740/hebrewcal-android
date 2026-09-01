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
import androidx.compose.foundation.gestures.calculateZoom
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
import com.hebrewcal.R
import com.hebrewcal.data.AliyotPartitioner
import com.hebrewcal.data.CalendarLanguage
import com.hebrewcal.data.HebrewVocalization
import com.hebrewcal.data.SefariaText
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
        vm.load(mode, ref, title, diaspora, elulRef)

        setContent {
            val state by vm.state.collectAsState()
            val persistedSize by vm.textSize.collectAsState()
            var textMode by rememberSaveable { mutableStateOf(textMode0) }
            var nikkud by rememberSaveable { mutableStateOf(nikkud0) }
            var teamim by rememberSaveable { mutableStateOf(teamim0) }
            var byAliyot by rememberSaveable { mutableStateOf(false) }
            var barExpanded by rememberSaveable { mutableStateOf(false) }
            val contentInteractionSource = remember { MutableInteractionSource() }

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
                        onTextMode = { m ->
                            textMode = m
                        },
                        onNikkud = { nikkud = !nikkud; if (!nikkud) teamim = false },
                        onTeamim = { teamim = !teamim; if (teamim) nikkud = true },
                        onAliyot = { byAliyot = !byAliyot },
                        onClose = { finish() }
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
                            onZoom = { factor -> textSize = (textSize * factor).coerceIn(14f, 40f) }
                        )
                    }
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
    }
}

private val ACTIVE_GOLD = Color(0xFFFFD255)   // vivid warm amber
private val ACTIVE_BG = Color(0x47FFC94D)     // stronger amber pill behind active toggles
private val INACTIVE = Color(0xE6FFFFFF)      // near-white, clearly legible
private val DISABLED = Color(0x66FFFFFF)

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
 * Two slim translucent rows: [✕  title] then the toggles — language (EN/עב/תיקון),
 * vocalization (ניקוד/טעמים), and, for a parsha, partitioning (עליות).
 */
@Composable
private fun ReaderBar(
    title: String, expanded: Boolean, onToggleExpanded: () -> Unit,
    textMode: String, nikkud: Boolean, teamim: Boolean,
    showAliyotToggle: Boolean, byAliyot: Boolean,
    onTextMode: (String) -> Unit, onNikkud: () -> Unit, onTeamim: () -> Unit,
    onAliyot: () -> Unit, onClose: () -> Unit
) {
    val tikkun = textMode == MODE_TIKKUN
    Column(Modifier.fillMaxWidth().background(Color(0x73181818))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("✕", color = INACTIVE, fontSize = 14.sp) }
            Text(
                title, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
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
            BarToggle("תיקון", active = tikkun) { onTextMode(MODE_TIKKUN) }
            Spacer(Modifier.width(10.dp))
            BarToggle("ניקוד", active = nikkud && !tikkun, enabled = !tikkun, onClick = onNikkud)
            BarToggle("טעמים", active = teamim && nikkud && !tikkun, enabled = nikkud && !tikkun, onClick = onTeamim)
            if (showAliyotToggle) {
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
    onZoom: (Float) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val hebrew = textMode != MODE_EN
    val tikkun = textMode == MODE_TIKKUN
    val gematria = remember { HebrewDateFormatter() }
    val stam = remember { FontFamily(Font(R.font.stam_ashkenaz)) }
    val gold = Color(0xFFD4AF37)

    fun verseBody(heRaw: String, en: String): String = when {
        tikkun -> HebrewVocalization.strip(heRaw, showNikkud = false, showTeamim = false)
        hebrew -> HebrewVocalization.strip(heRaw, nikkud, teamim)
        else -> en
    }

    // Section list: one per aliyah when partitioning, else one per chapter.
    val sections = remember(text, mode, bookNameEn, aliyot, hebrew, elulText) {
        val main = if (aliyot.isNotEmpty()) {
            val parts = AliyotPartitioner.partition(text.chapters, aliyot)
            if (parts.isNotEmpty()) {
                parts.map { sec ->
                    ReaderSection(AliyotPartitioner.aliyahName(sec.index, aliyot.size, hebrew), sec.verses, markChapters = true)
                }
            } else chapterSections(text, mode, bookNameEn, hebrew, gematria)
        } else chapterSections(text, mode, bookNameEn, hebrew, gematria)
        val extra = elulText?.let { et ->
            chapterSections(et, mode, bookNameEn, hebrew, gematria).map { it.copy(extra = true) }
        } ?: emptyList()
        main + extra
    }

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
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                itemsIndexed(sections) { index, section ->
                    val mint = Color(0xFF6EE7B7)
                    if (section.extra && (index == 0 || !sections[index - 1].extra)) {
                        // Visual separation for the Elul supplement: rule + labeled header.
                        Box(
                            Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 2.dp)
                                .height(1.dp).background(mint.copy(alpha = 0.45f))
                        ) {}
                        Text(
                            if (hebrew) "תהילים לאלול" else "Elul Tehillim",
                            color = mint, fontSize = (sizeSp * 0.8f).sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            textAlign = TextAlign.Start
                        )
                    }
                    Text(
                        section.title, color = if (section.extra) mint else gold, fontSize = (sizeSp + 2).sp,
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
            }
        }
        // Fast-scroll thumb: drag maps to section index. (CenterEnd flips to the left
        // edge in RTL, which is where a Hebrew reader expects the scroller.)
        FastScrollThumb(listState, sections.size + 1, Modifier.align(Alignment.CenterEnd))
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
