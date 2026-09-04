package org.ok1cdj.ksread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.text.TextMMD
import org.ok1cdj.ksread.R
import org.ok1cdj.ksread.data.ReaderPrefs

// The word context is pure black for maximum e-ink contrast; the focus (ORP)
// letter is a lighter grey so the eye locks onto the fixation point.
private val FocusGrey = Color(0xFF8A8A8A)

@Composable
fun ReaderScreen(vm: MainViewModel, state: UiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .safeDrawingPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Top bar: book title only (Back lives next to Go at the bottom).
        TextMMD(
            text = state.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
        )

        // RSVP display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            // Fixation ticks above and below the centre column.
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                    .size(width = 2.dp, height = 14.dp).background(Color.Black)
            )
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                    .size(width = 2.dp, height = 14.dp).background(Color.Black)
            )

            if (state.finished || state.words.isEmpty()) {
                Text(
                    text = stringResource(R.string.finished),
                    fontSize = 40.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Black,
                )
            } else {
                OrpWord(word = state.words[state.index], fontSize = state.fontSize)
            }
        }

        // Progress + position + time
        val total = state.words.size
        val fraction = if (total > 0) state.index.toFloat() / total else 0f
        ProgressBar(fraction = fraction, modifier = Modifier.padding(vertical = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextMMD(
                text = stringResource(R.string.position_label, state.index.coerceAtMost(total), total),
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            TextMMD(
                text = stringResource(R.string.time_left, formatTimeLeft(total - state.index, state.wpm)),
                fontSize = 13.sp,
            )
        }

        Spacer(Modifier.height(6.dp))

        // Transport controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconAppButton(R.drawable.ic_reset, stringResource(R.string.reset), { vm.reset() }, Modifier.weight(1f))
            IconAppButton(R.drawable.ic_prev, stringResource(R.string.prev), { vm.stepPrev() }, Modifier.weight(1f))
            IconAppButton(
                iconRes = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                onClick = { vm.togglePlay() },
                modifier = Modifier.weight(1.4f),
                iconSize = 30,
            )
            IconAppButton(R.drawable.ic_next, stringResource(R.string.next), { vm.stepNext() }, Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        // WPM + font steppers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stepper(
                label = stringResource(R.string.wpm_label, state.wpm),
                onMinus = { vm.changeWpm(-ReaderPrefs.WPM_STEP) },
                onPlus = { vm.changeWpm(ReaderPrefs.WPM_STEP) },
                modifier = Modifier.weight(1f),
            )
            Stepper(
                label = stringResource(R.string.font_label, state.fontSize),
                onMinus = { vm.changeFont(-ReaderPrefs.FONT_STEP) },
                onPlus = { vm.changeFont(ReaderPrefs.FONT_STEP) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Jump-to-word + Back
        BottomRow(onJump = { vm.jumpTo(it) }, onBack = { vm.leaveReader() })
    }
}

/**
 * Splits a word at its Optimal Recognition Point and renders it centred at a
 * fixed font size, with the focus letter pinned to the middle column.
 */
@Composable
private fun OrpWord(word: String, fontSize: Int) {
    val orp = when {
        word.length <= 1 -> 0
        word.length <= 5 -> 1
        word.length <= 9 -> 2
        else -> 3
    }
    val before = word.substring(0, orp)
    val focus = word.getOrNull(orp)?.toString() ?: ""
    val after = if (orp + 1 <= word.length) word.substring((orp + 1).coerceAtMost(word.length)) else ""

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = before,
            fontSize = fontSize.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = focus,
            fontSize = fontSize.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = FocusGrey,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = after,
            fontSize = fontSize.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Start,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Stepper(label: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppButton(text = "−", onClick = onMinus, bold = true, fontSize = 18.sp)
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            TextMMD(text = label, fontSize = 13.sp)
        }
        AppButton(text = "+", onClick = onPlus, bold = true, fontSize = 18.sp)
    }
}

@Composable
private fun BottomRow(onJump: (Int) -> Unit, onBack: () -> Unit) {
    var value by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Small position input.
        OutlinedTextField(
            value = value,
            onValueChange = { s -> value = s.filter { it.isDigit() } },
            singleLine = true,
            label = { TextMMD(text = stringResource(R.string.jump_hint), fontSize = 12.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(110.dp),
        )
        AppButton(
            text = stringResource(R.string.jump),
            onClick = {
                value.toIntOrNull()?.let { onJump(it) }
                value = ""
            },
            fontSize = 14.sp,
        )
        // Push Back to the far right so it isn't hit by accident next to Go.
        Spacer(Modifier.weight(1f))
        AppButton(
            text = stringResource(R.string.back),
            onClick = onBack,
            fontSize = 14.sp,
        )
    }
}

private fun formatTimeLeft(wordsLeft: Int, wpm: Int): String {
    val left = wordsLeft.coerceAtLeast(0)
    val totalSeconds = (left.toDouble() / wpm * 60).toInt()
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m == 0 && s == 0 -> "0s"
        m == 0 -> "${s}s"
        else -> "${m}m ${s}s"
    }
}
