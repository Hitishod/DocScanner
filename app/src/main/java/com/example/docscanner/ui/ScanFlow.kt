package com.example.docscanner.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.example.docscanner.data.DocType
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/** The capture modes shown when the user taps Scan. */
enum class ScanMode(val label: String, val hint: String, val pageLimit: Int, val docType: DocType) {
    DOCUMENT("Document", "Letters, forms, notes, receipts", 30, DocType.DOCUMENT),
    ID_CARD("ID card", "Front and back, one page", 2, DocType.ID_CARD),
    BOOK("Book", "Many pages, one after another", 100, DocType.BOOK),
    WHITEBOARD("Whiteboard", "Boards and slides, boosted contrast", 10, DocType.WHITEBOARD)
}

/**
 * Returns a function that opens Google's scanner screen for [mode].
 * That screen finds the page edges automatically, captures when the page is steady,
 * and lets the user crop, rotate and clean the scan. [onResult] receives one image URI per page.
 */
@Composable
fun rememberScanLauncher(mode: ScanMode, onResult: (List<Uri>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onResult)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val result = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
            val uris = result?.pages?.map { it.imageUri } ?: emptyList()
            if (uris.isNotEmpty()) callback(uris)
        }
    }

    return {
        val activity = context.findActivity()
        if (activity == null) {
            context.toast("Cannot start the scanner")
        } else {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(mode.pageLimit)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            GmsDocumentScanning.getClient(options)
                .getStartScanIntent(activity)
                .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener {
                    context.toast("Scanner not available. Update Google Play services and try again.")
                }
        }
    }
}
