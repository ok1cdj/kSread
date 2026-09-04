package org.ok1cdj.ksread.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ok1cdj.ksread.data.BookEntry
import org.ok1cdj.ksread.data.Library
import org.ok1cdj.ksread.data.ReaderPrefs
import org.ok1cdj.ksread.data.SortMode
import org.ok1cdj.ksread.data.sortedBy
import org.ok1cdj.ksread.parser.BookImporter
import java.util.regex.Pattern

enum class Screen { LIBRARY, READER }

data class UiState(
    val screen: Screen = Screen.LIBRARY,
    val hasFolder: Boolean = false,
    val books: List<BookEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    // Reader
    val title: String = "",
    val words: List<String> = emptyList(),
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val wpm: Int = ReaderPrefs.DEFAULT_WPM,
    val fontSize: Int = ReaderPrefs.DEFAULT_FONT,
    val uppercase: Boolean = false,
    val sortMode: SortMode = SortMode.PROGRESS,
    val docUri: String? = null,
) {
    val finished: Boolean get() = words.isNotEmpty() && index >= words.size
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val library = Library(app)
    private val prefs = ReaderPrefs(app)

    private val _state = MutableStateFlow(
        UiState(
            hasFolder = library.folderUri != null,
            wpm = prefs.wpm,
            fontSize = prefs.fontSize,
            uppercase = prefs.uppercase,
            sortMode = prefs.sortMode,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    // Raw text kept so words can be rebuilt when the uppercase toggle flips.
    private var rawText: String = ""
    private var playJob: Job? = null

    init {
        refreshLibrary()
    }

    // ---- Library ----------------------------------------------------------

    fun onFolderPicked(treeUri: Uri) {
        library.folderUri = treeUri
        _state.update { it.copy(hasFolder = true) }
        refreshLibrary()
    }

    fun refreshLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val books = try {
                library.listBooks().sortedBy(_state.value.sortMode)
            } catch (e: Exception) {
                emptyList()
            }
            _state.update { it.copy(books = books, hasFolder = library.folderUri != null) }
        }
    }

    fun toggleSort() {
        val next = if (_state.value.sortMode == SortMode.PROGRESS) SortMode.NAME else SortMode.PROGRESS
        prefs.sortMode = next
        _state.update { it.copy(sortMode = next, books = it.books.sortedBy(next)) }
    }

    fun openBook(entry: BookEntry) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = getApplication<Application>().contentResolver
                    .openInputStream(entry.uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("empty")
                val text = BookImporter.extractText(entry.name, bytes)
                val savedIndex = library.getPosition(entry.uri.toString())
                withContext(Dispatchers.Main) {
                    startReader(entry.name, text, savedIndex, entry.uri.toString())
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    fun openText(text: String, title: String) {
        startReader(title, text, 0, null)
    }

    private fun startReader(title: String, text: String, index: Int, docUri: String?) {
        rawText = text
        val words = buildWords(text, _state.value.uppercase)
        val safeIndex = index.coerceIn(0, (words.size - 1).coerceAtLeast(0))
        _state.update {
            it.copy(
                screen = Screen.READER,
                loading = false,
                error = null,
                title = title,
                words = words,
                index = safeIndex,
                isPlaying = false,
                docUri = docUri,
            )
        }
    }

    fun leaveReader() {
        pause()
        savePosition()
        _state.update { it.copy(screen = Screen.LIBRARY, isPlaying = false) }
        refreshLibrary()
    }

    fun clearError() = _state.update { it.copy(error = null) }

    // ---- Playback ---------------------------------------------------------

    fun togglePlay() {
        if (_state.value.isPlaying) pause() else play()
    }

    private fun play() {
        val s = _state.value
        if (s.words.isEmpty()) return
        // Restart from the beginning if we're already at the end.
        if (s.index >= s.words.size) _state.update { it.copy(index = 0) }
        _state.update { it.copy(isPlaying = true) }
        playJob?.cancel()
        playJob = viewModelScope.launch {
            while (isActive) {
                val cur = _state.value
                if (cur.index >= cur.words.size) break
                delay(computeDelay(cur.words[cur.index], cur.wpm))
                val next = _state.value.index + 1
                _state.update { it.copy(index = next) }
                if (next % 20 == 0) savePosition()
            }
            _state.update { it.copy(isPlaying = false) }
            savePosition()
        }
    }

    fun pause() {
        playJob?.cancel()
        playJob = null
        if (_state.value.isPlaying) _state.update { it.copy(isPlaying = false) }
        savePosition()
    }

    fun stepPrev() {
        pause()
        _state.update { it.copy(index = (it.index - 1).coerceAtLeast(0)) }
    }

    fun stepNext() {
        pause()
        _state.update {
            it.copy(index = (it.index + 1).coerceAtMost((it.words.size - 1).coerceAtLeast(0)))
        }
    }

    fun reset() {
        pause()
        _state.update { it.copy(index = 0) }
    }

    fun jumpTo(target: Int) {
        pause()
        _state.update {
            it.copy(index = target.coerceIn(0, (it.words.size - 1).coerceAtLeast(0)))
        }
    }

    // ---- Settings ---------------------------------------------------------

    fun changeWpm(delta: Int) {
        val v = (_state.value.wpm + delta).coerceIn(ReaderPrefs.WPM_MIN, ReaderPrefs.WPM_MAX)
        prefs.wpm = v
        _state.update { it.copy(wpm = v) }
    }

    fun changeFont(delta: Int) {
        val v = (_state.value.fontSize + delta).coerceIn(ReaderPrefs.FONT_MIN, ReaderPrefs.FONT_MAX)
        prefs.fontSize = v
        _state.update { it.copy(fontSize = v) }
    }

    fun toggleUppercase() {
        val newVal = !_state.value.uppercase
        prefs.uppercase = newVal
        // Word boundaries don't change with case, so the current index stays valid.
        val words = if (rawText.isNotEmpty()) buildWords(rawText, newVal) else emptyList()
        _state.update { it.copy(uppercase = newVal, words = words) }
    }

    // ---- Internals --------------------------------------------------------

    private fun savePosition() {
        val s = _state.value
        val uri = s.docUri ?: return
        library.savePosition(uri, s.index.coerceAtMost(s.words.size), s.words.size)
    }

    companion object {
        // Android's regex engine matches Unicode categories (\p{L}, \p{N}) by
        // default; the JDK's UNICODE_CHARACTER_CLASS flag is unsupported here.
        private val SENTENCE_SPACE =
            Pattern.compile("([\\p{L}\\p{N}])\\.([\\p{L}\\p{N}])")
        private val WHITESPACE = Regex("\\s+")

        fun buildWords(text: String, uppercase: Boolean): List<String> {
            var t = SENTENCE_SPACE.matcher(text).replaceAll("$1. $2")
            if (uppercase) t = t.uppercase()
            return t.split(WHITESPACE).filter { it.isNotEmpty() }
        }

        /** Per-word display time in ms with punctuation-aware pauses (SwiftRead). */
        fun computeDelay(word: String, wpm: Int): Long {
            var delay = 60000.0 / wpm
            val last = word.lastOrNull()
            when {
                last == '.' || last == '!' || last == '?' -> delay *= 2.0
                last == ',' || last == ';' || last == ':' -> delay *= 1.5
                word.length > 10 -> delay *= 1.3
            }
            return delay.toLong()
        }
    }
}
