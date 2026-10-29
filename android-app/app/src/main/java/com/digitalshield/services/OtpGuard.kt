package com.digitalshield.services

import android.content.Context
import android.media.AudioManager

object OtpGuard {
    private val otpWord = Regex("(?i)\\b(otp|one[ -]?time password|verification code|passcode)\\b")
    private val code = Regex("\\b\\d{4,8}\\b")

    fun looksLikeOtp(body: String) = otpWord.containsMatchIn(body) && code.containsMatchIn(body)

    // True during a normal call or a WhatsApp/VoIP call. No permission needed.
    fun isInCall(context: Context): Boolean {
        val mode = context.getSystemService(AudioManager::class.java).mode
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
    }
}