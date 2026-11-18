package com.oamsapps.pdfzy.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.oamsapps.pdfzy.ads.Ads
import com.oamsapps.pdfzy.ads.rememberRewardGate
import com.oamsapps.pdfzy.fileMeta
import com.oamsapps.pdfzy.Incoming
import com.oamsapps.pdfzy.pdf.PdfThumbs
import com.oamsapps.pdfzy.work.PdfWorker
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/** "1-3, 5, 7-9" -> 0-based ranges; null if invalid. */
fun parseRanges(s: String, count: Int): List<IntRange>? = s.split(',').map(String::trim).filter(String::isNotEmpty).map { p ->
    val n = p.split('-').map { it.trim().toIntOrNull() ?: return null }
    val (a, b) = when (n.size) { 1 -> n[0] to n[0]; 2 -> n[0] to n[1]; else -> return null }
    if (a < 1 || b > count || a > b) return null
    (a - 1)..(b - 1)
}.ifEmpty { null }

@Composable
fun SplitScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    var src by remember { mutableStateOf(Incoming.uris.firstOrNull()) }
    val name = remember(src) { src?.let { ctx.fileMeta(it).first } }
    val count = remember(src) { src?.let { u -> runCatching { PdfThumbs(ctx, u).use { it.count } }.getOrNull() } }
    var every by remember { mutableStateOf(true) }
    var n by remember { mutableStateOf("1") }
    var custom by remember { mutableStateOf("") }
    val ranges = count?.let { c ->
        if (every) n.toIntOrNull()?.takeIf { it > 0 }?.let { k -> (0 until c step k).map { it..minOf(it + k - 1, c - 1) } }
        else parseRanges(custom, c)
    }
    val gate = rememberRewardGate()
    var workId by remember { mutableStateOf<UUID?>(null) }

    val pick = rememberLauncherForActivityResult(OpenDocument()) { if (it != null) src = it }
    LaunchedEffect(Unit) { if (src == null) pick.launch(arrayOf("application/pdf")) }
    val folder = rememberLauncherForActivityResult(OpenDocumentTree()) { tree ->
        val s = src; val r = ranges
        if (tree == null || s == null || r == null) return@rememberLauncherForActivityResult
        ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        workId = PdfWorker.enqueue(ctx, PdfWorker.SPLIT, listOf(s), tree,
            mapOf(PdfWorker.RANGES to r.flatMap { listOf(it.first, it.last) }.toIntArray()))
    }
    val info by remember(workId) {
        workId?.let { WorkManager.getInstance(ctx).getWorkInfoByIdFlow(it) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(null)

    ToolLayout(PdfWorker.SPLIT, back,
        actions = { if (src != null) TextButton({ pick.launch(arrayOf("application/pdf")) }) { Text("Change file") } },
        bottom = {
            if (src == null) Button({ pick.launch(arrayOf("application/pdf")) }, Modifier.weight(1f)) { Text("Select PDF") }
            else Button({ val n = ranges?.size ?: 0; gate(Ads.needsReward(PdfWorker.SPLIT, n), Ads.gateText(PdfWorker.SPLIT, n)) { folder.launch(null) } },
                Modifier.weight(1f), enabled = ranges != null && info?.state != WorkInfo.State.RUNNING) { Text("Choose folder & split") }
        }) {
        JobStatus(info)
        when {
            src == null -> EmptyState(PdfWorker.SPLIT, "Pick a PDF to split", "Split every few pages, or by your own page ranges.")
            count == null -> Hint("Can't open this PDF. If it's password-protected, use Unlock first.", error = true)
            else -> {
                FileRow(name.orEmpty(), "$count pages")
                SectionTitle("Split by")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(every, { every = true }); Text("Every")
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(n, { n = it.filter(Char::isDigit).take(4); every = true }, Modifier.width(80.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Spacer(Modifier.width(8.dp)); Text("pages")
                }
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(!every, { every = false }); Text("Custom ranges") }
                if (!every) OutlinedTextField(custom, { custom = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("e.g. 1-3, 4, 5-10") })
                if (ranges != null) Hint("Will create ${ranges.size} file(s)") else Hint("Enter valid pages (1–$count)", error = true)
            }
        }
    }
}
