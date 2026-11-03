package com.example.docscanner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.docscanner.util.FileStore
import com.example.docscanner.util.ImageTools
import com.example.docscanner.util.PdfTools
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class OcrScript(val label: String) {
    LATIN("English / Latin"),
    DEVANAGARI("Hindi / Devanagari")
}

private fun recognizerFor(script: OcrScript): TextRecognizer = when (script) {
    OcrScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    OcrScript.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
}

/** Blocking call, so run it on a background thread. */
private fun recognize(recognizer: TextRecognizer, bmp: Bitmap): String =
    Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0))).text

@Composable
fun OcrScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var script by remember { mutableStateOf(OcrScript.LATIN) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedFile by remember { mutableStateOf<File?>(null) }

    fun process(work: (TextRecognizer) -> String) {
        if (busy) return
        busy = true
        error = null
        savedFile = null
        scope.launch {
            val recognizer = recognizerFor(script)
            try {
                val result = withContext(Dispatchers.IO) { work(recognizer) }
                text = result
                if (result.isBlank()) error = "No text found. Try a sharper, well lit picture."
            } catch (e: Exception) {
                error = "Could not read text: ${e.message}"
            } finally {
                recognizer.close()
                busy = false
            }
        }
    }

    val pickImages = rememberImagePicker(multiple = true) { uris ->
        process { r ->
            uris.mapNotNull { ImageTools.decode(context, it, 2600) }
                .joinToString("\n\n") { recognize(r, it) }
        }
    }
    val pickPdf = rememberPdfPicker { uris ->
        process { r ->
            val uri = uris.first()
            val count = PdfTools.pageCount(context, uri).coerceAtMost(30)
            (0 until count).joinToString("\n\n") { i ->
                recognize(r, PdfTools.renderPreview(context, uri, i, 1600))
            }
        }
    }

    ToolScaffold("Image to text (OCR)", onBack) {
        HintText("Reads printed text from photos, scans or PDFs. Everything happens on your phone, no internet needed once the language data is downloaded. PDFs: first 30 pages.")
        SectionTitle("Language")
        ChoiceChips(OcrScript.values().toList(), script, { it.label }) { script = it }

        BigButton("Choose image(s)", enabled = !busy) { pickImages() }
        TonalPickButton("Choose PDF") { pickPdf() }
        if (busy) HintText("Reading text…")
        error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }

        if (text.isNotEmpty()) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Recognized text (you can edit it)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("OCR", text))
                    context.toast("Copied")
                }) { Text("Copy") }
                OutlinedButton(onClick = {
                    val f = FileStore.newFile(context, "Text", "txt")
                    f.writeText(text)
                    savedFile = f
                }) { Text("Save .txt") }
                OutlinedButton(onClick = {
                    val f = FileStore.newTempFile(context, "Text", "txt")
                    f.writeText(text)
                    FileStore.share(context, listOf(f))
                }) { Text("Share") }
            }
            savedFile?.let { ResultsCard(listOf(it)) }
        }
    }
}
