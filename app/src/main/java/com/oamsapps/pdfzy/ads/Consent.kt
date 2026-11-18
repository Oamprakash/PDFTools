package com.oamsapps.pdfzy.ads

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.google.android.ump.*

/**
 * Google UMP consent (GDPR/UK, and US-state privacy messages). The form only appears where the law requires it
 * and only for messages you publish in AdMob → Privacy & messaging. Ads start only once [ConsentInformation.canRequestAds].
 */
object Consent {
    /**
     * To test the EU form on your phone in a debug build: run once, find the logcat line
     * "Use new ConsentDebugSettings.Builder().addTestDeviceHashedId("...")" and paste that ID here.
     */
    private const val TEST_DEVICE_HASH = ""

    /** True when the user must be able to change their choice later (shown as a Privacy button on Home). */
    val privacyOptionsRequired = mutableStateOf(false)

    fun gather(activity: Activity, onCanRequestAds: () -> Unit) {
        val ci = UserMessagingPlatform.getConsentInformation(activity)
        val debug = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val params = ConsentRequestParameters.Builder().apply {
            if (debug && TEST_DEVICE_HASH.isNotEmpty()) setConsentDebugSettings(
                ConsentDebugSettings.Builder(activity)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .addTestDeviceHashedId(TEST_DEVICE_HASH).build()
            )
        }.build()
        ci.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { err ->
                err?.let { Log.w("Ads", "Consent form: ${it.message}") }
                privacyOptionsRequired.value =
                    ci.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                if (ci.canRequestAds()) onCanRequestAds()
            }
        }, { err ->
            Log.w("Ads", "Consent info: ${err.message}")
            if (ci.canRequestAds()) onCanRequestAds()
        })
        // Consent given in an earlier session: start ads now, in parallel with the update above
        if (ci.canRequestAds()) onCanRequestAds()
    }

    fun showPrivacyOptions(activity: Activity) = UserMessagingPlatform.showPrivacyOptionsForm(activity) { err ->
        err?.let { Log.w("Ads", "Privacy options: ${it.message}") }
    }
}
