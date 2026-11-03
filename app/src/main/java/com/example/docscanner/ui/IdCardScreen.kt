package com.example.docscanner.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.docscanner.util.FileStore
import com.example.docscanner.util.ImageFilter
import com.example.docscanner.util.ImageTools
import com.example.docscanner.util.PdfTools

@Composable
fun IdCardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var front by remember { mutableStateOf<Uri?>(null) }
    var back by remember { mutableStateOf<Uri?>(null) }
    var filter by remember { mutableStateOf(ImageFilter.MAGIC) }
    val task = rememberTaskState()

    val pickFront = rememberImagePicker(multiple = false) { front = it.first(); task.clear() }
    val pickBack = rememberImagePicker(multiple = false) { back = it.first(); task.clear() }

    ToolScaffold("ID card copy", onBack) {
        HintText("Put the front and back of an ID card (Aadhaar, PAN, licence, college card…) on one A4 page. Tip: use \"Scan document\" first to crop each side neatly, then pick the scans here.")

        SectionTitle("Front side")
        front?.let { AsyncImage(it, "Front", Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Fit) }
        TonalPickButton(if (front == null) "Choose front image" else "Change front image") { pickFront() }

        SectionTitle("Back side (optional)")
        back?.let { AsyncImage(it, "Back", Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Fit) }
        TonalPickButton(if (back == null) "Choose back image" else "Change back image") { pickBack() }

        SectionTitle("Look")
        ChoiceChips(
            listOf(ImageFilter.ORIGINAL, ImageFilter.MAGIC, ImageFilter.GRAYSCALE),
            filter, { it.label }
        ) { filter = it }

        BigButton("Create PDF", enabled = !task.busy && front != null) {
            task.run(scope) {
                val f = ImageTools.decode(context, front!!, 1800) ?: error("Could not read the front image")
                val b = back?.let { ImageTools.decode(context, it, 1800) }
                val out = FileStore.newFile(context, "ID_card", "pdf")
                listOf(
                    PdfTools.idCardToPdf(
                        ImageTools.applyFilter(f, filter),
                        b?.let { ImageTools.applyFilter(it, filter) },
                        out
                    )
                )
            }
        }
        TaskPanel(task)
    }
}
