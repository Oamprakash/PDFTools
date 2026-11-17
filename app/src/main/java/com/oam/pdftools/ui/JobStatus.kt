package com.oam.pdftools.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.work.WorkInfo
import com.oam.pdftools.openPdf
import com.oam.pdftools.work.PdfWorker

@Composable
fun JobStatus(info: WorkInfo?, out: Uri?, canOpen: Boolean = true) {
    val ctx = LocalContext.current
    when (info?.state) {
        WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> LinearProgressIndicator(Modifier.fillMaxWidth())
        WorkInfo.State.SUCCEEDED -> {
            // TODO ad: capped interstitial here (≥ 2–3 min apart), then show result
            val n = info.outputData.getInt(PdfWorker.COUNT, 1)
            Text(if (n > 1) "Done ✓ $n files saved (see Recent)" else "Done ✓")
            if (canOpen) Button({ out?.let(ctx::openPdf) }) { Text("Open PDF") }
        }
        WorkInfo.State.FAILED -> Text("Failed: ${info.outputData.getString("err")}", color = MaterialTheme.colorScheme.error)
        else -> {}
    }
}
