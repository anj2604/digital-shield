package com.digitalshield.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.digitalshield.data.LiveCallStore
import com.digitalshield.data.remote.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** Records the microphone in fixed-length chunks; each chunk is hashed, uploaded and scored. */
class ChunkRecorder(
    private val context: Context,
    private val callId: String,
    private val number: String,
    private val chunkSeconds: Int = 10
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var running = false

    @SuppressLint("MissingPermission")   // checked just below
    fun start() {
        if (running) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            LiveCallStore.start(callId, number)
            LiveCallStore.error("Microphone permission is off")
            LiveCallStore.finish()
            return
        }
        running = true
        LiveCallStore.start(callId, number)
        notifyStatus("Checking the call…")

        scope.launch {
            val rate = 16000
            val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate))
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                LiveCallStore.error("The microphone is not available on this phone during calls")
                running = false
                finishUp()
                return@launch
            }
            val buf = ShortArray(rate / 10)
            val pcm = ByteArrayOutputStream()
            var samples = 0
            var seq = 0
            rec.startRecording()
            try {
                while (running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) {
                        for (i in 0 until n) {
                            val v = buf[i].toInt()
                            pcm.write(v and 0xFF)
                            pcm.write((v shr 8) and 0xFF)
                        }
                        samples += n
                    }
                    if (samples >= rate * chunkSeconds) {
                        val bytes = pcm.toByteArray()
                        pcm.reset(); samples = 0
                        val s = seq++
                        scope.launch { upload(s, bytes) }
                    }
                }
            } finally {
                rec.stop()
                rec.release()
                if (samples >= rate) upload(seq, pcm.toByteArray())   // last partial chunk (1 s or more)
                finishUp()
            }
        }
    }

    fun stop() { running = false }

    private suspend fun finishUp() {
        try { ApiClient.callAudioApi.end(callId) } catch (e: Exception) { /* already ended */ }
        LiveCallStore.finish()
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }

    private suspend fun upload(seq: Int, pcm: ByteArray) {
        val wav = wavOf(pcm, 16000)
        val sha = MessageDigest.getInstance("SHA-256").digest(wav).joinToString("") { "%02x".format(it) }
        try {
            val text = MediaType.parse("text/plain")
            val r = ApiClient.callAudioApi.uploadChunk(
                callId,
                MultipartBody.Part.createFormData("file", "chunk_$seq.wav", RequestBody.create(MediaType.parse("audio/wav"), wav)),
                RequestBody.create(text, seq.toString()),
                RequestBody.create(text, sha),
                RequestBody.create(text, chunkSeconds.toString())
            )
            LiveCallStore.update(r)
            notifyStatus("Risk ${r.liveRisk.band} ${r.liveRisk.score}/100 · ${seq + 1} chunk(s) checked")
            if (r.liveRisk.band == "CRITICAL" && LiveCallStore.markAlerted()) {
                ScamAlert.show(context, "Scam detected in this call",
                    "The caller said things like: " + r.liveRisk.indicators.take(3).joinToString { it.replace('_', ' ') } + ".",
                    number, r.liveRisk.band, r.liveRisk.score)
            }
        } catch (e: Exception) {
            LiveCallStore.error("Chunk ${seq + 1} could not be checked: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyStatus(text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Call recording", NotificationManager.IMPORTANCE_LOW))
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("🔴 Recording unknown call: $number")
            .setContentText(text)
            .setOngoing(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID, n)
    }

    companion object {
        private const val CHANNEL = "call_recording"
        private const val NOTIF_ID = 7300

        /** Wraps raw 16-bit mono PCM in a WAV header so Whisper can read it. */
        fun wavOf(pcm: ByteArray, rate: Int): ByteArray {
            fun i32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
            fun i16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
            val out = ByteArrayOutputStream()
            out.write("RIFF".toByteArray()); out.write(i32(36 + pcm.size)); out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray()); out.write(i32(16)); out.write(i16(1)); out.write(i16(1))
            out.write(i32(rate)); out.write(i32(rate * 2)); out.write(i16(2)); out.write(i16(16))
            out.write("data".toByteArray()); out.write(i32(pcm.size)); out.write(pcm)
            return out.toByteArray()
        }
    }
}