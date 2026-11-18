package com.oam.pdftools.ads

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.oam.pdftools.billing.Billing
import com.oam.pdftools.findActivity

/** Asks before a batch that needs a rewarded ad. Usage: gate(needsReward, message) { proceed() } */
@Composable
fun rememberRewardGate(): (Boolean, String, () -> Unit) -> Unit {
    val act = LocalContext.current.findActivity()
    var pending by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    pending?.let { (msg, go) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Unlock this batch") }, text = { Text(msg) },
            confirmButton = { TextButton({ pending = null; Ads.showRewarded(act, go) }) { Text("Watch video") } },
            dismissButton = { TextButton({ pending = null }) { Text("Cancel") } }
        )
    }
    return remember { { gated, msg, go -> if (gated) pending = msg to go else go() } }
}

/** Small native ad card with a clear "Ad" badge and its own border, so it can't be mistaken for a file. */
@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    if (Billing.adFree.value) return
    val ctx = LocalContext.current
    val debug = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    LaunchedEffect(Unit) { Ads.refreshNativeIfStale() }
    val a = Ads.native.value ?: run {
        // Debug builds only: show why there's no ad instead of an empty gap
        Ads.nativeError.value?.let { if (debug) Text("Ad not loaded ($it)", modifier, style = MaterialTheme.typography.bodySmall) }
        return
    }
    val c = MaterialTheme.colorScheme
    val colors = AdColors(c.onSurface.toArgb(), c.onSurfaceVariant.toArgb(), c.primary.toArgb(), c.onPrimary.toArgb())
    OutlinedCard(modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = c.surfaceContainerLow)) {
        key(a) { AndroidView(::buildAdView, Modifier.fillMaxWidth()) { v -> bind(v, a, colors) } }
    }
}

private fun buildAdView(c: Context): NativeAdView {
    val d = c.resources.displayMetrics.density
    fun dp(x: Int) = (x * d).toInt()
    val icon = ImageView(c).apply { layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) } }
    val badge = TextView(c).apply {
        text = "Ad"; textSize = 11f; setTextColor(Color.WHITE); setBackgroundColor(0xFFB8860B.toInt()); setPadding(dp(4), 0, dp(4), 0)
    }
    val headline = TextView(c).apply {
        textSize = 15f; setTypeface(null, Typeface.BOLD); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(dp(6), 0, 0, 0)
    }
    val body = TextView(c).apply { textSize = 13f; maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
    val cta = TextView(c).apply { // pill button styled from the app theme (a platform Button ignores dark mode here)
        textSize = 14f; setPadding(dp(14), dp(8), dp(14), dp(8)); gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(8) }
    }
    val titleRow = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL; addView(badge); addView(headline) }
    val texts = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        addView(titleRow); addView(body)
    }
    val row = LinearLayout(c).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12))
        addView(icon); addView(texts); addView(cta)
    }
    return NativeAdView(c).apply {
        layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, WRAP_CONTENT)
        addView(row, android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, WRAP_CONTENT)); iconView = icon; headlineView = headline; bodyView = body; callToActionView = cta
    }
}

private data class AdColors(val text: Int, val sub: Int, val accent: Int, val onAccent: Int)

private fun bind(v: NativeAdView, ad: NativeAd, k: AdColors) {
    val d = v.resources.displayMetrics.density
    (v.headlineView as TextView).setTextColor(k.text)
    (v.bodyView as TextView).setTextColor(k.sub)
    (v.callToActionView as TextView).apply {
        setTextColor(k.onAccent)
        background = android.graphics.drawable.GradientDrawable().apply { setColor(k.accent); cornerRadius = 100 * d }
    }
    (v.headlineView as TextView).text = ad.headline
    (v.bodyView as TextView).apply { text = ad.body; visibility = if (ad.body == null) android.view.View.GONE else android.view.View.VISIBLE }
    (v.callToActionView as TextView).apply { text = ad.callToAction; visibility = if (ad.callToAction == null) android.view.View.GONE else android.view.View.VISIBLE }
    (v.iconView as ImageView).apply { setImageDrawable(ad.icon?.drawable); visibility = if (ad.icon == null) android.view.View.GONE else android.view.View.VISIBLE }
    v.setNativeAd(ad)
}
