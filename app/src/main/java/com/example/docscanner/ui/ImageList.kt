package com.example.docscanner.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.docscanner.util.ImageFilter
import com.example.docscanner.util.PageSize
import kotlin.math.roundToInt

/** Vertical list of image thumbnails with move up / down / remove buttons. */
@Composable
fun ImageListEditor(uris: List<Uri>, onChange: (List<Uri>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        uris.forEachIndexed { i, uri ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = uri,
                    contentDescription = "Page ${i + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))
                )
                Text("Page ${i + 1}", Modifier.weight(1f).padding(start = 12.dp))
                MoveButtons(
                    canUp = i > 0,
                    canDown = i < uris.lastIndex,
                    onUp = { onChange(uris.swap(i, i - 1)) },
                    onDown = { onChange(uris.swap(i, i + 1)) },
                    onRemove = { onChange(uris.filterIndexed { j, _ -> j != i }) }
                )
            }
        }
    }
}

/** Settings shared by every "make a PDF from images" screen. */
class PdfOptions {
    var filter by mutableStateOf(ImageFilter.ORIGINAL)
    var pageSize by mutableStateOf(PageSize.A4)
    var quality by mutableStateOf(85f)
    var margin by mutableStateOf(0f)
    var name by mutableStateOf("")
}

@Composable
fun PdfOptionsPanel(o: PdfOptions, showFilter: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = o.name,
            onValueChange = { o.name = it },
            label = { Text("File name (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (showFilter) {
            SectionTitle("Look")
            ChoiceChips(ImageFilter.values().toList(), o.filter, { it.label }) { o.filter = it }
        }
        SectionTitle("Page size")
        ChoiceChips(PageSize.values().toList(), o.pageSize, { it.label }) { o.pageSize = it }
        if (o.pageSize != PageSize.FIT) {
            LabeledSlider("Margin", o.margin, 0f..60f, "${o.margin.roundToInt()} pt") { o.margin = it }
        }
        LabeledSlider("Quality (lower = smaller file)", o.quality, 30f..100f, "${o.quality.roundToInt()}%") {
            o.quality = it
        }
    }
}
