package com.oam.pdftools.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oam.pdftools.*
import com.oam.pdftools.ads.NativeAdCard
import com.oam.pdftools.billing.RemoveAdsButton
import com.oam.pdftools.work.PdfWorker

private val tools = listOf(PdfWorker.SCAN, PdfWorker.IMG, PdfWorker.ID_CARD, PdfWorker.MERGE, PdfWorker.SPLIT, PdfWorker.COMPRESS, PdfWorker.EXTRACT, PdfWorker.ROTATE, PdfWorker.TO_IMAGE, PdfWorker.PROTECT, PdfWorker.UNLOCK)

@Composable
fun HomeScreen(open: (String) -> Unit) {
    val ctx = LocalContext.current
    val recents by remember { (ctx.applicationContext as App).db.recents().all() }.collectAsStateWithLifecycle(emptyList())
    LazyColumn(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("PDF Tools", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                RemoveAdsButton()
            }
        }
        items(tools) { t -> ElevatedCard(Modifier.fillMaxWidth().clickable { open(t) }) { Text(t, Modifier.padding(20.dp)) } }
        // One fixed slot between tools and Recent: always visible, never between files (no misclicks)
        item(key = "ad") { NativeAdCard(Modifier.padding(top = 8.dp)) }
        item { Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp)) }
        if (recents.isEmpty()) item { Text("No files yet. Everything is processed on your phone.") }
        items(recents, key = { it.uri }) { r ->
            ListItem(
                headlineContent = { Text(r.name) },
                supportingContent = { Text("${r.op} · ${r.size.kb()}") },
                modifier = Modifier.clickable { ctx.openPdf(Uri.parse(r.uri)) }
            )
        }
    }
}
