package com.example.docscanner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.docscanner.util.FileStore
import com.example.docscanner.util.ImageFormat
import com.example.docscanner.util.ImageTools
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File

private fun makeQr(content: String, size: Int = 800): Bitmap {
    val matrix = QRCodeWriter().encode(
        content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 2)
    )
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) for (y in 0 until size) {
        bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
    }
    return bmp
}

@Composable
fun QrScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanned by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    var saved by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    ToolScaffold("QR scan & create", onBack) {
        SectionTitle("Scan a QR code or barcode")
        BigButton("Open scanner") {
            error = null
            GmsBarcodeScanning.getClient(context).startScan()
                .addOnSuccessListener { scanned = it.rawValue ?: "" }
                .addOnFailureListener { e ->
                    // Cancelled by user is not an error worth showing
                    if (e.message?.contains("cancel", true) != true) {
                        error = "Scanner not available. Update Google Play services and try again."
                    }
                }
        }
        error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }

        if (scanned.isNotEmpty()) {
            OutlinedTextField(
                value = scanned,
                onValueChange = { scanned = it },
                label = { Text("Result") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("QR", scanned))
                    context.toast("Copied")
                }) { Text("Copy") }
                if (scanned.startsWith("http://") || scanned.startsWith("https://")) {
                    OutlinedButton(onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(scanned)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) {
                            context.toast("No app can open this link")
                        }
                    }) { Text("Open link") }
                }
            }
        }

        SectionTitle("Create a QR code")
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; qr = null; saved = null },
            label = { Text("Text, link, phone number, Wi-Fi…") },
            modifier = Modifier.fillMaxWidth()
        )
        TonalPickButton("Create QR code") {
            if (input.isBlank()) return@TonalPickButton
            try {
                qr = makeQr(input.trim())
                saved = null
            } catch (e: Exception) {
                context.toast("Text is too long for a QR code")
            }
        }
        qr?.let { bmp ->
            Image(bmp.asImageBitmap(), "QR code", Modifier.size(260.dp))
            BigButton("Save as PNG") {
                val f = FileStore.newFile(context, "QR", "png")
                ImageTools.save(bmp, f, ImageFormat.PNG)
                saved = f
            }
            saved?.let { ResultsCard(listOf(it)) }
        }
    }
}
