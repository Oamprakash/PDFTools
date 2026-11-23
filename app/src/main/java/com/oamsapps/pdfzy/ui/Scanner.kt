package com.oamsapps.pdfzy.ui

import com.oamsapps.pdfzy.R
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.oamsapps.pdfzy.findActivity
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.oamsapps.pdfzy.findActivity
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.*
import com.oamsapps.pdfzy.findActivity
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.oamsapps.pdfzy.findActivity
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult


/**
 * ML Kit Document Scanner (Play services): edge detection, perspective crop, filters, cleanup.
 * Runs on-device; the module downloads once on first use. Returns a launch function.
 */
@Composable
fun rememberScanner(pageLimit: Int? = null, onError: (String) -> Unit, onPages: (List<Uri>) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val pages by rememberUpdatedState(onPages)
    val err by rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) runCatching {
            GmsDocumentScanningResult.fromActivityResultIntent(r.data)?.pages?.map { it.imageUri }?.let(pages)
        }.onFailure { android.util.Log.e("Scanner", "Reading scan result failed", it); err(ctx.getString(R.string.scanner_unavailable, it.message ?: "")) }
    }
    return remember(pageLimit) {
        {
            // Never let a scanner problem crash the app: show the reason instead (and log it for diagnosis)
            fun fail(t: Throwable) {
                android.util.Log.e("Scanner", "Scanner failed", t)
                err(ctx.getString(R.string.scanner_unavailable, t.message ?: t.javaClass.simpleName))
            }
            try {
                val opts = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setResultFormats(RESULT_FORMAT_JPEG)
                    .setScannerMode(SCANNER_MODE_FULL)
                    .apply { if (pageLimit != null) setPageLimit(pageLimit) }
                    .build()
                GmsDocumentScanning.getClient(opts).getStartScanIntent(ctx.findActivity())
                    .addOnSuccessListener { runCatching { launcher.launch(IntentSenderRequest.Builder(it).build()) }.onFailure(::fail) }
                    .addOnFailureListener(::fail)
            } catch (t: Throwable) { fail(t) }
        }
    }
}
