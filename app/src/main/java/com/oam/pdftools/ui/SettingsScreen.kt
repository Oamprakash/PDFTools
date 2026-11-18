package com.oam.pdftools.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.oam.pdftools.*
import com.oam.pdftools.ads.Consent
import com.oam.pdftools.billing.Billing
import com.oam.pdftools.billing.RemoveAdsButton

@Composable
fun SettingsScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
    fun web(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    ToolLayout("Settings", back) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!Billing.adFree.value && Billing.price.value != null) Row(
                Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
            ) { Text("Enjoying the app?", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); RemoveAdsButton() }
            SectionTitle("Support")
            Item(Icons.Outlined.StarOutline, "Rate Pdfzy", "Takes 10 seconds and helps a lot") { Rating.openStore(ctx) }
            Item(Icons.Outlined.MailOutline, "Send feedback", "Found a problem or want a feature? Tell us") {
                val body = "\n\n---\nApp $version · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"
                runCatching {
                    ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, arrayOf(Config.SUPPORT_EMAIL))
                        .putExtra(Intent.EXTRA_SUBJECT, "Pdfzy feedback").putExtra(Intent.EXTRA_TEXT, body))
                }
            }
            Item(Icons.Outlined.Share, "Share the app", "Send it to friends who handle PDFs") {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
                    "I use Pdfzy to scan, compress and merge PDFs offline: https://play.google.com/store/apps/details?id=${ctx.packageName}"), "Share"))
            }
            SectionTitle("Privacy")
            Item(Icons.Outlined.Shield, "Privacy policy", "Your files are processed on this phone and never uploaded") { web(Config.PRIVACY_URL) }
            if (Consent.privacyOptionsRequired.value)
                Item(Icons.Outlined.PrivacyTip, "Ad privacy choices", "Change your consent for ads") { Consent.showPrivacyOptions(ctx.findActivity()) }
            Hint("Version $version", )
        }
    }
}

@Composable
private fun Item(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) = Surface(
    shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, border = cardBorder,
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
