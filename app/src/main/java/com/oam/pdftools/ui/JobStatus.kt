package com.oam.pdftools.ui

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.oam.pdftools.*
import com.oam.pdftools.work.PdfWorker

@Composable
fun JobStatus(info: WorkInfo?) {
    val ctx = LocalContext.current
    when (info?.state) {
        WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            info.progress.getString(PdfWorker.STEP)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        WorkInfo.State.SUCCEEDED -> {
            // TODO ad: capped interstitial here (skip first 2 tasks, ≥ 3–4 min apart)
            val d = info.outputData
            val outs = d.getStringArray(PdfWorker.OUTS).orEmpty().map(Uri::parse)
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (outs.size > 1) "Done ✓ ${outs.size} files saved" else "Done ✓", style = MaterialTheme.typography.titleMedium)
                    Text(sizeChange(d.getLong(PdfWorker.IN_SIZE, 0), d.getLong(PdfWorker.OUT_SIZE, 0)))
                    val tKb = d.getInt(PdfWorker.TARGET_KB, 0)
                    if (tKb > 0) {
                        val hit = d.getBoolean(PdfWorker.HIT, false)
                        Text(if (hit) "Under ${kbLabel(tKb)} ✓" else if (d.getBoolean(PdfWorker.ALLOW_RASTER, false)) "Couldn't reach ${kbLabel(tKb)}. This is the smallest readable size."
                             else "Couldn't reach ${kbLabel(tKb)} without losing quality. Tick \"Allow lower quality\" to go further.",
                            color = if (hit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                    if (d.getBoolean(PdfWorker.RASTER, false))
                        Text("Pages were converted to images to reach this size, so text can't be selected.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (outs.size == 1) OutlinedButton({ ctx.openPdf(outs[0]) }) { Text("Open") }
                        Button({ ctx.sharePdfs(outs, "com.whatsapp") }) { Text("WhatsApp") }
                        OutlinedButton({ ctx.sharePdfs(outs) }) { Text("Share") }
                    }
                }
            }
        }
        WorkInfo.State.FAILED -> Text("Failed: ${info.outputData.getString("err")}", color = MaterialTheme.colorScheme.error)
        else -> {}
    }
}
