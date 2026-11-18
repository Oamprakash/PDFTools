package com.oamsapps.pdfzy.ui

import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oamsapps.pdfzy.*
import com.oamsapps.pdfzy.ads.Consent
import com.oamsapps.pdfzy.ads.NativeAdCard
import com.oamsapps.pdfzy.billing.RemoveAdsButton
import com.oamsapps.pdfzy.data.Recent
import com.oamsapps.pdfzy.work.PdfWorker
import kotlinx.coroutines.launch

/** Most-used tools get big cards; the rest sit in a compact grid. */
private val HERO = listOf(PdfWorker.SCAN, PdfWorker.IMG, PdfWorker.COMPRESS, PdfWorker.ID_CARD)
private val MORE = listOf(PdfWorker.MERGE, PdfWorker.SPLIT, PdfWorker.EXTRACT, PdfWorker.ROTATE,
    PdfWorker.TO_IMAGE, PdfWorker.PROTECT, PdfWorker.UNLOCK)

private fun LazyGridScope.full(key: Any? = null, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(open: (String) -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { (ctx.applicationContext as App).db.recents() }
    val recents by remember { dao.all() }.collectAsStateWithLifecycle(null)
    val nav = LocalNavigate.current
    Scaffold(topBar = {
        TopAppBar(title = { Text("PDFzy", fontWeight = FontWeight.SemiBold) }, actions = {
            // Required by Google when the user is in a consent region: lets them change their ad choices
            if (Consent.privacyOptionsRequired.value)
                IconButton({ Consent.showPrivacyOptions(ctx.findActivity()) }) { Icon(Icons.Outlined.PrivacyTip, "Privacy settings") }
            RemoveAdsButton()
            IconButton({ nav("settings") }) { Icon(Icons.Outlined.Settings, "Settings") }
        })
    }) { pad ->
        // 6-column grid: hero cards span 3 (two per row), small tiles span 2 (three per row)
        LazyVerticalGrid(GridCells.Fixed(6), Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            full { PrivacyNote() }
            items(HERO, span = { GridItemSpan(3) }) { HeroCard(it) { open(it) } }
            full("ad") { NativeAdCard() } // one native ad between the hero cards and More tools; never inside the file list
            full { SectionTitle("More tools") }
            items(MORE, span = { GridItemSpan(2) }) { ToolTile(it) { open(it) } }
            full { SectionTitle("Recent files") }
            val list = recents
            when {
                list == null -> {}
                list.isEmpty() -> full { RecentEmpty() }
                else -> items(list, key = { it.uri }, span = { GridItemSpan(maxLineSpan) }) { RecentRow(it) }
            }
        }
    }
}

@Composable
private fun PrivacyNote() = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
        .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
) {
    Icon(Icons.Outlined.Shield, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
    Text("Your files are processed on this phone and never uploaded.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun HeroCard(op: String, onClick: () -> Unit) = Card(
    onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), border = cardBorder
) {
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToolIcon(op)
        Column {
            Text(label(op), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(toolStyle(op).desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ToolTile(op: String, onClick: () -> Unit) = Card(
    onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), border = cardBorder
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolIcon(op, 40.dp)
        Text(label(op), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, minLines = 2)
    }
}

@Composable
private fun RecentEmpty() = Column(Modifier.fillMaxWidth().padding(vertical = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Icon(Icons.Outlined.FolderOpen, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("No files yet", style = MaterialTheme.typography.titleSmall)
    Text("Files you create show up here, ready to open or share.", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

/** Recent file: tap to open, share in one tap, ⋮ for next actions (keeps users coming back to the app). */
@Composable
private fun RecentRow(r: Recent) {
    val ctx = LocalContext.current
    val nav = LocalNavigate.current
    val scope = rememberCoroutineScope()
    val uri = remember(r.uri) { Uri.parse(r.uri) }
    var menu by remember { mutableStateOf(false) }
    // Opening/sharing a file that was deleted or moved elsewhere: tell the user and drop it from the list
    fun ifExists(action: () -> Unit) = scope.launch {
        if (withContext(Dispatchers.IO) { ctx.canOpen(uri) }) action()
        else { Toast.makeText(ctx, "File not found. It was moved or deleted.", Toast.LENGTH_SHORT).show(); dao(ctx).remove(r.uri) }
    }
    val time = DateUtils.getRelativeTimeSpanString(r.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, border = cardBorder,
        modifier = Modifier.clickable { ifExists { ctx.openPdf(uri) } }) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolIcon("pdf", 40.dp)
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${label(r.op)} · ${r.size.kb()} · $time", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton({ ifExists { ctx.sharePdfs(listOf(uri)) } }) { Icon(Icons.Outlined.Share, "Share") }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                DropdownMenu(menu, { menu = false }) {
                    listOf(PdfWorker.COMPRESS, PdfWorker.PROTECT, PdfWorker.SPLIT, PdfWorker.TO_IMAGE).forEach { op ->
                        DropdownMenuItem({ Text(label(op)) }, { menu = false; ifExists { Incoming.uris = listOf(uri); nav(op) } },
                            leadingIcon = { Icon(toolStyle(op).icon, null, tint = toolStyle(op).tint) })
                    }
                    HorizontalDivider()
                    DropdownMenuItem({ Text("Remove from list") }, { menu = false; scope.launch { dao(ctx).remove(r.uri) } },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) })
                }
            }
        }
    }
}

private fun dao(ctx: android.content.Context) = (ctx.applicationContext as App).db.recents()
