package com.oamsapps.pdfzy.ui

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.oamsapps.pdfzy.R
import androidx.compose.ui.res.stringResource
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
import com.oamsapps.pdfzy.outputName
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
    // Open the PDF off the main thread: a big file would otherwise freeze the screen
    val thumbsState = remember(src) { mutableStateOf<PdfThumbs?>(null) }
    var openFailed by remember(src) { mutableStateOf(false) }
    LaunchedEffect(src) {
        val u = src ?: return@LaunchedEffect
        val t = withContext(Dispatchers.IO) { runCatching { PdfThumbs(ctx, u) }.getOrNull() }
        if (t == null) openFailed = true else thumbsState.value = t
    }
    val thumbs = thumbsState.value
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
        actions = { if (src != null) TextButton({ pick.launch(arrayOf("application/pdf")) }) { Text(stringResource(R.string.change_file)) } },
        bottom = {
            val busy = info?.state == WorkInfo.State.RUNNING
            if (src == null) Button({ pick.launch(arrayOf("application/pdf")) }, Modifier.weight(1f)) { Text(stringResource(R.string.select_pdf)) }
            else if (isImage) Button({
                val n = if (sel.isEmpty()) thumbs?.count ?: 0 else sel.size
                gate(Ads.needsReward(op, n), Ads.gateText(ctx, op, n)) { folder.launch(null) }
            }, Modifier.weight(1f), enabled = thumbs != null && !busy) {
                Text(if (sel.isEmpty()) stringResource(R.string.export_all) else pluralRes(R.plurals.export_n, sel.size))
            } else {
                if (isRotate) {
                    FilledTonalIconButton({ turn(-90) }, enabled = thumbs != null) { Icon(Icons.Outlined.RotateLeft, stringResource(R.string.rotate_left)) }
                    FilledTonalIconButton({ turn(90) }, enabled = thumbs != null) { Icon(Icons.Outlined.RotateRight, stringResource(R.string.rotate_right)) }
                }
                val ready = if (isRotate) rot.isNotEmpty() else sel.isNotEmpty()
                Button({ save.launch(ctx.outputName(op, listOfNotNull(src))) }, Modifier.weight(1f),
                    enabled = ready && !busy) { Text(stringResource(R.string.save_pdf)) }
            }
        }) {
        JobStatus(info) { workId = it }
        when {
            src == null -> EmptyState(op, stringResource(R.string.empty_grid_t), stringResource(R.string.empty_grid_h))
            openFailed -> CantOpenPdf(src)
            thumbs == null -> SkeletonPageGrid(Modifier.weight(1f))
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isImage && sel.isEmpty()) pluralRes(R.plurals.all_n_pages, thumbs.count) else stringResource(R.string.n_of_m_selected, sel.size, thumbs.count),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton({ sel.clear(); sel += 0 until thumbs.count }) { Text(stringResource(R.string.all)) }
                    TextButton({ sel.clear() }) { Text(stringResource(R.string.none)) }
                }
                when (op) {
                    PdfWorker.EXTRACT -> Hint(stringResource(R.string.hint_extract))
                    PdfWorker.ROTATE -> Hint(stringResource(R.string.hint_rotate))
                    else -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppChip(!png, { png = false }, { Text("JPG") }); AppChip(png, { png = true }, { Text("PNG") })
                        AppChip(!hiRes, { hiRes = false }, { Text(stringResource(R.string.quality_standard)) }); AppChip(hiRes, { hiRes = true }, { Text(stringResource(R.string.quality_high)) })
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
            bmp?.let { Image(it.asImageBitmap(), stringResource(R.string.page_n, i + 1), Modifier.fillMaxSize().rotate(deg.toFloat())) }
                ?: CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Text(if (selected) "✓ ${i + 1}" else "${i + 1}", style = MaterialTheme.typography.labelMedium)
    }
}
