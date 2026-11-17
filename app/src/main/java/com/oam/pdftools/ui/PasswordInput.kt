package com.oam.pdftools.ui

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.*
import com.oam.pdftools.pdf.PdfOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Password entry for Protect (with confirm) / Unlock. Reports a usable password, or null if not ready. */
@Composable
fun PasswordInput(protect: Boolean, uri: Uri?, onChange: (String?) -> Unit) {
    val ctx = LocalContext.current
    val lock by produceState<PdfOps.Lock?>(null, uri) {
        value = null
        uri ?: return@produceState
        value = withContext(Dispatchers.IO) { runCatching { PdfOps(ctx.contentResolver).lockState(uri) }.getOrNull() }
    }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val blocked = when (lock) {
        null -> uri != null // still checking
        PdfOps.Lock.NONE -> !protect
        else -> protect
    }
    val ok = !blocked && if (protect) pw.length >= 4 && pw == pw2 else true
    LaunchedEffect(ok, pw) { onChange(if (ok) pw else null) }

    val note = when {
        uri == null -> null
        lock == null -> "Checking file…"
        protect && lock != PdfOps.Lock.NONE -> "This PDF is already protected. Unlock it first."
        !protect && lock == PdfOps.Lock.NONE -> "This PDF isn't password-protected."
        !protect && lock == PdfOps.Lock.OWNER_ONLY -> "Only editing/printing is restricted. No password needed, just tap Create."
        else -> null
    }
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (blocked && lock != null) MaterialTheme.colorScheme.error else LocalContentColor.current) }
    if (blocked || (!protect && lock == PdfOps.Lock.OWNER_ONLY)) return

    val vt = if (show) VisualTransformation.None else PasswordVisualTransformation()
    val ko = KeyboardOptions(keyboardType = KeyboardType.Password)
    OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth(), singleLine = true, visualTransformation = vt, keyboardOptions = ko,
        label = { Text("Password") }, trailingIcon = { TextButton({ show = !show }) { Text(if (show) "Hide" else "Show") } })
    if (protect) {
        OutlinedTextField(pw2, { pw2 = it }, Modifier.fillMaxWidth(), singleLine = true, visualTransformation = vt, keyboardOptions = ko,
            label = { Text("Confirm password") }, isError = pw2.isNotEmpty() && pw2 != pw)
        Text("At least 4 characters. AES-256. If you forget it, the file can't be recovered.", style = MaterialTheme.typography.bodySmall)
    }
}
