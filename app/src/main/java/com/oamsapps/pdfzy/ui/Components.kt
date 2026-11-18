package com.oamsapps.pdfzy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oamsapps.pdfzy.work.PdfWorker

/** Navigate to a tool (keeps Incoming files). Provided by MainActivity. */
val LocalNavigate = staticCompositionLocalOf<(String) -> Unit> { {} }

data class ToolStyle(val icon: ImageVector, val tint: Color, val desc: String)

@Suppress("DEPRECATION")
fun toolStyle(op: String) = when (op) {
    PdfWorker.SCAN -> ToolStyle(Icons.Outlined.DocumentScanner, Color(0xFF2F5BEA), "Paper to clean PDF")
    PdfWorker.IMG -> ToolStyle(Icons.Outlined.Image, Color(0xFF0E9F6E), "Photos & screenshots")
    PdfWorker.ID_CARD -> ToolStyle(Icons.Outlined.Badge, Color(0xFF7C5CDB), "Both sides on one page")
    PdfWorker.COMPRESS -> ToolStyle(Icons.Outlined.Compress, Color(0xFFE8710A), "Shrink to an exact size")
    PdfWorker.MERGE -> ToolStyle(Icons.Outlined.CallMerge, Color(0xFF2F5BEA), "Combine PDFs into one")
    PdfWorker.SPLIT -> ToolStyle(Icons.Outlined.CallSplit, Color(0xFF0E9F6E), "Break into smaller PDFs")
    PdfWorker.EXTRACT -> ToolStyle(Icons.Outlined.ContentCut, Color(0xFF7C5CDB), "Keep only some pages")
    PdfWorker.ROTATE -> ToolStyle(Icons.Outlined.RotateRight, Color(0xFF0B8FAC), "Fix sideways pages")
    PdfWorker.TO_IMAGE -> ToolStyle(Icons.Outlined.PhotoLibrary, Color(0xFFD6336C), "Save pages as JPG/PNG")
    PdfWorker.PROTECT -> ToolStyle(Icons.Outlined.Lock, Color(0xFF5B6475), "Add a password")
    PdfWorker.UNLOCK -> ToolStyle(Icons.Outlined.LockOpen, Color(0xFF5B6475), "Remove a password")
    else -> ToolStyle(Icons.Outlined.PictureAsPdf, Color(0xFFD93025), "")
}

fun label(op: String) = when (op) {
    PdfWorker.SCAN -> "Scan document"; PdfWorker.MERGE -> "Merge"; PdfWorker.SPLIT -> "Split"
    PdfWorker.COMPRESS -> "Compress"; PdfWorker.ROTATE -> "Rotate"; PdfWorker.EXTRACT -> "Extract pages"
    PdfWorker.PROTECT -> "Protect"; PdfWorker.UNLOCK -> "Unlock"; else -> op
}

fun actionLabel(op: String) = when (op) {
    PdfWorker.COMPRESS -> "Compress"; PdfWorker.MERGE -> "Merge"; PdfWorker.PROTECT -> "Protect"
    PdfWorker.UNLOCK -> "Unlock"; else -> "Create PDF"
}

@Composable
fun ToolIcon(op: String, size: Dp = 44.dp) {
    val s = toolStyle(op)
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 3.5f)).background(s.tint.copy(alpha = 0.12f)), Alignment.Center) {
        Icon(s.icon, null, Modifier.size(size * 0.55f), tint = s.tint)
    }
}

/** Standard tool screen: top bar with back, scrollable content, and a pinned bottom action bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolLayout(
    title: String, onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottom: (@Composable RowScope.() -> Unit)? = null,
    showAd: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) = Scaffold(
    topBar = {
        TopAppBar(
            title = { Text(label(title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = actions
        )
    },
    bottomBar = {
        if (bottom != null) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 8.dp) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = bottom
            )
        }
    }
) { pad ->
    Column(
        Modifier.fillMaxSize().padding(pad).imePadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showAd) com.oamsapps.pdfzy.ads.BannerAd()
        content()
    }
}

@Composable
fun EmptyState(op: String, title: String, hint: String, modifier: Modifier = Modifier) =
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ToolIcon(op, 72.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }

@Composable
fun Hint(text: String, error: Boolean = false) = Text(
    text, style = MaterialTheme.typography.bodySmall,
    color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))

val cardBorder @Composable get() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

/** A picked file in a list, with optional reorder/remove actions. */
@Composable
fun FileRow(name: String, sub: String?, image: Boolean = false,
            onUp: (() -> Unit)? = null, onDown: (() -> Unit)? = null, onRemove: (() -> Unit)? = null) =
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, border = cardBorder) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolIcon(if (image) PdfWorker.IMG else "pdf", 36.dp)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            onUp?.let { IconButton(it) { Icon(Icons.Outlined.ArrowUpward, "Move up") } }
            onDown?.let { IconButton(it) { Icon(Icons.Outlined.ArrowDownward, "Move down") } }
            onRemove?.let { IconButton(it) { Icon(Icons.Outlined.Close, "Remove") } }
        }
    }
