package com.oamsapps.pdfzy.billing

import com.oamsapps.pdfzy.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.oamsapps.pdfzy.findActivity

@Composable
fun RemoveAdsButton() {
    val act = LocalContext.current.findActivity()
    var open by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(false) }
    when {
        Billing.adFree.value -> { Text(stringResource(R.string.ad_free), style = MaterialTheme.typography.labelMedium); return }
        Billing.pending.value -> { Text(stringResource(R.string.payment_pending), style = MaterialTheme.typography.labelMedium); return }
        // Product not available yet (merchant account pending / product not created): hide the button entirely.
        // It appears automatically, without an app update, once the product goes live in Play Console.
        Billing.price.value == null -> return
    }
    TextButton({ open = true; unavailable = false }) { Text(stringResource(R.string.remove_ads)) }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.remove_ads)) },
        text = {
            Text(
                if (unavailable) stringResource(R.string.purchases_unavailable)
                else stringResource(R.string.one_time_purchase) + (Billing.price.value?.let { " · $it" } ?: "") + "\n\n" +
                    stringResource(R.string.remove_ads_benefits)
            )
        },
        confirmButton = {
            if (!unavailable) TextButton({ if (Billing.buy(act)) open = false else unavailable = true }) { Text(stringResource(R.string.buy)) }
        },
        dismissButton = { TextButton({ open = false }) { Text(if (unavailable) stringResource(R.string.ok) else stringResource(R.string.not_now)) } }
    )
}
