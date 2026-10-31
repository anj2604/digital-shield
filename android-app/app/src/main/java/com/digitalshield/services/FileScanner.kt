package com.digitalshield.services

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.digitalshield.data.remote.FileFinding
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

data class LocalScan(
    val name: String, val size: Long, val sha256: String,
    val detectedType: String, val findings: List<FileFinding>
)

object FileScanner {
    private const val MAX_DEEP = 100L * 1024 * 1024        // inspect contents up to 100 MB
    private const val MAX_PDF_READ = 20 * 1024 * 1024

    private val REMOTE_ACCESS = mapOf(
        "com.anydesk.anydeskandroid" to "AnyDesk",
        "com.teamviewer.quicksupport.market" to "TeamViewer QuickSupport",
        "com.teamviewer.teamviewer.market.mobile" to "TeamViewer",
        "com.carriez.flutter_hbb" to "RustDesk",
        "com.rustdesk.rustdesk" to "RustDesk",
        "com.sand.airdroid" to "AirDroid",
        "com.sand.airmirror" to "AirMirror",
        "com.realvnc.viewer.android" to "VNC Viewer"
    )
    private val SCRIPT_EXT = setOf("exe", "msi", "bat", "cmd", "scr", "js", "vbs", "ps1", "jar", "sh", "com", "hta", "lnk")
    private val LURE = listOf("cbi", "police", "arrest", "warrant", "fir", "court", "notice", "kyc",
        "refund", "aadhaar", "customs", "incometax", "income_tax", "rbi", "summons")
    private val EXT_TYPE = mapOf(
        "pdf" to "pdf", "apk" to "apk", "zip" to "zip", "exe" to "exe",
        "docx" to "office", "docm" to "office", "xlsx" to "office", "xlsm" to "office", "pptx" to "office",
        "jpg" to "jpg", "jpeg" to "jpg", "png" to "png", "gif" to "gif"
    )

    fun scan(context: Context, file: File, name: String): LocalScan {
        val findings = mutableListOf<FileFinding>()
        val add: (String, String, Int) -> Unit = { code, label, weight ->
            if (findings.none { it.code == code }) findings.add(FileFinding(code, label, weight))
        }
        val sha = sha256(file)
        val lower = name.lowercase()
        val ext = lower.substringAfterLast('.', "")
        val detected = detectType(file)

        // 1. Name tricks
        val parts = lower.split('.')
        if (parts.size >= 3 && parts[parts.size - 2] in setOf("pdf", "jpg", "jpeg", "png", "doc", "docx", "txt", "mp4")) {
            add("double_extension", "Double extension (.${parts.takeLast(2).joinToString(".")}) hides the real file type", 25)
        }
        val expected = EXT_TYPE[ext]
        if (expected != null && detected != "unknown" && expected != detected && !(expected == "zip" && detected == "office")) {
            add("type_mismatch", "Named .$ext but it is really a ${describe(detected)}", 40)
        }

        // 2. Dangerous types
        when {
            detected == "apk" || ext == "apk" ->
                add("apk", "Android app installer (APK). Scammers send these to take over phones", 35)
            detected == "exe" || detected == "elf" || ext in SCRIPT_EXT ->
                add("program", "Program or script file, not a normal document", 40)
        }
        val risky = findings.any { it.code in setOf("apk", "program", "type_mismatch", "double_extension") }
        if (risky && LURE.any { lower.contains(it) }) {
            add("lure_name", "File name uses pressure words (police, arrest, KYC…) on a program", 20)
        }

        // 3. Look inside, without opening or running
        if (file.length() > MAX_DEEP) {
            add("too_large", "Too large to check inside; only the fingerprint was checked", 10)
        } else when (detected) {
            "pdf" -> scanPdf(file, add)
            "apk" -> scanApk(context, file, add)
            "office" -> scanZip(file, office = true, add = add)
            "zip" -> scanZip(file, office = false, add = add)
        }
        return LocalScan(name, file.length(), sha, detected, findings)
    }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r < 0) break
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun describe(t: String) = when (t) {
        "apk" -> "Android app (APK)"
        "exe" -> "Windows program"
        "elf" -> "Linux program"
        "pdf" -> "PDF"
        "office" -> "Office document"
        "zip" -> "ZIP archive"
        "jpg", "png", "gif" -> "image"
        else -> "unknown file"
    }

    fun mimeFor(t: String, fallback: String?) = when (t) {
        "pdf" -> "application/pdf"
        "jpg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        else -> fallback ?: "*/*"
    }

    private fun detectType(file: File): String {
        val head = ByteArray(8)
        val n = FileInputStream(file).use { it.read(head) }
        if (n < 4) return "unknown"
        fun starts(vararg b: Int) = b.indices.all { head[it] == b[it].toByte() }
        return when {
            starts(0x25, 0x50, 0x44, 0x46) -> "pdf"           // %PDF
            starts(0x50, 0x4B, 0x03, 0x04) -> zipKind(file)    // PK..
            starts(0x4D, 0x5A) -> "exe"                        // MZ
            starts(0x7F, 0x45, 0x4C, 0x46) -> "elf"
            starts(0xFF, 0xD8, 0xFF) -> "jpg"
            starts(0x89, 0x50, 0x4E, 0x47) -> "png"
            starts(0x47, 0x49, 0x46, 0x38) -> "gif"
            else -> "unknown"
        }
    }

    private fun zipKind(file: File): String {
        val names = try {
            ZipInputStream(FileInputStream(file)).use { z ->
                generateSequence { z.nextEntry }.take(3000).map { it.name }.toList()
            }
        } catch (e: Exception) { emptyList() }
        return when {
            "AndroidManifest.xml" in names && names.any { it.endsWith(".dex") } -> "apk"
            "[Content_Types].xml" in names -> "office"
            else -> "zip"
        }
    }

    private fun scanZip(file: File, office: Boolean, add: (String, String, Int) -> Unit) {
        try {
            ZipInputStream(FileInputStream(file)).use { z ->
                var count = 0
                while (true) {
                    val e = z.nextEntry ?: break
                    if (++count > 5000) break
                    val n = e.name.lowercase()
                    if (office && n.endsWith("vbaproject.bin")) {
                        add("macro", "Document contains macros (hidden programs)", 35)
                    }
                    if (!office && (n.endsWith(".apk") || SCRIPT_EXT.any { n.endsWith(".$it") })) {
                        add("hidden_program", "Archive hides a program inside (${e.name.substringAfterLast('/')})", 35)
                    }
                }
            }
        } catch (e: ZipException) {
            if (e.message?.contains("encrypted", ignoreCase = true) == true) {
                add("encrypted_zip", "Password-protected archive: its contents cannot be checked", 20)
            }
        } catch (e: Exception) { /* unreadable archive: fingerprint check still applies */ }
    }

    private fun scanPdf(file: File, add: (String, String, Int) -> Unit) {
        val buf = ByteArray(minOf(file.length(), MAX_PDF_READ.toLong()).toInt())
        var len = 0
        FileInputStream(file).use { input ->
            while (len < buf.size) {
                val r = input.read(buf, len, buf.size - len)
                if (r < 0) break
                len += r
            }
        }
        val text = String(buf, 0, len, Charsets.ISO_8859_1)    // raw bytes, nothing is rendered
        if (text.contains("/JavaScript") || Regex("/JS[\\s(<\\[/]").containsMatchIn(text)) {
            add("pdf_js", "PDF contains JavaScript code", 30)
        }
        if (text.contains("/Launch")) add("pdf_launch", "PDF can launch other programs", 35)
        if (text.contains("/EmbeddedFile")) add("pdf_embedded", "PDF hides attached files", 15)
        if (text.contains("/OpenAction") || text.contains("/AA")) {
            add("pdf_autorun", "PDF runs actions automatically when opened", 10)
        }
    }

    @Suppress("DEPRECATION")
    private fun scanApk(context: Context, file: File, add: (String, String, Int) -> Unit) {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
        val info = (if (Build.VERSION.SDK_INT >= 33)
            pm.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else pm.getPackageArchiveInfo(file.path, flags))
        if (info == null) {
            add("apk_unreadable", "App installer could not be read; it may be damaged or disguised", 20)
            return
        }
        REMOTE_ACCESS[info.packageName]?.let {
            add("remote_access", "Remote-access app ($it): lets another person control your phone. Scammers ask victims to install these", 50)
        }
        val perms = info.requestedPermissions?.toSet() ?: emptySet()
        val sms = perms.any { it.endsWith("READ_SMS") || it.endsWith("RECEIVE_SMS") }
        if (sms && "android.permission.SYSTEM_ALERT_WINDOW" in perms) {
            add("sms_overlay", "Can read your SMS (OTPs) and draw over other apps", 30)
        } else if (sms) {
            add("reads_sms", "Can read your SMS, including OTPs", 15)
        }
        if (info.services?.any { it.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE" } == true) {
            add("accessibility", "Uses accessibility to watch and control the screen", 25)
        }
        if ("android.permission.REQUEST_INSTALL_PACKAGES" in perms) {
            add("installs_apps", "Can install other apps", 10)
        }
    }
}