package com.oamsapps.pdfzy.ui

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
import com.oamsapps.pdfzy.*
import com.oamsapps.pdfzy.ads.Ads
import com.oamsapps.pdfzy.ads.rememberRewardGate
import com.oamsapps.pdfzy.pdf.PdfOps
import com.oamsapps.pdfzy.work.PdfWorker
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
    val gate = rememberRewardGate()
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

    val pickMore: () -> Unit = {
        if (isScan) { scanError = null; scan() }
        else if (isImg) pickImg.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        else pickPdf.launch(arrayOf("application/pdf"))
    }
    val (emptyTitle, emptyHint) = when (op) {
        PdfWorker.SCAN -> "Scan your document" to "Pages are auto-cropped and cleaned up. Scan as many as you need."
        PdfWorker.IMG -> "Add photos" to "Pick images from your gallery. You can reorder them before creating the PDF."
        PdfWorker.MERGE -> "Add PDFs to merge" to "Tip: long-press a file in the picker to select several at once."
        PdfWorker.COMPRESS -> "Pick a PDF to compress" to "Choose an exact size like 200 KB for job, exam and form uploads."
        PdfWorker.PROTECT -> "Pick a PDF to protect" to "Add a password before sharing bank statements, ID copies and more."
        PdfWorker.UNLOCK -> "Pick a locked PDF" to "Remove the password from a PDF you're allowed to open."
        else -> "Pick a file" to ""
    }
    ToolLayout(op, back, bottom = {
        OutlinedButton(pickMore, Modifier.weight(1f)) {
            Text(when { items.isEmpty() -> if (isScan) "Scan" else "Select"; !multi -> "Change"; isScan -> "Scan more"; else -> "Add more" })
        }
        Button({ gate(Ads.needsReward(op, items.size), Ads.gateText(op, items.size)) { save.launch("${op.replace(' ', '_')}_${System.currentTimeMillis()}.pdf") } },
            Modifier.weight(1f), enabled = items.size >= minItems && (!isPw || password != null) && state != WorkInfo.State.RUNNING) { Text(actionLabel(op)) }
    }) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            item { JobStatus(info) } // result first, so it's visible without scrolling
            if (items.isEmpty()) item { EmptyState(op, emptyTitle, emptyHint) }
            itemsIndexed(items) { i, u ->
                val meta = remember(u) { ctx.fileMeta(u) }
                FileRow(meta.first, meta.second.takeIf { it > 0 }?.kb(), image = isImg || isScan,
                    onUp = if (multi && i > 0) ({ items.add(i - 1, items.removeAt(i)) }) else null,
                    onDown = if (multi && i < items.lastIndex) ({ items.add(i + 1, items.removeAt(i)) }) else null,
                    onRemove = { items.removeAt(i) })
            }
            if (op == PdfWorker.MERGE && items.isNotEmpty()) item {
                Hint(if (items.size == 1) "Add at least one more PDF (long-press in the picker to pick several)." else "Use the arrows to set the order.")
            }
            if (op == PdfWorker.COMPRESS && items.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val srcSize = remember(items.firstOrNull()) { items.firstOrNull()?.let { ctx.fileMeta(it).second } }
                    val analysis by produceState<PdfOps.Analysis?>(null, items.firstOrNull()) {
                        value = null
                        val u = items.firstOrNull() ?: return@produceState
                        value = withContext(Dispatchers.IO) { runCatching { PdfOps(ctx.contentResolver).analyze(u, srcSize ?: 0) }.getOrNull() }
                    }
                    val a = analysis
                    val floor = a?.takeIf { it.textOnly }?.minLossless // only text-only files have a hard floor
                    when {
                        a == null -> Hint("Checking file…")
                        floor != null -> Hint("Text-only PDF · ${a.pages} pages. Smallest size without losing quality: about ${floor.kb()}")
                    }
                    TargetPicker(targetKb, floor) { targetKb = it }
                    targetKb?.let { t ->
                        if (floor != null && t * 1000L < floor) Hint("${kbLabel(t)} isn't realistic for this file. You'll get about ${floor.kb()}.", error = true)
                        else if (srcSize != null && srcSize <= t * 1000L) Hint("Already under ${kbLabel(t)}. It will be saved as is.")
                    }
                    if (targetKb != null) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(allowRaster, { allowRaster = it })
                        Text("Allow lower quality (text may look blurry)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (isPw && items.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { PasswordInput(op == PdfWorker.PROTECT, items.firstOrNull()) { password = it } }
            }
            scanError?.let { e -> item { Hint(e, error = true) } }
        }
    }
}
