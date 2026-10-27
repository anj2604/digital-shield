package com.digitalshield.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    // 10.0.2.2 = the host machine, as seen from the Android emulator
    private const val BASE_URL = "http://10.0.2.2:8000/"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val smsApi: SmsApi by lazy { retrofit.create(SmsApi::class.java) }
    val callApi: CallApi by lazy { retrofit.create(CallApi::class.java) }
    val screenshotApi: ScreenshotApi by lazy { retrofit.create(ScreenshotApi::class.java) }

}