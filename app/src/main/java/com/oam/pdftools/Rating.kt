package com.oam.pdftools

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory

/** In-app review: asked after the 3rd successful task, at most once every 90 days. Google may still choose not to show it. */
object Rating {
    private const val MIN_TASKS = 3
    private const val GAP_MS = 90L * 24 * 3600 * 1000

    /** Returns true if a review was requested (so the caller skips other full-screen UI, e.g. an interstitial). */
    fun maybeAsk(activity: Activity, tasks: Int): Boolean {
        val prefs = activity.getSharedPreferences("rating", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (tasks < MIN_TASKS || now - prefs.getLong("askedAt", 0) < GAP_MS) return false
        prefs.edit().putLong("askedAt", now).apply()
        val m = ReviewManagerFactory.create(activity)
        m.requestReviewFlow().addOnCompleteListener { t -> if (t.isSuccessful) m.launchReviewFlow(activity, t.result) }
        return true
    }

    fun openStore(ctx: Context) = runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ctx.packageName}")))
    }.recoverCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${ctx.packageName}")))
    }
}
