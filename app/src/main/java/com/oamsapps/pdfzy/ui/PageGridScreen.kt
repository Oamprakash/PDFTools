package com.oamsapps.pdfzy.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.oamsapps.pdfzy.Incoming
import com.oamsapps.pdfzy.ads.Ads
import com.oamsapps.pdfzy.ads.rememberRewardGate
import com.oamsapps.pdfzy.pdf.PdfThumbs
import com.oamsapps.pdfzy.work.PdfWorker
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/** Extract (select pages) or Rotate (per-page rotation) on a thumbnail grid. */
@Composable
fun PageGridScreen(op: String, back: () -> Unit) {
    val ctx = LocalContext.current
    val isRotate = op == PdfWorker.ROTATE
    val isImage = op == PdfWorker.TO_IMAGE
    var png by remember { mutableStateOf(false) }
    var hiRes by remember { mutableStateOf(false) }
    var src by remember { mutableStateOf(Incoming.uris.firstOrNull()) }
    val thumbs = remember(src) { src?.let { runCatching { PdfThumbs(ctx, it) }.getOrNull() } }
    DisposableEffect(thumbs) { onDispose { thumbs?.close() } }
    val sel = remember(src) { mutableStateListOf<Int>() }
    val rot = remember(src) { mutableStateMapOf<Int, Int>() }
    val gate = rememberRewardGate()
    var workId by remember { mutableStateOf<UUID?>(null) }

    val pick = rememberLauncherForActivityResult(OpenDocument()) { if (it != null) src = it }
    LaunchedEffect(Unit) { if (src == null) pick.launch(arrayOf("application/pdf")) }
    val save = rememberLauncherForActivityResult(CreateDocument("application/pdf")) { u ->
        val s = src ?: return@rememberLauncherForActivityResult; u ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val pages = if (isRotate) rot.keys.toIntArray() else sel.sorted().toIntArray()
        val extra = buildMap<String, Any> {
            put(PdfWorker.PAGES, pages)
            if (isRotate) put(PdfWorker.DEGS, pages.map { rot[it]!! }.toIntArray())
        }
        workId = PdfWorker.enqueue(ctx, op, listOf(s), u, extra)
    }
    val folder = rememberLauncherForActivityResult(OpenDocumentTree()) { tree ->
        val s = src ?: return@rememberLauncherForActivityResult; tree ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        workId = PdfWorker.enqueue(ctx, op, listOf(s), tree, mapOf(
            PdfWorker.PAGES to sel.sorted().toIntArray(), PdfWorker.PNG to png, PdfWorker.DPI to if (hiRes) 300 else 150))
    }
    val info by remember(workId) {
        workId?.let { WorkManager.getInstance(ctx).getWorkInfoByIdFlow(it) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(null)

    fun turn(d: Int) = (sel.ifEmpty { (0 until (thumbs?.count ?: 0)).toList() }).forEach {
        val v = ((rot[it] ?: 0) + d + 360) % 360
        if (v == 0) rot.remove(it) else rot[it] = v
    }

    ToolLayout(op, back,
        actions = { if (src != null) TextButton({ pick.launch(arrayOf("application/pdf")) }) { Text("Change file") } },
        bottom = {
            val busy = info?.state == WorkInfo.State.RUNNING
            if (src == null) Button({ pick.launch(arrayOf("application/pdf")) }, Modifier.weight(1f)) { Text("Select PDF") }
            else if (isImage) Button({
                val n = if (sel.isEmpty()) thumbs?.count ?: 0 else sel.size
                gate(Ads.needsReward(op, n), Ads.gateText(op, n)) { folder.launch(null) }
            }, Modifier.weight(1f), enabled = thumbs != null && !busy) {
                Text(if (sel.isEmpty()) "Export all pages" else "Export ${sel.size} page(s)")
            } else {
                if (isRotate) {
                    FilledTonalIconButton({ turn(-90) }, enabled = thumbs != null) { Icon(Icons.Outlined.RotateLeft, "Rotate left") }
                    FilledTonalIconButton({ turn(90) }, enabled = thumbs != null) { Icon(Icons.Outlined.RotateRight, "Rotate right") }
                }
                val ready = if (isRotate) rot.isNotEmpty() else sel.isNotEmpty()
                Button({ save.launch("${op.replace(' ', '_')}_${System.currentTimeMillis()}.pdf") }, Modifier.weight(1f),
                    enabled = ready && !busy) { Text("Save PDF") }
            }
        }) {
        JobStatus(info)
        when {
            src == null -> EmptyState(op, "Pick a PDF", "You'll see all its pages here.")
            thumbs == null -> Hint("Can't open this PDF. If it's password-protected, use Unlock first.", error = true)
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isImage && sel.isEmpty()) "All ${thumbs.count} pages" else "${sel.size} of ${thumbs.count} selected",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton({ sel.clear(); sel += 0 until thumbs.count }) { Text("All") }
                    TextButton({ sel.clear() }) { Text("None") }
                }
                when (op) {
                    PdfWorker.EXTRACT -> Hint("Tap the pages to keep. They'll be saved as a new PDF.")
                    PdfWorker.ROTATE -> Hint("Tap pages, then rotate. With nothing selected, all pages rotate.")
                    else -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!png, { png = false }, { Text("JPG") }); FilterChip(png, { png = true }, { Text("PNG") })
                        FilterChip(!hiRes, { hiRes = false }, { Text("Standard") }); FilterChip(hiRes, { hiRes = true }, { Text("High quality") })
                    }
                }
                LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(thumbs.count, key = { it }) { i ->
                        PageThumb(thumbs, i, i in sel, rot[i] ?: 0) { if (i in sel) sel.remove(i) else sel.add(i) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageThumb(thumbs: PdfThumbs, i: Int, selected: Boolean, deg: Int, onClick: () -> Unit) {
    val bmp by produceState<android.graphics.Bitmap?>(null, i) { value = runCatching { thumbs.page(i) }.getOrNull() }
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(MaterialTheme.shapes.small).border(if (selected) 2.dp else 1.dp, border, MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onClick).padding(6.dp)) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            bmp?.let { Image(it.asImageBitmap(), "Page ${i + 1}", Modifier.fillMaxSize().rotate(deg.toFloat())) }
                ?: CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Text(if (selected) "✓ ${i + 1}" else "${i + 1}", style = MaterialTheme.typography.labelMedium)
    }
}
