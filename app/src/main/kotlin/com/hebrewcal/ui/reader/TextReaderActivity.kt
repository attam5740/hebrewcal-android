package com.hebrewcal.ui.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hebrewcal.data.CalendarLanguage
import com.hebrewcal.data.HebrewVocalization
import com.hebrewcal.data.SefariaText
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
            val sizeSp by vm.textSize.collectAsState()
            var langName by rememberSaveable { mutableStateOf(lang0.name) }
            val lang = if (langName == CalendarLanguage.HEBREW.name) CalendarLanguage.HEBREW else CalendarLanguage.ENGLISH
            var nikkud by rememberSaveable { mutableStateOf(nikkud0) }
            var teamim by rememberSaveable { mutableStateOf(teamim0) }
            val contentInteractionSource = remember { MutableInteractionSource() }
            // Local live slider value for smooth dragging; persisted to DataStore only on release.
            var sliderValue by remember { mutableStateOf(sizeSp) }
            LaunchedEffect(sizeSp) { sliderValue = sizeSp }

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
                        lang = lang, nikkud = nikkud, teamim = teamim, sizeSp = sliderValue,
                        onLang = { langName = if (lang == CalendarLanguage.HEBREW) CalendarLanguage.ENGLISH.name else CalendarLanguage.HEBREW.name },
                        onNikkud = { nikkud = it; if (!it) teamim = false },
                        onTeamim = { teamim = it; if (it) nikkud = true },
                        onSizeChange = { sliderValue = it },
                        onSizeChangeFinished = { vm.setTextSize(sliderValue) },
                        onClose = { finish() }
                    )
                    when {
                        state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                        state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(if (lang == CalendarLanguage.HEBREW) "אין חיבור לאינטרנט" else "No internet connection", color = Color.White)
                        }
                        state.text != null -> ReaderBody(state.text!!, lang, nikkud, teamim, sliderValue, state.mode, state.bookNameEn)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderBar(
    title: String, lang: CalendarLanguage, nikkud: Boolean, teamim: Boolean, sizeSp: Float,
    onLang: () -> Unit, onNikkud: (Boolean) -> Unit, onTeamim: (Boolean) -> Unit,
    onSizeChange: (Float) -> Unit, onSizeChangeFinished: () -> Unit, onClose: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF1A1A2E)).padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = onLang) { Text(if (lang == CalendarLanguage.HEBREW) "EN" else "עב", color = Color.White) }
            TextButton(onClick = onClose) { Text("✕", color = Color.White) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = nikkud, onClick = { onNikkud(!nikkud) }, label = { Text("ניקוד") })
            Spacer(Modifier.width(6.dp))
            FilterChip(selected = teamim, onClick = { onTeamim(!teamim) }, enabled = nikkud, label = { Text("טעמים") })
        }
        Slider(
            value = sizeSp,
            onValueChange = onSizeChange,
            onValueChangeFinished = onSizeChangeFinished,
            valueRange = 14f..34f
        )
    }
}

@Composable
private fun ReaderBody(
    text: SefariaText,
    lang: CalendarLanguage,
    nikkud: Boolean,
    teamim: Boolean,
    sizeSp: Float,
    mode: String = "tehillim",
    bookNameEn: String? = null
) {
    val listState = rememberLazyListState()
    val hebrew = lang == CalendarLanguage.HEBREW
    // Flatten to display rows for a simple fast-scrollable list.
    data class Row(val header: String?, val verseNum: Int?, val body: String)
    val rows = remember(text, lang, nikkud, teamim, mode, bookNameEn) {
        buildList {
            text.chapters.forEach { ch ->
                val header = when {
                    hebrew -> "פרק ${ch.number}"
                    mode == "parsha" -> "${bookNameEn ?: "Chapter"} ${ch.number}"
                    else -> "Psalm ${ch.number}"
                }
                add(Row(header, null, ""))
                ch.verses.forEach { v ->
                    val body = if (hebrew) HebrewVocalization.strip(v.he, nikkud, teamim) else v.en
                    add(Row(null, v.num, body))
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(rows) { row ->
                if (row.header != null) {
                    Text(
                        row.header, color = Color(0xFFD4AF37), fontSize = (sizeSp + 4).sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                        textAlign = if (hebrew) TextAlign.End else TextAlign.Start
                    )
                } else {
                    Text(
                        "${row.verseNum}. ${row.body}", color = Color.White, fontSize = sizeSp.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        textAlign = if (hebrew) TextAlign.End else TextAlign.Start
                    )
                }
            }
            item { Text(if (hebrew) "מקור: ספריא" else "Source: Sefaria", color = Color(0xFFAA9977), fontSize = 12.sp, modifier = Modifier.padding(16.dp)) }
        }
        // Fast-scroll thumb: drag maps to list index.
        FastScrollThumb(listState, rows.size, Modifier.align(Alignment.CenterEnd))
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
