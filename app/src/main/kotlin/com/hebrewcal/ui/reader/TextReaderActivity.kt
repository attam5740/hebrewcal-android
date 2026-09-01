package com.hebrewcal.ui.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hebrewcal.data.CalendarLanguage
import com.hebrewcal.data.HebrewVocalization
import com.hebrewcal.data.SefariaText
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class TextReaderActivity : ComponentActivity() {
    private val vm: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: "tehillim"
        val ref = intent.getStringExtra(EXTRA_REF) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val diaspora = intent.getBooleanExtra(EXTRA_DIASPORA, true)
        val lang0 = if (intent.getStringExtra(EXTRA_LANG) == "HEBREW") CalendarLanguage.HEBREW else CalendarLanguage.ENGLISH
        val nikkud0 = intent.getBooleanExtra(EXTRA_NIKKUD, true)
        val teamim0 = intent.getBooleanExtra(EXTRA_TEAMIM, false)
        vm.load(mode, ref, title, diaspora)

        setContent {
            val state by vm.state.collectAsState()
            val persistedSize by vm.textSize.collectAsState()
            var langName by rememberSaveable { mutableStateOf(lang0.name) }
            val lang = if (langName == CalendarLanguage.HEBREW.name) CalendarLanguage.HEBREW else CalendarLanguage.ENGLISH
            var nikkud by rememberSaveable { mutableStateOf(nikkud0) }
            var teamim by rememberSaveable { mutableStateOf(teamim0) }
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
                // Inner column consumes clicks (no-op, no ripple) so taps on content don't dismiss.
                Column(
                    Modifier.fillMaxWidth().fillMaxHeight(0.92f).align(Alignment.Center).clickable(
                        interactionSource = contentInteractionSource,
                        indication = null
                    ) {}
                ) {
                    ReaderBar(
                        title = state.title,
                        lang = lang, nikkud = nikkud, teamim = teamim,
                        onLang = { langName = if (lang == CalendarLanguage.HEBREW) CalendarLanguage.ENGLISH.name else CalendarLanguage.HEBREW.name },
                        onNikkud = { nikkud = !nikkud; if (!nikkud) teamim = false },
                        onTeamim = { teamim = !teamim; if (teamim) nikkud = true },
                        onClose = { finish() }
                    )
                    when {
                        state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                        state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(if (lang == CalendarLanguage.HEBREW) "אין חיבור לאינטרנט" else "No internet connection", color = Color.White)
                        }
                        state.text != null -> ReaderBody(
                            text = state.text!!, lang = lang, nikkud = nikkud, teamim = teamim,
                            sizeSp = textSize, mode = state.mode, bookNameEn = state.bookNameEn,
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
    }
}

/**
 * Single slim, translucent control row: close, title, vocalization toggles, language.
 * Deliberately low-key — the text below is the star.
 */
@Composable
private fun ReaderBar(
    title: String, lang: CalendarLanguage, nikkud: Boolean, teamim: Boolean,
    onLang: () -> Unit, onNikkud: () -> Unit, onTeamim: () -> Unit, onClose: () -> Unit
) {
    val on  = Color(0xFFD4AF37)          // gold = active
    val off = Color.White.copy(alpha = 0.35f)
    Row(
        Modifier.fillMaxWidth().background(Color(0x59101020)).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onClose) { Text("✕", color = off, fontSize = 13.sp) }
        Text(
            title, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onNikkud) { Text("ניקוד", color = if (nikkud) on else off, fontSize = 12.sp) }
        TextButton(onClick = onTeamim, enabled = nikkud) { Text("טעמים", color = if (teamim && nikkud) on else off, fontSize = 12.sp) }
        TextButton(onClick = onLang) { Text(if (lang == CalendarLanguage.HEBREW) "EN" else "עב", color = off, fontSize = 12.sp) }
    }
}

/**
 * Continuous flowing text per chapter (like a printed Tanach): verses run inline,
 * separated by small gold verse numerals (gematria in Hebrew). Hebrew renders in a
 * true RTL layout. Pinch anywhere on the text to zoom.
 */
@Composable
private fun ReaderBody(
    text: SefariaText,
    lang: CalendarLanguage,
    nikkud: Boolean,
    teamim: Boolean,
    sizeSp: Float,
    mode: String = "tehillim",
    bookNameEn: String? = null,
    onZoom: (Float) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val hebrew = lang == CalendarLanguage.HEBREW
    val gematria = remember { HebrewDateFormatter() }
    val gold = Color(0xFFD4AF37)
    val zoomState = rememberTransformableState { zoomChange, _, _ -> onZoom(zoomChange) }

    val direction = if (hebrew) LayoutDirection.Rtl else LayoutDirection.Ltr
    Box(Modifier.fillMaxSize().transformable(zoomState)) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(text.chapters) { ch ->
                    val header = when {
                        hebrew -> "פרק ${gematria.formatHebrewNumber(ch.number)}"
                        mode == "parsha" -> "${bookNameEn ?: "Chapter"} ${ch.number}"
                        else -> "Psalm ${ch.number}"
                    }
                    Text(
                        header, color = gold, fontSize = (sizeSp + 2).sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
                        textAlign = TextAlign.Start
                    )
                    val paragraph = buildAnnotatedString {
                        ch.verses.forEach { v ->
                            val body = if (hebrew) HebrewVocalization.strip(v.he, nikkud, teamim) else v.en
                            if (body.isBlank()) return@forEach
                            withStyle(SpanStyle(color = gold, fontSize = (sizeSp * 0.62f).sp)) {
                                append(if (hebrew) gematria.formatHebrewNumber(v.num) else v.num.toString())
                            }
                            append(" ")
                            append(body)
                            append("  ")
                        }
                    }
                    Text(
                        paragraph,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = sizeSp.sp,
                            lineHeight = (sizeSp * 1.75f).sp,
                            textAlign = TextAlign.Start,
                            textDirection = TextDirection.Content
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { Text(if (hebrew) "מקור: ספריא" else "Source: Sefaria", color = Color(0xFFAA9977), fontSize = 11.sp, modifier = Modifier.padding(16.dp)) }
            }
        }
        // Fast-scroll thumb: drag maps to chapter index. (CenterEnd flips to the left
        // edge in RTL, which is where a Hebrew reader expects the scroller.)
        FastScrollThumb(listState, text.chapters.size + 1, Modifier.align(Alignment.CenterEnd))
    }
}

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
