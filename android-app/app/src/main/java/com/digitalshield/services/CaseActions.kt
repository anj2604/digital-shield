package com.digitalshield.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.digitalshield.data.CallStore
import com.digitalshield.data.ResultStore
import com.digitalshield.data.ScreenshotStore
import com.digitalshield.data.remote.ApiClient
import com.digitalshield.data.remote.CaseRequest
import com.digitalshield.data.remote.EvidenceRef

object CaseActions {
    suspend fun reportIncident(context: Context): String {
        val refs = buildList {
            ResultStore.results.value.filter { it.stored && it.id != null }.forEach { add(EvidenceRef("sms", it.id!!)) }
            ScreenshotStore.shots.value.forEach { add(EvidenceRef("screenshot", it.id)) }
            CallStore.calls.value.filter { it.stored && it.id != null }.forEach { add(EvidenceRef("call", it.id!!)) }
        }
        if (refs.isEmpty()) return "Nothing stored yet to report"
        val case = ApiClient.caseApi.create(
            CaseRequest("Suspected digital arrest scam", "Reported from the Digital Shield app", refs)
        )
        context.startActivity(
            com.digitalshield.ReportViewerActivity.intent(context, case.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return "Case created with ${refs.size} item(s), risk ${case.riskBand}"
    }
}