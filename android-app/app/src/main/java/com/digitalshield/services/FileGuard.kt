package com.digitalshield.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.digitalshield.FileGuardActivity
import com.digitalshield.data.remote.ApiClient
import com.digitalshield.data.remote.FileCheckRequest
import com.digitalshield.data.remote.FileFinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class FileVerdict(
    val name: String, val sha256: String, val band: String, val score: Int,
    val decision: String, val verdict: String, val findings: List<FileFinding>,
    val detectedType: String, val file: File, val offline: Boolean
)

object FileGuard {
    private fun bandOf(score: Int) = when {
        score >= 80 -> "CRITICAL"; score >= 60 -> "HIGH"; score >= 30 -> "MEDIUM"; else -> "LOW"
    }
    fun decisionOf(band: String) = when (band) { "LOW" -> "safe"; "MEDIUM" -> "caution"; else -> "blocked" }

    suspend fun check(context: Context, file: File, name: String, source: String): FileVerdict =
        withContext(Dispatchers.IO) {
            val local = FileScanner.scan(context, file, name)
            try {
                val r = ApiClient.fileApi.check(
                    FileCheckRequest(local.sha256, name, local.size, local.detectedType, local.findings, source)
                )
                FileVerdict(name, r.sha256, r.riskBand, r.riskScore, r.decision, r.verdict,
                    r.findings, local.detectedType, file, offline = false)
            } catch (e: Exception) {
                // Server unreachable: decide on the phone's own checks
                val score = local.findings.sumOf { it.weight }.coerceAtMost(100)
                val band = bandOf(score)
                FileVerdict(name, local.sha256, band, score, decisionOf(band),
                    "Checked on this phone only (server unreachable).", local.findings,
                    local.detectedType, file, offline = true)
            }
        }

    /** Copies the file into the app, so the exact scanned bytes are what gets opened later. */
    suspend fun seal(context: Context, uri: Uri): Pair<File, String> = withContext(Dispatchers.IO) {
        val name = displayName(context, uri) ?: "file"
        val dir = File(context.cacheDir, "safe_open").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, name.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        context.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            ?: throw IllegalStateException("Cannot read the file")
        out to name
    }

    suspend fun sealFile(context: Context, src: File): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "safe_open").apply { mkdirs() }
        val out = File(dir, src.name)
        src.inputStream().use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        out
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment

    fun open(context: Context, file: File, detectedType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val mime = FileScanner.mimeFor(detectedType, context.contentResolver.getType(uri))
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(view, "Open with")
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, FileGuardActivity::class.java)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}