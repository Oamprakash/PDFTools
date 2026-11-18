package com.oam.pdftools.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.google.android.gms.ads.*
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.interstitial.InterstitialAd
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.oam.pdftools.Rating
import com.oam.pdftools.billing.Billing
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
    }

    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Starts the SDK and preloads ads. Call only once consent allows it (see Consent). Safe to call repeatedly. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(app) { s -> android.util.Log.d("Ads", "Init: ${s.adapterStatusMap.mapValues { it.value.initializationState }}") }
            withContext(Dispatchers.Main) { loadInterstitial(); loadRewarded(); loadNative() }
        }
    }

    private fun request() = AdRequest.Builder().build()

    private fun loadInterstitial() = InterstitialAd.load(app, INTERSTITIAL, request(), object : InterstitialAdLoadCallback() {
        override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
        override fun onAdFailedToLoad(e: LoadAdError) { interstitial = null; android.util.Log.w("Ads", "Interstitial failed: ${e.code} ${e.message}") }
    })

    private fun loadRewarded() = RewardedAd.load(app, REWARDED, request(), object : RewardedAdLoadCallback() {
        override fun onAdLoaded(ad: RewardedAd) { rewarded = ad }
        override fun onAdFailedToLoad(e: LoadAdError) { rewarded = null; android.util.Log.w("Ads", "Rewarded failed: ${e.code} ${e.message}") }
    })

    // One shared native ad, kept outside composition so list changes / navigation don't destroy and reload it.
    val native = mutableStateOf<NativeAd?>(null)
    val nativeError = mutableStateOf<String?>(null)
    private var nativeAt = 0L
    private var nativeLoading = false

    private fun loadNative() {
        if (nativeLoading || Billing.adFree.value) return
        nativeLoading = true
        android.util.Log.d("Ads", "Native: loading")
        AdLoader.Builder(app, NATIVE)
            .forNativeAd { ad ->
                android.util.Log.d("Ads", "Native: loaded")
                native.value?.destroy(); native.value = ad; nativeError.value = null
                nativeAt = SystemClock.elapsedRealtime(); nativeLoading = false
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(e: LoadAdError) {
                    android.util.Log.w("Ads", "Native failed: code=${e.code} ${e.message}")
                    nativeError.value = "code ${e.code}: ${e.message}"; nativeLoading = false
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT).build())
            .build().loadAd(request())
    }

    /** Native ads expire after ~1 h; refresh when Home is shown and the ad is old or missing. */
    fun refreshNativeIfStale() {
        if (!started.get()) return
        if (native.value == null || SystemClock.elapsedRealtime() - nativeAt > 50 * 60_000L) loadNative()
    }

    /** Call when a job succeeds. Counts the task once and maybe shows an interstitial. */
    fun onTaskCompleted(activity: Activity, jobId: UUID) {
        if (!counted.add(jobId)) return
        val n = prefs.getInt("tasks", 0) + 1
        prefs.edit().putInt("tasks", n).apply()
        if (Rating.maybeAsk(activity, n) || Billing.adFree.value) return // never stack a review and an ad
        val now = SystemClock.elapsedRealtime()
        if (n <= FREE_TASKS || (lastFullScreen != 0L && now - lastFullScreen < COOLDOWN_MS)) return
        val ad = interstitial ?: run { if (started.get()) loadInterstitial(); return }
        interstitial = null; lastFullScreen = now
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = loadInterstitial()
            override fun onAdFailedToShowFullScreenContent(e: AdError) = loadInterstitial()
        }
        ad.show(activity)
    }

    fun needsReward(op: String, count: Int) = !Billing.adFree.value && count > (FREE_LIMIT[op] ?: Int.MAX_VALUE)
    fun gateText(op: String, count: Int) = "Up to ${FREE_LIMIT[op]} at once is free. Watch a short video to process all $count."

    /** Shows a rewarded ad; runs [onGranted] if earned, or right away if no ad is available. */
    fun showRewarded(activity: Activity, onGranted: () -> Unit) {
        val ad = rewarded ?: run { if (started.get()) loadRewarded(); onGranted(); return }
        rewarded = null; lastFullScreen = SystemClock.elapsedRealtime() // no interstitial right after
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { loadRewarded(); if (earned) onGranted() }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { loadRewarded(); onGranted() }
        }
        ad.show(activity) { earned = true }
    }
}
