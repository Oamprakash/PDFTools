package com.oam.pdftools.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
    // Gallery only as a fallback when the scanner can't run; the scanner has its own gallery import (which still crops/filters)
    val pickFront = rememberLauncherForActivityResult(PickVisualMedia()) { if (it != null) front = it }
    val pickBack = rememberLauncherForActivityResult(PickVisualMedia()) { if (it != null) rear = it }
    val fallback = error != null
    val save = rememberLauncherForActivityResult(CreateDocument("application/pdf")) { u ->
        val f = front ?: return@rememberLauncherForActivityResult; u ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        workId = PdfWorker.enqueue(ctx, PdfWorker.ID_CARD, listOfNotNull(f, rear), u)
    }
    val info by remember(workId) {
        workId?.let { WorkManager.getInstance(ctx).getWorkInfoByIdFlow(it) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(null)
    val img = PickVisualMediaRequest(PickVisualMedia.ImageOnly)

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(back) { Text("← Back") }
            Text(PdfWorker.ID_CARD, style = MaterialTheme.typography.titleLarge)
        }
        Text("Both sides go on one A4 page at real card size (85.6 × 54 mm), ready to print.", style = MaterialTheme.typography.bodySmall)
        Text("Tap Scan. To use a photo you already have, tap the gallery icon inside the scanner; it will still be cropped and cleaned up.",
            style = MaterialTheme.typography.bodySmall)
        CardSlot("Front", front, scanFront, if (fallback) ({ pickFront.launch(img) }) else null) { front = null }
        CardSlot("Back (optional)", rear, scanBack, if (fallback) ({ pickBack.launch(img) }) else null) { rear = null }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.weight(1f))
        JobStatus(info)
        Button({ save.launch("ID_card_${System.currentTimeMillis()}.pdf") }, Modifier.fillMaxWidth(),
            enabled = front != null && info?.state != WorkInfo.State.RUNNING) { Text("Create PDF") }
    }
}

@Composable
private fun CardSlot(label: String, uri: Uri?, scan: () -> Unit, pick: (() -> Unit)?, clear: () -> Unit) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, uri) {
        value = uri?.let { withContext(Dispatchers.IO) { runCatching { ctx.thumb(it) }.getOrNull() } }
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(96.dp, 61.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant), contentAlignment = Alignment.Center) {
                bmp?.let { Image(it.asImageBitmap(), label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    ?: Text(label.substringBefore(' '), style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Row {
                    TextButton(scan) { Text(if (uri == null) "Scan" else "Rescan") }
                    if (pick != null) TextButton(pick) { Text("Gallery") }
                    if (uri != null) TextButton(clear) { Text("Remove") }
                }
            }
        }
    }
}
