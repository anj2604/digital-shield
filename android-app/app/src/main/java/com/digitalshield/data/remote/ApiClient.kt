package com.digitalshield.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    // 10.0.2.2 = the host machine, as seen from the Android emulator
    private const val BASE_URL = "http://10.0.2.2:8000/"

    val smsApi: SmsApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SmsApi::class.java)
    }
}