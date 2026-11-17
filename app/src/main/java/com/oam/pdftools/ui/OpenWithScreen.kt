package com.oam.pdftools.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.oam.pdftools.*
import com.oam.pdftools.work.PdfWorker

/** Shown when a single PDF is shared / opened into the app. */
@Composable
fun OpenWithScreen(open: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val meta = remember { Incoming.uris.firstOrNull()?.let(ctx::fileMeta) }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(back) { Text("← Back") }
        Text(meta?.let { "${it.first} · ${it.second.kb()}" } ?: "No file", style = MaterialTheme.typography.titleMedium)
        Text("What do you want to do?")
        listOf(PdfWorker.COMPRESS, PdfWorker.SPLIT, PdfWorker.EXTRACT, PdfWorker.ROTATE, PdfWorker.MERGE).forEach { op ->
            ElevatedCard(Modifier.fillMaxWidth().clickable { open(op) }) { Text(op, Modifier.padding(20.dp)) }
        }
    }
}
