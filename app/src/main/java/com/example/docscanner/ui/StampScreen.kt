package com.example.docscanner.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.docscanner.util.FileStore
import com.example.docscanner.util.PdfTools
import kotlin.math.roundToInt

@Composable
fun StampScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdf by remember { mutableStateOf<Uri?>(null) }
    var text by remember { mutableStateOf("CONFIDENTIAL") }
    var opacity by remember { mutableStateOf(25f) }
    val task = rememberTaskState()

    val pick = rememberPdfPicker { pdf = it.first(); task.clear() }

    ToolScaffold("Watermark & page numbers", onBack) {
        if (pdf == null) {
            BigButton("Choose PDF") { pick() }
        } else {
            PickedFileCard(FileStore.displayName(context, pdf!!), FileStore.sizeOf(context, pdf!!)) { pick() }

            SectionTitle("Text watermark")
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Watermark text") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            LabeledSlider("Visibility", opacity, 10f..80f, "${opacity.roundToInt()}%") { opacity = it }
            BigButton("Add watermark", enabled = !task.busy && text.isNotBlank()) {
                task.run(scope) {
                    val out = FileStore.newFile(context, "Watermarked", "pdf")
                    listOf(PdfTools.addTextWatermark(context, pdf!!, text.trim(), out, opacity.roundToInt()))
                }
            }

            SectionTitle("Page numbers")
            TonalPickButton("Add \"Page 1 of N\" to every page") {
                if (!task.busy) task.run(scope) {
                    val out = FileStore.newFile(context, "Numbered", "pdf")
                    listOf(PdfTools.addPageNumbers(context, pdf!!, out))
                }
            }
            TaskPanel(task)
        }
    }
}
