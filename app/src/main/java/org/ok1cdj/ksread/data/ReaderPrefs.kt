package org.ok1cdj.ksread.data

import android.content.Context

/** Global reading settings, persisted across app restarts. */
class ReaderPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("kread_prefs", Context.MODE_PRIVATE)

    var wpm: Int
        get() = prefs.getInt(KEY_WPM, DEFAULT_WPM)
        set(v) = prefs.edit().putInt(KEY_WPM, v.coerceIn(WPM_MIN, WPM_MAX)).apply()

    var fontSize: Int
        // Coerce on read too, so a value saved under an older, larger max is clamped.
        get() = prefs.getInt(KEY_FONT, DEFAULT_FONT).coerceIn(FONT_MIN, FONT_MAX)
        set(v) = prefs.edit().putInt(KEY_FONT, v.coerceIn(FONT_MIN, FONT_MAX)).apply()

    var uppercase: Boolean
        get() = prefs.getBoolean(KEY_UPPER, false)
        set(v) = prefs.edit().putBoolean(KEY_UPPER, v).apply()

    var hideFinished: Boolean
        get() = prefs.getBoolean(KEY_HIDE_FINISHED, false)
        set(v) = prefs.edit().putBoolean(KEY_HIDE_FINISHED, v).apply()

    var highlightFocus: Boolean
        get() = prefs.getBoolean(KEY_HIGHLIGHT, true)
        set(v) = prefs.edit().putBoolean(KEY_HIGHLIGHT, v).apply()

    var sortMode: SortMode
        get() = runCatching { SortMode.valueOf(prefs.getString(KEY_SORT, null) ?: "") }
            .getOrDefault(SortMode.PROGRESS)
        set(v) = prefs.edit().putString(KEY_SORT, v.name).apply()

    companion object {
        const val WPM_MIN = 50
        const val WPM_MAX = 1000
        const val WPM_STEP = 10
        const val DEFAULT_WPM = 250

        const val FONT_MIN = 16
        const val FONT_MAX = 40
        const val FONT_STEP = 2
        const val DEFAULT_FONT = 36

        private const val KEY_WPM = "wpm"
        private const val KEY_FONT = "font_size"
        private const val KEY_UPPER = "uppercase"
        private const val KEY_HIDE_FINISHED = "hide_finished"
        private const val KEY_HIGHLIGHT = "highlight_focus"
        private const val KEY_SORT = "sort_mode"
    }
}

/** How the library book list is ordered. */
enum class SortMode { PROGRESS, NAME }
