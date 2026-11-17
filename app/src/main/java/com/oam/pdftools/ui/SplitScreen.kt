package com.oam.pdftools.ui

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
import com.oam.pdftools.fileMeta
import com.oam.pdftools.pdf.PdfThumbs
import com.oam.pdftools.work.PdfWorker
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
    var src by remember { mutableStateOf<Uri?>(null) }
    val name = remember(src) { src?.let { ctx.fileMeta(it).first } }
    val count = remember(src) { src?.let { u -> runCatching { PdfThumbs(ctx, u).use { it.count } }.getOrNull() } }
    var every by remember { mutableStateOf(true) }
    var n by remember { mutableStateOf("1") }
    var custom by remember { mutableStateOf("") }
    val ranges = count?.let { c ->
        if (every) n.toIntOrNull()?.takeIf { it > 0 }?.let { k -> (0 until c step k).map { it..minOf(it + k - 1, c - 1) } }
        else parseRanges(custom, c)
    }
    var workId by remember { mutableStateOf<UUID?>(null) }
    var out by remember { mutableStateOf<Uri?>(null) }

    val pick = rememberLauncherForActivityResult(OpenDocument()) { if (it != null) src = it }
    LaunchedEffect(Unit) { pick.launch(arrayOf("application/pdf")) }
    val folder = rememberLauncherForActivityResult(OpenDocumentTree()) { tree ->
        val s = src; val r = ranges
        if (tree == null || s == null || r == null) return@rememberLauncherForActivityResult
        ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        // TODO ad: rewarded gate if r.size > free limit
        out = tree
        workId = PdfWorker.enqueue(ctx, PdfWorker.SPLIT, listOf(s), tree,
            mapOf(PdfWorker.RANGES to r.flatMap { listOf(it.first, it.last) }.toIntArray()))
    }
    val info by remember(workId) {
        workId?.let { WorkManager.getInstance(ctx).getWorkInfoByIdFlow(it) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(null)

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(back) { Text("← Back") }
            Text(PdfWorker.SPLIT, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton({ pick.launch(arrayOf("application/pdf")) }) { Text("Change file") }
        }
        when {
            src == null -> Text("Select a PDF to start.")
            count == null -> Text("Can't open this PDF (it may be password-protected).", color = MaterialTheme.colorScheme.error)
            else -> {
                Text("$name · $count pages")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(every, { every = true }); Text("Every ")
                    OutlinedTextField(n, { n = it.filter(Char::isDigit).take(4) }, Modifier.width(72.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Text(" pages")
                }
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(!every, { every = false }); Text("Custom ranges") }
                if (!every) OutlinedTextField(custom, { custom = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("e.g. 1-3, 4, 5-10") })
                if (ranges != null) Text("Will create ${ranges.size} file(s)")
                else Text("Enter valid pages (1–$count)", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.weight(1f))
        JobStatus(info, out, canOpen = false)
        Button({ folder.launch(null) }, Modifier.fillMaxWidth(),
            enabled = ranges != null && info?.state != WorkInfo.State.RUNNING) { Text("Choose folder & split") }
    }
}
