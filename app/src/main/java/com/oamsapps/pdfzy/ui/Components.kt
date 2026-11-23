package com.oamsapps.pdfzy.ui

import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalContext
import com.oamsapps.pdfzy.R
import androidx.compose.ui.res.stringResource
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

data class ToolStyle(val icon: ImageVector, val tint: Color, @androidx.annotation.StringRes val desc: Int)

@Suppress("DEPRECATION")
fun toolStyle(op: String) = when (op) {
    PdfWorker.SCAN -> ToolStyle(Icons.Outlined.DocumentScanner, Color(0xFF2F5BEA), R.string.desc_scan)
    PdfWorker.IMG -> ToolStyle(Icons.Outlined.Image, Color(0xFF0E9F6E), R.string.desc_img)
    PdfWorker.ID_CARD -> ToolStyle(Icons.Outlined.Badge, Color(0xFF7C5CDB), R.string.desc_id)
    PdfWorker.COMPRESS -> ToolStyle(Icons.Outlined.Compress, Color(0xFFE8710A), R.string.desc_compress)
    PdfWorker.MERGE -> ToolStyle(Icons.Outlined.CallMerge, Color(0xFF2F5BEA), R.string.desc_merge)
    PdfWorker.SPLIT -> ToolStyle(Icons.Outlined.CallSplit, Color(0xFF0E9F6E), R.string.desc_split)
    PdfWorker.EXTRACT -> ToolStyle(Icons.Outlined.ContentCut, Color(0xFF7C5CDB), R.string.desc_extract)
    PdfWorker.ROTATE -> ToolStyle(Icons.Outlined.RotateRight, Color(0xFF0B8FAC), R.string.desc_rotate)
    PdfWorker.TO_IMAGE -> ToolStyle(Icons.Outlined.PhotoLibrary, Color(0xFFD6336C), R.string.desc_to_image)
    PdfWorker.PROTECT -> ToolStyle(Icons.Outlined.Lock, Color(0xFF5B6475), R.string.desc_protect)
    PdfWorker.UNLOCK -> ToolStyle(Icons.Outlined.LockOpen, Color(0xFF5B6475), R.string.desc_unlock)
    else -> ToolStyle(Icons.Outlined.PictureAsPdf, Color(0xFFD93025), R.string.app_name)
}

fun opLabelRes(op: String): Int = when (op) {
    PdfWorker.SCAN -> R.string.op_scan; PdfWorker.IMG -> R.string.op_img; PdfWorker.ID_CARD -> R.string.op_id
    PdfWorker.COMPRESS -> R.string.op_compress; PdfWorker.MERGE -> R.string.op_merge; PdfWorker.SPLIT -> R.string.op_split
    PdfWorker.EXTRACT -> R.string.op_extract; PdfWorker.ROTATE -> R.string.op_rotate; PdfWorker.TO_IMAGE -> R.string.op_to_image
    PdfWorker.PROTECT -> R.string.op_protect; PdfWorker.UNLOCK -> R.string.op_unlock; else -> 0
}

/** Localized tool name; op ids stay English internally (routes, Recents, WorkManager data). Non-op strings pass through. */
fun android.content.Context.opLabel(op: String) = opLabelRes(op).let { if (it == 0) op else getString(it) }

@Composable
fun label(op: String): String = LocalContext.current.opLabel(op)

@Composable
fun actionLabel(op: String) = stringResource(when (op) {
    PdfWorker.COMPRESS -> R.string.op_compress; PdfWorker.MERGE -> R.string.op_merge
    PdfWorker.PROTECT -> R.string.op_protect; PdfWorker.UNLOCK -> R.string.op_unlock; else -> R.string.act_create
})

/** "1 page" / "5 pages" in the user's language (plural rules differ per language). */
@Composable
fun pluralRes(id: Int, n: Int): String = LocalContext.current.resources.getQuantityString(id, n, n)

/** Filter chip whose selected state is obvious: solid brand blue with white text. */
@Composable
fun AppChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, enabled: Boolean = true) =
    FilterChip(selected, onClick, label, enabled = enabled, colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))

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
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
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
            onUp?.let { IconButton(it) { Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.move_up)) } }
            onDown?.let { IconButton(it) { Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.move_down)) } }
            onRemove?.let { IconButton(it) { Icon(Icons.Outlined.Close, stringResource(R.string.remove)) } }
        }
    }


/** Pulsing grey placeholder while content loads. */
@Composable
fun skeletonAlpha(): Float {
    val a by rememberInfiniteTransition(label = "skeleton").animateFloat(
        0.45f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha")
    return a
}

@Composable
private fun Bar(w: Float, h: Dp) = Box(Modifier.fillMaxWidth(w).height(h).clip(RoundedCornerShape(4.dp))
    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))

/** Placeholder shaped like a file row (Recent list). */
@Composable
fun SkeletonRow() {
    val a = skeletonAlpha()
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, border = cardBorder,
        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = a }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { Bar(0.7f, 12.dp); Bar(0.4f, 10.dp) }
        }
    }
}

/** Placeholder page grid while a PDF opens. */
@Composable
fun SkeletonPageGrid(modifier: Modifier = Modifier) {
    val a = skeletonAlpha()
    LazyVerticalGrid(GridCells.Adaptive(104.dp), modifier.graphicsLayer { alpha = a }, userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(9) {
            Box(Modifier.height(124.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)))
        }
    }
}

/** "Can't open this PDF" with a one-tap route to Unlock (the usual cause is a password). */
@Composable
fun CantOpenPdf(uri: android.net.Uri?) {
    val nav = LocalNavigate.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Hint(stringResource(R.string.cant_open_pdf), error = true)
        if (uri != null) OutlinedButton({ com.oamsapps.pdfzy.Incoming.uris = listOf(uri); nav(PdfWorker.UNLOCK) }) {
            Text(stringResource(R.string.unlock_it))
        }
    }
}
