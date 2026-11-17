package com.oam.pdftools.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.oam.pdftools.work.PdfWorker
import kotlinx.coroutines.*
import java.util.UUID

/**
 * AdMob with guardrails:
 * - Interstitial: only after a saved result, never in a user's first [FREE_TASKS] tasks, ≥ [COOLDOWN_MS] apart.
 * - Rewarded: only for batches over [FREE_LIMIT]; single-file work is always free. If no ad is ready, the user is NOT blocked.
 * - Native: one card on Home (see NativeAdCard).
 */
object Ads {
    // ⚠ Google TEST IDs. Replace all four (incl. APPLICATION_ID in AndroidManifest.xml) before release.
    const val NATIVE = "ca-app-pub-3940256099942544/2247696110"
    private const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    private const val REWARDED = "ca-app-pub-3940256099942544/5224354917"

    private const val FREE_TASKS = 2
    private const val COOLDOWN_MS = 210_000L // 3.5 min
    private val FREE_LIMIT = mapOf(
        PdfWorker.MERGE to 4, PdfWorker.IMG to 15, PdfWorker.SCAN to 15, PdfWorker.SPLIT to 10, PdfWorker.TO_IMAGE to 10
    )

    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var lastFullScreen = 0L
    private val counted = mutableSetOf<UUID>()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        prefs = app.getSharedPreferences("ads", Context.MODE_PRIVATE)
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(app) {}
            withContext(Dispatchers.Main) { loadInterstitial(); loadRewarded() }
        }
    }

    private fun request() = AdRequest.Builder().build()

    private fun loadInterstitial() = InterstitialAd.load(app, INTERSTITIAL, request(), object : InterstitialAdLoadCallback() {
        override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
        override fun onAdFailedToLoad(e: LoadAdError) { interstitial = null }
    })

    private fun loadRewarded() = RewardedAd.load(app, REWARDED, request(), object : RewardedAdLoadCallback() {
        override fun onAdLoaded(ad: RewardedAd) { rewarded = ad }
        override fun onAdFailedToLoad(e: LoadAdError) { rewarded = null }
    })

    /** Call when a job succeeds. Counts the task once and maybe shows an interstitial. */
    fun onTaskCompleted(activity: Activity, jobId: UUID) {
        if (!counted.add(jobId)) return
        val n = prefs.getInt("tasks", 0) + 1
        prefs.edit().putInt("tasks", n).apply()
        val now = SystemClock.elapsedRealtime()
        if (n <= FREE_TASKS || (lastFullScreen != 0L && now - lastFullScreen < COOLDOWN_MS)) return
        val ad = interstitial ?: return loadInterstitial()
        interstitial = null; lastFullScreen = now
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = loadInterstitial()
            override fun onAdFailedToShowFullScreenContent(e: AdError) = loadInterstitial()
        }
        ad.show(activity)
    }

    fun needsReward(op: String, count: Int) = count > (FREE_LIMIT[op] ?: Int.MAX_VALUE)
    fun gateText(op: String, count: Int) = "Up to ${FREE_LIMIT[op]} at once is free. Watch a short video to process all $count."

    /** Shows a rewarded ad; runs [onGranted] if earned, or right away if no ad is available. */
    fun showRewarded(activity: Activity, onGranted: () -> Unit) {
        val ad = rewarded ?: run { loadRewarded(); onGranted(); return }
        rewarded = null; lastFullScreen = SystemClock.elapsedRealtime() // no interstitial right after
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { loadRewarded(); if (earned) onGranted() }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { loadRewarded(); onGranted() }
        }
        ad.show(activity) { earned = true }
    }
}
