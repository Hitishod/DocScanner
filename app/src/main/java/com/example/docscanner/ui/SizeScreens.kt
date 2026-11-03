package com.example.docscanner.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.example.docscanner.util.FileStore
import com.example.docscanner.util.ImageFormat
import com.example.docscanner.util.ImageTools
import com.example.docscanner.util.PdfTools
import java.io.File
import kotlin.math.roundToInt

// ------------------------------------------------------------------ Resize image

private enum class ResizeMode(val label: String) {
    PERCENT("Percent"),
    PIXELS("Pixels"),
    TARGET("Target file size")
}

@Composable
fun ResizeImageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var mode by remember { mutableStateOf(ResizeMode.PERCENT) }
    var percent by remember { mutableStateOf(50f) }
    var longestSide by remember { mutableStateOf("1080") }
    var targetKb by remember { mutableStateOf("200") }
    var format by remember { mutableStateOf(ImageFormat.JPG) }
    var quality by remember { mutableStateOf(85f) }
    var firstDims by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val task = rememberTaskState()

    val pick = rememberImagePicker(multiple = true) { images = it; task.clear() }

    LaunchedEffect(images) {
        firstDims = images.firstOrNull()?.let { ImageTools.dimensions(context, it) }
    }

    ToolScaffold("Resize image", onBack) {
        HintText("Make photos smaller to share or upload, or bigger when you need more pixels. You can also ask for a maximum file size in KB.")
        BigButton(if (images.isEmpty()) "Choose images" else "Choose different images") { pick() }

        if (images.isNotEmpty()) {
            val info = buildString {
                append("${images.size} image(s) selected")
                firstDims?.let { append(". First one: ${it.first} x ${it.second} px, ${FileStore.humanSize(FileStore.sizeOf(context, images[0]))}") }
            }
            HintText(info)

            SectionTitle("How to resize")
            ChoiceChips(ResizeMode.values().toList(), mode, { it.label }) { mode = it }

            when (mode) {
                ResizeMode.PERCENT -> {
                    LabeledSlider(
                        "Scale (below 100 = smaller, above 100 = bigger)",
                        percent, 10f..300f, "${percent.roundToInt()}%"
                    ) { percent = it }
                    firstDims?.let { (w, h) ->
                        HintText("First image becomes about ${(w * percent / 100).roundToInt()} x ${(h * percent / 100).roundToInt()} px")
                    }
                }
                ResizeMode.PIXELS -> {
                    OutlinedTextField(
                        value = longestSide,
                        onValueChange = { longestSide = it.filter(Char::isDigit).take(5) },
                        label = { Text("Longest side in pixels") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    ChoiceChips(listOf("640", "1080", "1600", "2048", "3000"), longestSide, { "$it px" }) {
                        longestSide = it
                    }
                    HintText("The other side follows automatically, so the picture is never stretched.")
                }
                ResizeMode.TARGET -> {
                    OutlinedTextField(
                        value = targetKb,
                        onValueChange = { targetKb = it.filter(Char::isDigit).take(5) },
                        label = { Text("Maximum size in KB") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    ChoiceChips(listOf("20", "50", "100", "200", "500", "1000"), targetKb, { "$it KB" }) {
                        targetKb = it
                    }
                    HintText("Handy for forms that say \"photo under 100 KB\". The result is always a JPG.")
                }
            }

            if (mode != ResizeMode.TARGET) {
                SectionTitle("Format")
                ChoiceChips(ImageFormat.values().toList(), format, { it.label }) { format = it }
                if (format == ImageFormat.JPG) {
                    LabeledSlider("JPG quality", quality, 30f..100f, "${quality.roundToInt()}%") { quality = it }
                }
            }

            BigButton("Resize", enabled = !task.busy) {
                task.run(scope) {
                    val outputs = mutableListOf<File>()
                    images.forEachIndexed { i, uri ->
                        val bmp = ImageTools.decode(context, uri, 5000)
                            ?: error("Could not read image ${i + 1}")
                        val stamp = FileStore.timestamp()
                        when (mode) {
                            ResizeMode.PERCENT, ResizeMode.PIXELS -> {
                                val scaled = if (mode == ResizeMode.PERCENT) {
                                    ImageTools.scaleByPercent(bmp, percent.roundToInt())
                                } else {
                                    val px = longestSide.toIntOrNull() ?: error("Enter the size in pixels")
                                    ImageTools.scaleToLongest(bmp, px)
                                }
                                val f = File(FileStore.docsDir(context), "resized_${stamp}_${i + 1}.${format.ext}")
                                outputs += ImageTools.save(scaled, f, format, quality.roundToInt())
                            }
                            ResizeMode.TARGET -> {
                                val kb = targetKb.toIntOrNull()?.takeIf { it > 0 } ?: error("Enter the size in KB")
                                val bytes = ImageTools.compressToTarget(bmp, kb)
                                val f = File(FileStore.docsDir(context), "resized_${stamp}_${i + 1}.jpg")
                                f.writeBytes(bytes)
                                outputs += f
                            }
                        }
                    }
                    outputs
                }
            }
            TaskPanel(task)
        }
    }
}

// ------------------------------------------------------------------ Compress PDF

private enum class Strength(val label: String, val scale: Float, val quality: Int) {
    LIGHT("Light (best quality)", 2.0f, 85),
    BALANCED("Balanced", 1.5f, 70),
    STRONG("Strong", 1.0f, 50),
    EXTREME("Extreme (smallest)", 0.7f, 35)
}

private enum class CompressMode(val label: String) {
    PRESET("Preset"),
    TARGET("Target size")
}

@Composable
fun CompressPdfScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdf by remember { mutableStateOf<Uri?>(null) }
    var mode by remember { mutableStateOf(CompressMode.PRESET) }
    var strength by remember { mutableStateOf(Strength.BALANCED) }
    var targetKb by remember { mutableStateOf("500") }
    val task = rememberTaskState()

    val pick = rememberPdfPicker { pdf = it.first(); task.clear() }

    ToolScaffold("Compress PDF", onBack) {
        HintText("Shrinks a PDF by re-saving every page as a lighter picture. Text stays readable but can no longer be selected or searched.")
        if (pdf == null) {
            BigButton("Choose PDF") { pick() }
        } else {
            PickedFileCard(FileStore.displayName(context, pdf!!), FileStore.sizeOf(context, pdf!!)) { pick() }

            ChoiceChips(CompressMode.values().toList(), mode, { it.label }) { mode = it }
            if (mode == CompressMode.PRESET) {
                ChoiceChips(Strength.values().toList(), strength, { it.label }) { strength = it }
            } else {
                OutlinedTextField(
                    value = targetKb,
                    onValueChange = { targetKb = it.filter(Char::isDigit).take(6) },
                    label = { Text("Maximum size in KB") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                ChoiceChips(listOf("100", "200", "500", "1000", "2000"), targetKb, { "$it KB" }) { targetKb = it }
                HintText("The app tries stronger and stronger settings until the file fits. Very small targets may not be reachable for long documents.")
            }

            BigButton("Compress", enabled = !task.busy) {
                task.run(scope) {
                    val base = FileStore.displayName(context, pdf!!).removeSuffix(".pdf")
                        .replace(Regex("[^A-Za-z0-9 _.-]"), "_").take(40)
                    val out = File(FileStore.docsDir(context), "${base}_compressed_${FileStore.timestamp()}.pdf")
                    if (mode == CompressMode.PRESET) {
                        PdfTools.compress(context, pdf!!, out, strength.scale, strength.quality)
                    } else {
                        val kb = targetKb.toIntOrNull()?.takeIf { it > 0 } ?: error("Enter the size in KB")
                        PdfTools.compressToTarget(context, pdf!!, out, kb)
                    }
                    listOf(out)
                }
            }
            TaskPanel(task)
        }
    }
}
