package com.digitalshield

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.digitalshield.data.remote.ApiClient
import com.digitalshield.ui.theme.DigitalShieldTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ReportViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val caseId = intent.getStringExtra(EXTRA_CASE_ID)
        if (caseId == null) { finish(); return }
        setContent {
            DigitalShieldTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { p -> ReportViewer(Modifier.padding(p), caseId) }
            }
        }
    }

    companion object {
        const val EXTRA_CASE_ID = "case_id"
        fun intent(context: Context, caseId: String): Intent =
            Intent(context, ReportViewerActivity::class.java).putExtra(EXTRA_CASE_ID, caseId)
    }
}

@Composable
fun ReportViewer(modifier: Modifier, caseId: String) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(caseId) {
        try {
            pages = renderReport(context, caseId)
        } catch (e: Exception) {
            error = e.message ?: "Could not load the report"
        }
    }

    when {
        error != null -> Text("Report failed: $error", modifier.padding(16.dp))
        pages.isEmpty() -> Text("Loading report…", modifier.padding(16.dp))
        else -> LazyColumn(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pages) { page ->
                Image(bitmap = page.asImageBitmap(), contentDescription = "Report page",
                    modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

suspend fun renderReport(context: Context, caseId: String): List<Bitmap> =
    withContext(Dispatchers.IO) {
        // Download AND read the PDF on a background thread
        val bytes = ApiClient.caseApi.report(caseId).use { it.bytes() }
        val file = File(context.cacheDir, "case_$caseId.pdf").apply { writeBytes(bytes) }
        val result = mutableListOf<Bitmap>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            val renderer = PdfRenderer(pfd)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                result.add(bmp)
            }
            renderer.close()
        }
        result
    }