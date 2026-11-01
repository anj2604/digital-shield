package com.digitalshield.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // 10.0.2.2 = the host machine, as seen from the Android emulator
    const val BASE_URL = "http://10.0.2.2:8000/"

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)    // first transcription loads the Whisper model
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val smsApi: SmsApi by lazy { retrofit.create(SmsApi::class.java) }
    val callApi: CallApi by lazy { retrofit.create(CallApi::class.java) }
    val screenshotApi: ScreenshotApi by lazy { retrofit.create(ScreenshotApi::class.java) }
    val caseApi: CaseApi by lazy { retrofit.create(CaseApi::class.java) }
    val linkApi: LinkApi by lazy { retrofit.create(LinkApi::class.java) }
    val fileApi: FileApi by lazy { retrofit.create(FileApi::class.java) }
    val callAudioApi: CallAudioApi by lazy { retrofit.create(CallAudioApi::class.java) }
}