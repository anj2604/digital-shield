package com.digitalshield.data

import android.content.Context
import android.os.Environment
import java.io.File

data class QuarantineItem(val id: String, val name: String, val band: String, val sha256: String, val file: File)

object QuarantineStore {
    private fun dir(c: Context) = File(c.filesDir, "quarantine").apply { mkdirs() }
    private fun index(c: Context) = c.getSharedPreferences("quarantine_index", Context.MODE_PRIVATE)
    private fun trust(c: Context) = c.getSharedPreferences("file_trust", Context.MODE_PRIVATE)

    /** Moves a dangerous file into the app's private folder, where no other app can open it. */
    fun quarantine(c: Context, src: File, name: String, band: String, sha: String): Boolean {
        val id = System.currentTimeMillis().toString()
        val dest = File(dir(c), id)
        return try {
            src.inputStream().use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            if (!src.delete()) { dest.delete(); false }
            else { index(c).edit().putString(id, "$band|$sha|$name").apply(); true }
        } catch (e: Exception) { dest.delete(); false }
    }

    fun list(c: Context): List<QuarantineItem> = index(c).all.mapNotNull { (id, v) ->
        val p = (v as? String)?.split("|", limit = 3) ?: return@mapNotNull null
        QuarantineItem(id, p[2], p[0], p[1], File(dir(c), id))
    }.sortedByDescending { it.id }

    fun delete(c: Context, item: QuarantineItem) {
        item.file.delete()
        index(c).edit().remove(item.id).apply()
    }

    fun trusted(c: Context): Set<String> = trust(c).getStringSet("sha", emptySet()) ?: emptySet()

    /** The user decided they trust it: put it back in Download and don't flag it again. */
    fun restore(c: Context, item: QuarantineItem): Boolean = try {
        val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        var out = File(dl, item.name)
        if (out.exists()) out = File(dl, "restored_${item.name}")
        item.file.inputStream().use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        trust(c).edit().putStringSet("sha", trusted(c) + item.sha256).apply()
        delete(c, item)
        true
    } catch (e: Exception) { false }
}