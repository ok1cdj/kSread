package org.ok1cdj.ksread.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import org.ok1cdj.ksread.parser.BookImporter

/** One book found in the chosen folder, plus its saved reading progress. */
data class BookEntry(
    val name: String,
    val uri: Uri,
    val index: Int,
    val total: Int,
) {
    val percent: Int get() = if (total > 0) (index * 100 / total).coerceIn(0, 100) else 0
    val isFinished: Boolean get() = total > 0 && index >= total
    val isInProgress: Boolean get() = index > 0 && !isFinished
    val isUnread: Boolean get() = index == 0
}

/** Order books by the chosen [SortMode]. PROGRESS puts in-progress books first. */
fun List<BookEntry>.sortedBy(mode: SortMode): List<BookEntry> = when (mode) {
    SortMode.NAME -> sortedBy { it.name.lowercase() }
    SortMode.PROGRESS -> sortedWith(
        compareBy<BookEntry> {
            when {
                it.isInProgress -> 0
                it.isUnread -> 1
                else -> 2 // finished
            }
        }.thenByDescending { it.percent }
         .thenBy { it.name.lowercase() }
    )
}

/**
 * Persists the user-picked books folder (a SAF tree Uri) and the per-book
 * reading position. Books themselves are read on demand through the
 * ContentResolver — nothing is copied into app storage.
 */
class Library(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("kread_library", Context.MODE_PRIVATE)

    var folderUri: Uri?
        get() = prefs.getString(KEY_TREE, null)?.let { Uri.parse(it) }
        set(value) = prefs.edit().putString(KEY_TREE, value?.toString()).apply()

    /** Enumerate supported book files in the chosen folder. */
    fun listBooks(): List<BookEntry> {
        val tree = folderUri ?: return emptyList()
        val dir = DocumentFile.fromTreeUri(app, tree) ?: return emptyList()
        val positions = readPositions()
        return dir.listFiles()
            .filter { it.isFile && it.name != null && BookImporter.isSupported(it.name!!) }
            .map { doc ->
                val key = doc.uri.toString()
                val pos = positions.optJSONObject(key)
                BookEntry(
                    name = doc.name!!,
                    uri = doc.uri,
                    index = pos?.optInt("i", 0) ?: 0,
                    total = pos?.optInt("n", 0) ?: 0,
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    fun getPosition(docUri: String): Int =
        readPositions().optJSONObject(docUri)?.optInt("i", 0) ?: 0

    fun savePosition(docUri: String, index: Int, total: Int) {
        val positions = readPositions()
        positions.put(docUri, JSONObject().put("i", index).put("n", total))
        prefs.edit().putString(KEY_POSITIONS, positions.toString()).apply()
    }

    private fun readPositions(): JSONObject =
        try {
            JSONObject(prefs.getString(KEY_POSITIONS, "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }

    companion object {
        private const val KEY_TREE = "tree_uri"
        private const val KEY_POSITIONS = "positions"
    }
}
