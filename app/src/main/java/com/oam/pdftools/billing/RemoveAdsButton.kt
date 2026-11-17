package com.oam.pdftools.billing

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.oam.pdftools.findActivity

@Composable
fun RemoveAdsButton() {
    val act = LocalContext.current.findActivity()
    var open by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(false) }
    when {
        Billing.adFree.value -> { Text("Ad-free ✓", style = MaterialTheme.typography.labelMedium); return }
        Billing.pending.value -> { Text("Payment pending…", style = MaterialTheme.typography.labelMedium); return }
    }
    TextButton({ open = true; unavailable = false }) { Text("Remove ads") }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Remove ads") },
        text = {
            Text(
                if (unavailable) "Purchases aren't available right now. Check your connection and that Google Play is signed in."
                else "One-time purchase${Billing.price.value?.let { " · $it" } ?: ""}\n\n" +
                    "• No ads anywhere\n• No videos for large batches\n• Pay once, restored automatically on reinstall"
            )
        },
        confirmButton = {
            if (!unavailable) TextButton({ if (Billing.buy(act)) open = false else unavailable = true }) { Text("Buy") }
        },
        dismissButton = { TextButton({ open = false }) { Text(if (unavailable) "OK" else "Not now") } }
    )
}
