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
import com.oam.pdftools.work.PdfWorker
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/** One generic screen for every "pick files → run op → save" tool. */
@Composable
fun ToolScreen(op: String, back: () -> Unit) {
    val ctx = LocalContext.current
    val isImg = op == PdfWorker.IMG
    val multi = op != PdfWorker.COMPRESS
    val minItems = if (op == PdfWorker.MERGE) 2 else 1
    val items = remember { mutableStateListOf<Uri>() }
    var workId by remember { mutableStateOf<UUID?>(null) }
    var out by remember { mutableStateOf<Uri?>(null) }

    fun add(list: List<Uri>) { if (!multi) items.clear(); items += if (multi) list else list.take(1) }
    val pickImg = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { add(it) }
    val pickPdf = rememberLauncherForActivityResult(OpenMultipleDocuments()) { add(it) }
    val save = rememberLauncherForActivityResult(CreateDocument("application/pdf")) { u ->
        u ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        out = u; workId = PdfWorker.enqueue(ctx, op, items.toList(), u)
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
        JobStatus(info, out)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({
                if (isImg) pickImg.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                else pickPdf.launch(arrayOf("application/pdf"))
            }) { Text(if (items.isEmpty()) "Select" else "Add more") }
            // TODO ad: if items exceed free limit → rewarded ad gate
            Button({ save.launch("${op.replace(' ', '_')}_${System.currentTimeMillis()}.pdf") },
                enabled = items.size >= minItems && state != WorkInfo.State.RUNNING) { Text("Create PDF") }
        }
    }
}
