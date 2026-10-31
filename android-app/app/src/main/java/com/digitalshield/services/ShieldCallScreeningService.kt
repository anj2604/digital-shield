package com.digitalshield.services

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.digitalshield.data.CallStore
import com.digitalshield.data.remote.ApiClient
import com.digitalshield.data.remote.CallRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ShieldCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        // Always let the call through: we warn, the user decides
        respondToCall(callDetails, CallScreeningService.CallResponse.Builder().build())

        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return
        val number = callDetails.handle?.schemeSpecificPart ?: return
        val appContext = applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val known = ContactsHelper.isSavedContact(appContext, number)
                val result = ApiClient.callApi.analyzeCall(CallRequest(number, known))
                Log.d("DigitalShield", "call $number band=${result.riskBand} known=$known stored=${result.stored}")
                if (!known) CallStore.add(result)
                if (result.riskBand != "LOW") NotificationHelper.showCallWarning(appContext, result)
                if (result.riskBand != "LOW") ScamAlert.show(appContext, "Reported scam number calling",
                    "This number appears in scam reports. You can hang up. Never share OTPs or pay.",
                    number, result.riskBand, result.riskScore)
            } catch (e: Exception) {
                Log.e("DigitalShield", "Call analysis failed", e)
            }
        }
    }
}