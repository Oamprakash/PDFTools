package com.oamsapps.pdfzy.ui

import com.oamsapps.pdfzy.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.oamsapps.pdfzy.kbLabel

/** null = standard compression; otherwise target size in KB. */
@Composable
fun TargetPicker(value: Int?, minBytes: Long? = null, onChange: (Int?) -> Unit) {
    var custom by remember { mutableStateOf("") }
    Text(stringResource(R.string.target_size), style = MaterialTheme.typography.titleSmall)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppChip(value == null, { custom = ""; onChange(null) }, { Text(stringResource(R.string.size_standard)) })
        listOf(100, 200, 500, 1000, 2000).forEach { k ->
            AppChip(value == k && custom.isEmpty(), { custom = ""; onChange(k) }, { Text(kbLabel(k)) },
                enabled = minBytes == null || k * 1000L >= minBytes) // greyed out: not reachable for this file
        }
    }
    OutlinedTextField(custom, { v -> custom = v.filter(Char::isDigit).take(6); custom.toIntOrNull()?.takeIf { it > 0 }?.let(onChange) },
        Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.custom_size)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}
