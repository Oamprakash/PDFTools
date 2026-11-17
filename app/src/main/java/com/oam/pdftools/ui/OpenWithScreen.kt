package com.oam.pdftools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
    ToolLayout("What do you want to do?", back) {
        meta?.let { FileRow(it.first, it.second.kb()) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(listOf(PdfWorker.COMPRESS, PdfWorker.PROTECT, PdfWorker.SPLIT, PdfWorker.EXTRACT, PdfWorker.ROTATE,
                PdfWorker.TO_IMAGE, PdfWorker.UNLOCK, PdfWorker.MERGE)) { op ->
                Card({ open(op) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), border = cardBorder) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ToolIcon(op, 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(label(op), style = MaterialTheme.typography.titleSmall)
                            Text(toolStyle(op).desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
