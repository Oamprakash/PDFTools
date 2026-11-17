package com.oam.pdftools.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.oam.pdftools.kbLabel

/** null = standard compression; otherwise target size in KB. */
@Composable
fun TargetPicker(value: Int?, onChange: (Int?) -> Unit) {
    var custom by remember { mutableStateOf("") }
    Text("Target size", style = MaterialTheme.typography.titleSmall)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(value == null, { custom = ""; onChange(null) }, { Text("Standard") })
        listOf(100, 200, 500, 1000, 2000).forEach { k ->
            FilterChip(value == k && custom.isEmpty(), { custom = ""; onChange(k) }, { Text(kbLabel(k)) })
        }
    }
    OutlinedTextField(custom, { v -> custom = v.filter(Char::isDigit).take(6); custom.toIntOrNull()?.takeIf { it > 0 }?.let(onChange) },
        Modifier.fillMaxWidth(), singleLine = true, label = { Text("Custom size (KB)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}
