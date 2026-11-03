package com.example.docscanner.ui

import android.net.Uri
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.docscanner.util.FileStore
import com.example.docscanner.util.ImageFormat
import com.example.docscanner.util.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

// ------------------------------------------------------------------ Images -> PDF

@Composable
fun ImagesToPdfScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val options = remember { PdfOptions() }
    val task = rememberTaskState()

    val pick = rememberImagePicker(multiple = true) { images = images + it; task.clear() }

    ToolScaffold("Images to PDF", onBack) {
        HintText("Choose photos from your gallery. Each image becomes one page. Use the arrows to change the order.")
        BigButton(if (images.isEmpty()) "Choose images" else "Add more images") { pick() }

        if (images.isNotEmpty()) {
            SectionTitle("${images.size} image(s)")
            ImageListEditor(images) { images = it; task.clear() }
            PdfOptionsPanel(options)
            BigButton("Create PDF", enabled = !task.busy) {
                task.run(scope) {
                    val out = FileStore.namedFile(context, options.name, "Images", "pdf")
                    PdfTools.imageUrisToPdf(
                        context, images, out, options.pageSize, options.margin,
                        options.quality.toInt(), options.filter
                    )
                    listOf(out)
                }
            }
            TaskPanel(task)
        }
    }
}

// ------------------------------------------------------------------ PDF -> images

private enum class Quality(val label: String, val scale: Float) {
    NORMAL("Normal (108 dpi)", 1.5f),
    HIGH("High (144 dpi)", 2f),
    VERY_HIGH("Very high (216 dpi)", 3f)
}

@Composable
fun PdfToImagesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdf by remember { mutableStateOf<Uri?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var quality by remember { mutableStateOf(Quality.HIGH) }
    var format by remember { mutableStateOf(ImageFormat.JPG) }
    var range by remember { mutableStateOf("") }
    val task = rememberTaskState()

    val pick = rememberPdfPicker { pdf = it.first(); task.clear() }

    LaunchedEffect(pdf) {
        pageCount = pdf?.let {
            try { withContext(Dispatchers.IO) { PdfTools.pageCount(context, it) } } catch (e: Exception) { 0 }
        } ?: 0
    }

    ToolScaffold("PDF to images", onBack) {
        if (pdf == null) {
            BigButton("Choose PDF") { pick() }
        } else {
            PickedFileCard(
                "${FileStore.displayName(context, pdf!!)}  ($pageCount pages)",
                FileStore.sizeOf(context, pdf!!)
            ) { pick() }

            SectionTitle("Image quality")
            ChoiceChips(Quality.values().toList(), quality, { it.label }) { quality = it }
            SectionTitle("Format")
            ChoiceChips(ImageFormat.values().toList(), format, { it.label }) { format = it }
            OutlinedTextField(
                value = range,
                onValueChange = { range = it },
                label = { Text("Pages (empty = all). Example: 1-3, 5") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            BigButton("Convert to images", enabled = !task.busy && pageCount > 0) {
                task.run(scope) {
                    val pages = if (range.isBlank()) null else PdfTools.parsePageRanges(range, pageCount)
                    require(pages == null || pages.isNotEmpty()) { "Those page numbers are not in this PDF" }
                    val base = FileStore.displayName(context, pdf!!)
                        .removeSuffix(".pdf").replace(Regex("[^A-Za-z0-9 _.-]"), "_").take(40)
                    PdfTools.pdfToImages(
                        context, pdf!!, FileStore.docsDir(context), base,
                        quality.scale, format, 90, pages
                    )
                }
            }
            TaskPanel(task)
        }
    }
}
