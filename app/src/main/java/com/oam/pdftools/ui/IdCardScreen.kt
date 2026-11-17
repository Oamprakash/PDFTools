package com.oam.pdftools.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.oam.pdftools.thumb
import com.oam.pdftools.work.PdfWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.util.UUID

/** Front + back of an ID card on one A4 page at real size (85.6 × 54 mm), ready to print. */
@Composable
fun IdCardScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    var front by remember { mutableStateOf<Uri?>(null) }
    var rear by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var workId by remember { mutableStateOf<UUID?>(null) }

    val scanFront = rememberScanner(1, { error = it }) { front = it.firstOrNull() ?: front }
    val scanBack = rememberScanner(1, { error = it }) { rear = it.firstOrNull() ?: rear }
    val save = rememberLauncherForActivityResult(CreateDocument("application/pdf")) { u ->
        val f = front ?: return@rememberLauncherForActivityResult; u ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        workId = PdfWorker.enqueue(ctx, PdfWorker.ID_CARD, listOfNotNull(f, rear), u)
    }
    val info by remember(workId) {
        workId?.let { WorkManager.getInstance(ctx).getWorkInfoByIdFlow(it) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(null)

    ToolLayout(PdfWorker.ID_CARD, back, bottom = {
        Button({ save.launch("ID_card_${System.currentTimeMillis()}.pdf") }, Modifier.weight(1f),
            enabled = front != null && info?.state != WorkInfo.State.RUNNING) { Text("Create PDF") }
    }) {
        JobStatus(info)
        Hint("Scan each side. The card is auto-cropped, cleaned up and placed at real size (85.6 × 54 mm) on one A4 page, ready to print.")
        CardSlot("Front", front, scanFront) { front = null }
        CardSlot("Back (optional)", rear, scanBack) { rear = null }
        error?.let { Hint(it, error = true) }
    }
}

@Composable
private fun CardSlot(label: String, uri: Uri?, scan: () -> Unit, clear: () -> Unit) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, uri) {
        value = uri?.let { withContext(Dispatchers.IO) { runCatching { ctx.thumb(it) }.getOrNull() } }
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), border = cardBorder) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(96.dp, 61.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant), contentAlignment = Alignment.Center) {
                bmp?.let { Image(it.asImageBitmap(), label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    ?: Text(label.substringBefore(' '), style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Row {
                    TextButton(scan) { Text(if (uri == null) "Scan" else "Rescan") }
                    if (uri != null) TextButton(clear) { Text("Remove") }
                }
            }
        }
    }
}
