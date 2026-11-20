package com.oamsapps.pdfzy.ui

import com.oamsapps.pdfzy.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.oamsapps.pdfzy.*
import com.oamsapps.pdfzy.ads.Ads
import com.oamsapps.pdfzy.work.PdfWorker

/** Progress / result / error card. The result suggests a next step to keep the user moving. */
@Composable
fun JobStatus(info: WorkInfo?) {
    val ctx = LocalContext.current
    val nav = LocalNavigate.current
    when (info?.state) {
        WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
            AskNotificationsOnce()
            Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), border = cardBorder
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.working), style = MaterialTheme.typography.titleSmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Hint(info.progress.getString(PdfWorker.STEP) ?: stringResource(R.string.working_hint))
            }
        }
        }
        WorkInfo.State.SUCCEEDED -> {
            val act = ctx.findActivity()
            LaunchedEffect(info.id) { Ads.onTaskCompleted(act, info.id) } // capped interstitial (see Ads)
            val d = info.outputData
            val outs = d.getStringArray(PdfWorker.OUTS).orEmpty().map(Uri::parse)
            val mime = d.getString(PdfWorker.MIME) ?: "application/pdf"
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(if (outs.size > 1) pluralRes(R.plurals.files_saved, outs.size) else stringResource(R.string.saved), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(ctx.sizeChange(d.getLong(PdfWorker.IN_SIZE, 0), d.getLong(PdfWorker.OUT_SIZE, 0)), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    val tKb = d.getInt(PdfWorker.TARGET_KB, 0)
                    if (tKb > 0) {
                        val hit = d.getBoolean(PdfWorker.HIT, false)
                        Hint(
                            if (hit) stringResource(R.string.under_target, kbLabel(tKb))
                            else if (d.getBoolean(PdfWorker.ALLOW_RASTER, false)) stringResource(R.string.cant_reach_readable, kbLabel(tKb))
                            else stringResource(R.string.cant_reach_quality, kbLabel(tKb)),
                            error = !hit
                        )
                    }
                    if (d.getBoolean(PdfWorker.RASTER, false)) Hint(stringResource(R.string.raster_note))
                    // Equal widths, tighter padding and single-line labels so "WhatsApp" never wraps on narrow phones
                    val pad = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    @Composable fun Label(t: String) = Text(t, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ ctx.sharePdfs(outs, mime = mime) }, Modifier.weight(1f), contentPadding = pad) { Label(stringResource(R.string.share)) }
                        FilledTonalButton({ ctx.sharePdfs(outs, "com.whatsapp", mime) }, Modifier.weight(1f), contentPadding = pad) { Label("WhatsApp") }
                        if (outs.size == 1) OutlinedButton({ ctx.openPdf(outs[0], mime) }, Modifier.weight(1f), contentPadding = pad) { Label(stringResource(R.string.open)) }
                    }
                    if (mime == "application/pdf" && outs.size == 1) {
                        Text(stringResource(R.string.do_more), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(PdfWorker.COMPRESS, PdfWorker.PROTECT, PdfWorker.MERGE, PdfWorker.SPLIT, PdfWorker.TO_IMAGE).forEach { op ->
                                val s = toolStyle(op)
                                AssistChip({ Incoming.uris = outs; nav(op) }, { Text(label(op)) },
                                    leadingIcon = { Icon(s.icon, null, Modifier.size(18.dp), tint = s.tint) })
                            }
                        }
                    }
                }
            }
        }
        WorkInfo.State.FAILED -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(stringResource(R.string.couldnt_finish, info.outputData.getString("err") ?: stringResource(R.string.unknown_error)), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        else -> {}
    }
}

/** Android 13+: ask once, while a job runs, so we can say "Your PDF is ready" if the user switches apps. */
@Composable
private fun AskNotificationsOnce() {
    if (android.os.Build.VERSION.SDK_INT < 33) return
    val ctx = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences("perm", android.content.Context.MODE_PRIVATE)
        val granted = ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted && !prefs.getBoolean("askedNotif", false)) {
            prefs.edit().putBoolean("askedNotif", true).apply()
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
