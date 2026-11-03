package com.example.docscanner.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.docscanner.util.FileStore
import com.example.docscanner.util.ImageTools
import com.example.docscanner.util.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private enum class ApplyTo(val label: String) {
    THIS("This page"),
    ALL("All pages"),
    RANGE("Page range")
}

@Composable
fun InsertImageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pdf by remember { mutableStateOf<Uri?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var previewPage by remember { mutableStateOf(0f) }
    var pageBmp by remember { mutableStateOf<Bitmap?>(null) }

    var baseOverlay by remember { mutableStateOf<Bitmap?>(null) }
    var removeWhite by remember { mutableStateOf(false) }
    val overlay = remember(baseOverlay, removeWhite) {
        baseOverlay?.let { if (removeWhite) ImageTools.whiteToTransparent(it) else it }
    }

    var xFrac by remember { mutableStateOf(0.3f) }
    var yFrac by remember { mutableStateOf(0.7f) }
    var widthFrac by remember { mutableStateOf(0.35f) }
    var applyTo by remember { mutableStateOf(ApplyTo.THIS) }
    var range by remember { mutableStateOf("") }
    var showPad by remember { mutableStateOf(false) }
    val task = rememberTaskState()

    val signatureFile = remember { File(context.filesDir, "signature.png") }

    val pickPdf = rememberPdfPicker { pdf = it.first(); previewPage = 0f; task.clear() }
    val pickImage = rememberImagePicker(multiple = false) { uris ->
        scope.launch {
            baseOverlay = withContext(Dispatchers.IO) { ImageTools.decode(context, uris.first(), 1400) }
            task.clear()
        }
    }

    LaunchedEffect(pdf) {
        pageCount = pdf?.let {
            try { withContext(Dispatchers.IO) { PdfTools.pageCount(context, it) } } catch (e: Exception) { 0 }
        } ?: 0
    }
    LaunchedEffect(pdf, previewPage.roundToInt(), pageCount) {
        val uri = pdf
        pageBmp = if (uri != null && pageCount > 0) {
            try {
                withContext(Dispatchers.IO) {
                    PdfTools.renderPreview(context, uri, previewPage.roundToInt().coerceIn(0, pageCount - 1), 800)
                }
            } catch (e: Exception) { null }
        } else null
    }

    if (showPad) {
        SignaturePadDialog(
            onDismiss = { showPad = false },
            onDone = { bmp ->
                showPad = false
                baseOverlay = bmp
                removeWhite = false
                scope.launch(Dispatchers.IO) {
                    signatureFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        )
    }

    ToolScaffold("Insert image / signature", onBack) {
        HintText("Add a photo, stamp, logo or your signature to a PDF. Drag it on the page to move it.")

        if (pdf == null) {
            BigButton("Choose PDF") { pickPdf() }
        } else {
            PickedFileCard(
                "${FileStore.displayName(context, pdf!!)}  ($pageCount pages)",
                FileStore.sizeOf(context, pdf!!)
            ) { pickPdf() }

            SectionTitle("1. What to insert")
            TonalPickButton("Choose image from gallery") { pickImage() }
            TonalPickButton("Draw a signature") { showPad = true }
            if (signatureFile.exists()) {
                TonalPickButton("Use my saved signature") {
                    scope.launch {
                        baseOverlay = withContext(Dispatchers.IO) {
                            android.graphics.BitmapFactory.decodeFile(signatureFile.absolutePath)
                        }
                        removeWhite = false
                    }
                }
            }
            if (baseOverlay != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Make white background transparent", Modifier.weight(1f))
                    Switch(checked = removeWhite, onCheckedChange = { removeWhite = it })
                }
            }

            SectionTitle("2. Position and size")
            if (pageCount > 1) {
                LabeledSlider(
                    "Preview page", previewPage, 0f..(pageCount - 1).toFloat(),
                    "${previewPage.roundToInt() + 1} / $pageCount", steps = (pageCount - 2).coerceAtLeast(0)
                ) { previewPage = it }
            }

            val bmp = pageBmp
            if (bmp != null) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(bmp.width.toFloat() / bmp.height)
                        .border(1.dp, Color.LightGray)
                ) {
                    val boxW = maxWidth
                    val boxH = maxHeight
                    val pxW = constraints.maxWidth.toFloat()
                    val pxH = constraints.maxHeight.toFloat()
                    Image(bmp.asImageBitmap(), "Page preview", Modifier.fillMaxSize())
                    overlay?.let { ov ->
                        val ratio = ov.height.toFloat() / ov.width
                        val hFrac = widthFrac * ratio * pxW / pxH
                        Image(
                            ov.asImageBitmap(),
                            "Image to insert",
                            Modifier
                                .offset(x = boxW * xFrac, y = boxH * yFrac)
                                .width(boxW * widthFrac)
                                .height(boxW * widthFrac * ratio)
                                .border(1.dp, Color(0x881565C0))
                                .pointerInput(widthFrac, hFrac) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        xFrac = (xFrac + drag.x / pxW).coerceIn(0f, (1f - widthFrac).coerceAtLeast(0f))
                                        yFrac = (yFrac + drag.y / pxH).coerceIn(0f, (1f - hFrac).coerceAtLeast(0f))
                                    }
                                }
                        )
                    }
                }
            }
            LabeledSlider("Size", widthFrac, 0.05f..1f, "${(widthFrac * 100).roundToInt()}% of page width") {
                widthFrac = it
                xFrac = xFrac.coerceIn(0f, (1f - it).coerceAtLeast(0f))
            }

            SectionTitle("3. Which pages")
            ChoiceChips(ApplyTo.values().toList(), applyTo, { it.label }) { applyTo = it }
            if (applyTo == ApplyTo.RANGE) {
                OutlinedTextField(
                    value = range,
                    onValueChange = { range = it },
                    label = { Text("Pages, for example 1-3, 5") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            BigButton("Insert and save PDF", enabled = !task.busy && overlay != null && pageCount > 0) {
                val ov = overlay ?: return@BigButton
                task.run(scope) {
                    val pages: Set<Int> = when (applyTo) {
                        ApplyTo.THIS -> setOf(previewPage.roundToInt())
                        ApplyTo.ALL -> (0 until pageCount).toSet()
                        ApplyTo.RANGE -> PdfTools.parsePageRanges(range, pageCount).toSet()
                    }
                    require(pages.isNotEmpty()) { "No valid pages selected" }
                    val out = FileStore.newFile(context, "Inserted", "pdf")
                    listOf(PdfTools.insertImage(context, pdf!!, ov, pages, xFrac, yFrac, widthFrac, out))
                }
            }
            if (overlay == null) HintText("Choose an image or draw a signature first.")
            TaskPanel(task)
        }
    }
}

// ------------------------------------------------------------------ Signature pad

@Composable
fun SignaturePadDialog(onDismiss: () -> Unit, onDone: (Bitmap) -> Unit) {
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }

    fun toPath(points: List<Offset>): Path = Path().apply {
        if (points.isEmpty()) return@apply
        moveTo(points[0].x, points[0].y)
        if (points.size == 1) lineTo(points[0].x + 0.1f, points[0].y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw your signature") },
        text = {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color.White)
                    .border(1.dp, Color.Gray)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { current = listOf(it) },
                            onDrag = { change, _ ->
                                change.consume()
                                current = current + change.position
                            },
                            onDragEnd = {
                                if (current.isNotEmpty()) strokes = strokes + listOf(current)
                                current = emptyList()
                            }
                        )
                    }
            ) {
                val style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                (strokes + listOf(current)).forEach { drawPath(toPath(it), Color.Black, style = style) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = strokes.isNotEmpty(),
                onClick = {
                    // Draw all strokes on a transparent bitmap, then trim it to the signature
                    val all = strokes.map { toPath(it).asAndroidPath() }
                    val bounds = RectF()
                    val union = android.graphics.Path()
                    all.forEach { union.addPath(it) }
                    union.computeBounds(bounds, true)
                    val pad = 12f
                    val w = (bounds.width() + pad * 2).toInt().coerceAtLeast(20)
                    val h = (bounds.height() + pad * 2).toInt().coerceAtLeast(20)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = AndroidCanvas(bmp)
                    canvas.translate(-bounds.left + pad, -bounds.top + pad)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = AndroidColor.BLACK
                        style = Paint.Style.STROKE
                        strokeWidth = 6f
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    all.forEach { canvas.drawPath(it, paint) }
                    onDone(bmp)
                }
            ) { Text("Use") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { strokes = emptyList(); current = emptyList() }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
