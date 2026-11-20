package com.oamsapps.pdfzy.ui

import com.oamsapps.pdfzy.R
import androidx.compose.ui.res.stringResource
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
import com.oamsapps.pdfzy.*
import com.oamsapps.pdfzy.ads.Consent
import com.oamsapps.pdfzy.billing.Billing
import com.oamsapps.pdfzy.billing.RemoveAdsButton

@Composable
fun SettingsScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
    fun web(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    ToolLayout(stringResource(R.string.settings), back, showAd = false) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!Billing.adFree.value && Billing.price.value != null) Row(
                Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
            ) { Text(stringResource(R.string.enjoying), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); RemoveAdsButton() }
            SectionTitle(stringResource(R.string.support))
            Item(Icons.Outlined.StarOutline, stringResource(R.string.rate_app), stringResource(R.string.rate_sub)) { Rating.openStore(ctx) }
            Item(Icons.Outlined.MailOutline, stringResource(R.string.send_feedback), stringResource(R.string.feedback_sub)) {
                val body = "\n\n---\nApp $version · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"
                runCatching {
                    ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, arrayOf(Config.SUPPORT_EMAIL))
                        .putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.feedback_subject)).putExtra(Intent.EXTRA_TEXT, body))
                }
            }
            Item(Icons.Outlined.Share, stringResource(R.string.share_app), stringResource(R.string.share_app_sub)) {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
                    ctx.getString(R.string.share_text, "https://play.google.com/store/apps/details?id=${ctx.packageName}")), ctx.getString(R.string.share)))
            }
            SectionTitle(stringResource(R.string.privacy))
            Item(Icons.Outlined.Shield, stringResource(R.string.privacy_policy), stringResource(R.string.privacy_policy_sub)) { web(Config.PRIVACY_URL) }
            if (Consent.privacyOptionsRequired.value)
                Item(Icons.Outlined.PrivacyTip, stringResource(R.string.ad_privacy), stringResource(R.string.ad_privacy_sub)) { Consent.showPrivacyOptions(ctx.findActivity()) }
            Hint(stringResource(R.string.version, version))
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
