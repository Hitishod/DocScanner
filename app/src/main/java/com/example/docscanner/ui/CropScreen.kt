package com.example.docscanner.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.docscanner.data.DocStore
import com.example.docscanner.data.ScanDoc
import com.example.docscanner.util.ImageTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Crop rectangle as fractions (0..1) of the picture. */
private data class FracRect(val l: Float, val t: Float, val r: Float, val b: Float)

private const val MIN_SIZE = 0.1f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(docId: String, index: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var doc by remember { mutableStateOf<ScanDoc?>(null) }
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var crop by remember { mutableStateOf(FracRect(0.04f, 0.04f, 0.96f, 0.96f)) }
    var saving by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(onBack = onBack)

    LaunchedEffect(docId, index) {
        withContext(Dispatchers.IO) {
            val d = DocStore.load(context, docId)
            doc = d
            bmp = d?.let { DocStore.original(context, it, index, 1600) }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Crop") }) },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Cancel") }
                OutlinedButton(
                    onClick = { crop = FracRect(0f, 0f, 1f, 1f) },
                    modifier = Modifier.weight(1f)
                ) { Text("Reset") }
                Button(
                    enabled = !saving && bmp != null,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val d = doc ?: return@Button
                        saving = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val full = DocStore.original(context, d, index, 3200)
                                if (full != null) {
                                    val cropped = ImageTools.cropFraction(full, crop.l, crop.t, crop.r, crop.b)
                                    DocStore.replaceOriginal(context, d, index, cropped)
                                }
                            }
                            onBack()
                        }
                    }
                ) { Text(if (saving) "Saving…" else "Apply") }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            val b = bmp
            if (b == null) {
                Text("Loading…")
            } else {
                val primary = MaterialTheme.colorScheme.primary
                Box(Modifier.aspectRatio(b.width.toFloat() / b.height)) {
                    Image(
                        b.asImageBitmap(), "Page", Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            var active = -1 // 0..3 = corners TL TR BL BR, 4 = move whole box
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    val c = crop
                                    val corners = listOf(
                                        Offset(c.l * w, c.t * h), Offset(c.r * w, c.t * h),
                                        Offset(c.l * w, c.b * h), Offset(c.r * w, c.b * h)
                                    )
                                    val nearest = corners.withIndex().minByOrNull { (it.value - pos).getDistance() }!!
                                    active = if ((nearest.value - pos).getDistance() < 56.dp.toPx()) nearest.index else 4
                                },
                                onDrag = { change, drag ->
                                    change.consume()
                                    val dx = drag.x / size.width
                                    val dy = drag.y / size.height
                                    val c = crop
                                    crop = when (active) {
                                        0 -> c.copy(l = (c.l + dx).coerceIn(0f, c.r - MIN_SIZE), t = (c.t + dy).coerceIn(0f, c.b - MIN_SIZE))
                                        1 -> c.copy(r = (c.r + dx).coerceIn(c.l + MIN_SIZE, 1f), t = (c.t + dy).coerceIn(0f, c.b - MIN_SIZE))
                                        2 -> c.copy(l = (c.l + dx).coerceIn(0f, c.r - MIN_SIZE), b = (c.b + dy).coerceIn(c.t + MIN_SIZE, 1f))
                                        3 -> c.copy(r = (c.r + dx).coerceIn(c.l + MIN_SIZE, 1f), b = (c.b + dy).coerceIn(c.t + MIN_SIZE, 1f))
                                        4 -> {
                                            val mx = dx.coerceIn(-c.l, 1f - c.r)
                                            val my = dy.coerceIn(-c.t, 1f - c.b)
                                            FracRect(c.l + mx, c.t + my, c.r + mx, c.b + my)
                                        }
                                        else -> c
                                    }
                                },
                                onDragEnd = { active = -1 },
                                onDragCancel = { active = -1 }
                            )
                        }
                    ) {
                        val l = crop.l * size.width
                        val t = crop.t * size.height
                        val r = crop.r * size.width
                        val bt = crop.b * size.height
                        val dim = Color(0x99000000)
                        // dim the four areas outside the crop box
                        drawRect(dim, Offset(0f, 0f), Size(size.width, t))
                        drawRect(dim, Offset(0f, bt), Size(size.width, size.height - bt))
                        drawRect(dim, Offset(0f, t), Size(l, bt - t))
                        drawRect(dim, Offset(r, t), Size(size.width - r, bt - t))
                        drawRect(primary, Offset(l, t), Size(r - l, bt - t), style = Stroke(width = 4f))
                        listOf(Offset(l, t), Offset(r, t), Offset(l, bt), Offset(r, bt)).forEach {
                            drawCircle(Color.White, 16f, it)
                            drawCircle(primary, 11f, it)
                        }
                    }
                }
            }
        }
    }
}
