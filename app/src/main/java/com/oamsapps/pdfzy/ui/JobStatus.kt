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
import com.oamsapps.pdfzy.pdf.PdfError
import com.oamsapps.pdfzy.work.PdfWorker

/** Progress / result / error card. The result suggests a next step to keep the user moving. */
@Composable
fun JobStatus(info: WorkInfo?, onRetry: (java.util.UUID) -> Unit = {}) {
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
                TextButton({ PdfWorker.cancel(ctx, info.id) }, Modifier.align(Alignment.End)) { Text(stringResource(R.string.cancel)) }
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
        WorkInfo.State.FAILED -> {
            val d = info.outputData
            val code = d.getString(PdfWorker.ERR_CODE) ?: PdfError.UNKNOWN
            val ins = d.getStringArray(PdfWorker.IN).orEmpty().map(Uri::parse)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(stringResource(when (code) {
                            PdfError.WRONG_PASSWORD -> R.string.err_wrong_password; PdfError.ENCRYPTED -> R.string.err_encrypted
                            PdfError.DAMAGED -> R.string.err_damaged; PdfError.NO_SPACE -> R.string.err_no_space
                            PdfError.TOO_LARGE -> R.string.err_too_large; PdfError.NOT_FOUND -> R.string.err_not_found
                            PdfError.SESSION -> R.string.err_session; else -> R.string.err_unknown
                        }), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (code == PdfError.ENCRYPTED && ins.isNotEmpty())
                            OutlinedButton({ Incoming.uris = ins.take(1); nav(PdfWorker.UNLOCK) }) { Text(stringResource(R.string.unlock_it)) }
                        // Retry makes no sense when the input itself is the problem or a password must be re-entered
                        if (code !in setOf(PdfError.WRONG_PASSWORD, PdfError.ENCRYPTED, PdfError.DAMAGED, PdfError.SESSION, PdfError.NOT_FOUND)
                            && PdfWorker.canRetry(info.id))
                            Button({ PdfWorker.retry(ctx, info.id)?.let(onRetry) }) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
        }
        WorkInfo.State.CANCELLED -> Hint(stringResource(R.string.cancelled))
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
