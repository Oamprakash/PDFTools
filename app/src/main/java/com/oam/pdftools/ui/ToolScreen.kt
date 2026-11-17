package com.oam.pdftools.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.oam.pdftools.*
import com.oam.pdftools.pdf.PdfOps
import com.oam.pdftools.work.PdfWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/** One generic screen for every "pick files → run op → save" tool. */
@Composable
fun ToolScreen(op: String, back: () -> Unit) {
    val ctx = LocalContext.current
    val isImg = op == PdfWorker.IMG
    val isScan = op == PdfWorker.SCAN
    val multi = op == PdfWorker.IMG || op == PdfWorker.MERGE || isScan
    val isPw = op == PdfWorker.PROTECT || op == PdfWorker.UNLOCK
    var password by remember { mutableStateOf<String?>(null) }
    val minItems = if (op == PdfWorker.MERGE) 2 else 1
    val items = remember { mutableStateListOf<Uri>().apply { addAll(if (multi) Incoming.uris else Incoming.uris.take(1)) } }
    var targetKb by remember { mutableStateOf<Int?>(null) }
    var allowRaster by remember { mutableStateOf(false) }
    var workId by remember { mutableStateOf<UUID?>(null) }

    fun add(list: List<Uri>) { if (!multi) items.clear(); items += if (multi) list else list.take(1) }
    val pickImg = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { add(it) }
    val pickPdf = rememberLauncherForActivityResult(OpenMultipleDocuments()) { add(it) }
    var scanError by remember { mutableStateOf<String?>(null) }
    val scan = rememberScanner(onError = { scanError = it }) { add(it) }
    LaunchedEffect(Unit) { if (isScan && items.isEmpty()) scan() }
    val save = rememberLauncherForActivityResult(CreateDocument("application/pdf")) { u ->
        u ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val extra = buildMap<String, Any> {
            targetKb?.let { put(PdfWorker.TARGET_KB, it); put(PdfWorker.ALLOW_RASTER, allowRaster) }
            password?.let { put(PdfWorker.PW_KEY, Secrets.put(it)) }
        }
        workId = PdfWorker.enqueue(ctx, op, items.toList(), u, extra)
    }
    val info by remember(workId) {
        workId?.let { WorkManager.getInstance(ctx).getWorkInfoByIdFlow(it) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(null)
    val state = info?.state

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row { TextButton(back) { Text("← Back") }; Text(op, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp)) }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(items) { i, u ->
                ListItem(headlineContent = { Text(ctx.fileMeta(u).first, maxLines = 1) }, trailingContent = {
                    Row {
                        if (multi) {
                            TextButton({ if (i > 0) items.add(i - 1, items.removeAt(i)) }) { Text("↑") }
                            TextButton({ if (i < items.lastIndex) items.add(i + 1, items.removeAt(i)) }) { Text("↓") }
                        }
                        TextButton({ items.removeAt(i) }) { Text("✕") }
                    }
                })
            }
        }
        if (op == PdfWorker.COMPRESS) {
            val srcSize = remember(items.firstOrNull()) { items.firstOrNull()?.let { ctx.fileMeta(it).second } }
            val analysis by produceState<PdfOps.Analysis?>(null, items.firstOrNull()) {
                value = null
                val u = items.firstOrNull() ?: return@produceState
                value = withContext(Dispatchers.IO) { runCatching { PdfOps(ctx.contentResolver).analyze(u, srcSize ?: 0) }.getOrNull() }
            }
            val a = analysis
            val floor = a?.takeIf { it.textOnly }?.minLossless // only text-only files have a hard floor
            when {
                items.isEmpty() -> {}
                a == null -> Text("Checking file…", style = MaterialTheme.typography.bodySmall)
                floor != null -> Text("Text-only PDF · ${a.pages} pages. Smallest size without losing quality: about ${floor.kb()}",
                    style = MaterialTheme.typography.bodySmall)
            }
            TargetPicker(targetKb, floor) { targetKb = it }
            targetKb?.let { t ->
                if (floor != null && t * 1000L < floor) Text("${kbLabel(t)} isn't realistic for this file. You'll get about ${floor.kb()}.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (targetKb != null) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(allowRaster, { allowRaster = it })
                Text("Allow lower quality (text may look blurry)", style = MaterialTheme.typography.bodySmall)
            }
            val t = targetKb
            if (srcSize != null && t != null && srcSize <= t * 1000L) Text("Already under ${kbLabel(t)}. It will be saved as is.")
        }
        if (isPw) PasswordInput(op == PdfWorker.PROTECT, items.firstOrNull()) { password = it }
        scanError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        JobStatus(info)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isScan) OutlinedButton({ scanError = null; scan() }) { Text(if (items.isEmpty()) "Scan" else "Scan more") }
            else OutlinedButton({
                if (isImg) pickImg.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                else pickPdf.launch(arrayOf("application/pdf"))
            }) { Text(if (items.isEmpty()) "Select" else "Add more") }
            // TODO ad: if items exceed free limit → rewarded ad gate
            Button({ save.launch("${op.replace(' ', '_')}_${System.currentTimeMillis()}.pdf") },
                enabled = items.size >= minItems && (!isPw || password != null) && state != WorkInfo.State.RUNNING) { Text("Create PDF") }
        }
    }
}
