package com.oamsapps.pdfzy.ui

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
        if (r.resultCode == Activity.RESULT_OK)
            GmsDocumentScanningResult.fromActivityResultIntent(r.data)?.pages?.map { it.imageUri }?.let(pages)
    }
    return remember(pageLimit) {
        {
            val opts = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setResultFormats(RESULT_FORMAT_JPEG)
                .setScannerMode(SCANNER_MODE_FULL)
                .apply { if (pageLimit != null) setPageLimit(pageLimit) }
                .build()
            GmsDocumentScanning.getClient(opts).getStartScanIntent(ctx.findActivity())
                .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener { err("Scanner unavailable on this device (${it.message}).") }
        }
    }
}
