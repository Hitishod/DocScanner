package com.example.docscanner.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.docscanner.util.FileStore
import com.example.docscanner.util.PageEdit
import com.example.docscanner.util.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ------------------------------------------------------------------ Merge

@Composable
fun MergeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdfs by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    val task = rememberTaskState()

    val pick = rememberPdfPicker(multiple = true) { pdfs = pdfs + it; task.clear() }

    ToolScaffold("Merge PDFs", onBack) {
        HintText("Pick two or more PDFs. They are joined in the order shown below.")
        BigButton(if (pdfs.isEmpty()) "Choose PDFs" else "Add more PDFs") { pick() }

        if (pdfs.isNotEmpty()) {
            SectionTitle("${pdfs.size} PDF(s)")
            pdfs.forEachIndexed { i, uri ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${i + 1}. ${FileStore.displayName(context, uri)}", maxLines = 2)
                        HintText(FileStore.humanSize(FileStore.sizeOf(context, uri)))
                    }
                    MoveButtons(
                        canUp = i > 0,
                        canDown = i < pdfs.lastIndex,
                        onUp = { pdfs = pdfs.swap(i, i - 1) },
                        onDown = { pdfs = pdfs.swap(i, i + 1) },
                        onRemove = { pdfs = pdfs.filterIndexed { j, _ -> j != i } }
                    )
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("File name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            BigButton("Merge", enabled = !task.busy && pdfs.size >= 2) {
                task.run(scope) {
                    val out = FileStore.namedFile(context, name, "Merged", "pdf")
                    listOf(PdfTools.merge(context, pdfs, out))
                }
            }
            if (pdfs.size < 2) HintText("Add at least one more PDF.")
            TaskPanel(task)
        }
    }
}

// ------------------------------------------------------------------ Split / extract

@Composable
fun SplitScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdf by remember { mutableStateOf<Uri?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var range by remember { mutableStateOf("") }
    val task = rememberTaskState()

    val pick = rememberPdfPicker { pdf = it.first(); task.clear() }

    LaunchedEffect(pdf) {
        pageCount = pdf?.let {
            try { withContext(Dispatchers.IO) { PdfTools.pageCount(context, it) } } catch (e: Exception) { 0 }
        } ?: 0
    }

    ToolScaffold("Split / extract pages", onBack) {
        if (pdf == null) {
            BigButton("Choose PDF") { pick() }
        } else {
            PickedFileCard(
                "${FileStore.displayName(context, pdf!!)}  ($pageCount pages)",
                FileStore.sizeOf(context, pdf!!)
            ) { pick() }

            SectionTitle("Extract pages into one new PDF")
            OutlinedTextField(
                value = range,
                onValueChange = { range = it },
                label = { Text("Pages, for example 1-3, 5, 8-10") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            BigButton("Extract these pages", enabled = !task.busy && pageCount > 0) {
                task.run(scope) {
                    val pages = PdfTools.parsePageRanges(range, pageCount)
                    require(pages.isNotEmpty()) { "Enter valid page numbers between 1 and $pageCount" }
                    val out = FileStore.newFile(context, "Extract", "pdf")
                    listOf(PdfTools.extractPages(context, pdf!!, pages, out))
                }
            }

            SectionTitle("Or split every page into its own PDF")
            TonalPickButton("Split into $pageCount separate PDFs") {
                if (!task.busy && pageCount > 0) task.run(scope) {
                    require(pageCount <= 100) { "Too many pages to split at once. Extract a range instead." }
                    val stamp = FileStore.timestamp()
                    (0 until pageCount).map { i ->
                        val out = File(FileStore.docsDir(context), "split_${stamp}_page${i + 1}.pdf")
                        PdfTools.extractPages(context, pdf!!, listOf(i), out)
                    }
                }
            }
            TaskPanel(task)
        }
    }
}

// ------------------------------------------------------------------ Organize pages

@Composable
fun OrganizeScreen(back: () -> Unit, initialPdf: Uri? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdf by remember { mutableStateOf(initialPdf) }
    var edits by remember { mutableStateOf<List<PageEdit>>(emptyList()) }
    val thumbs = remember { mutableStateMapOf<Int, Bitmap>() }
    var name by remember { mutableStateOf("") }
    val task = rememberTaskState()

    val pick = rememberPdfPicker { pdf = it.first(); task.clear() }

    LaunchedEffect(pdf) {
        thumbs.clear()
        edits = emptyList()
        val uri = pdf ?: return@LaunchedEffect
        val count = try {
            withContext(Dispatchers.IO) { PdfTools.pageCount(context, uri) }
        } catch (e: Exception) {
            task.error = "Could not open this PDF"
            0
        }
        edits = (0 until count).map { PageEdit(it) }
        for (i in 0 until count) {
            val bmp = try {
                withContext(Dispatchers.IO) { PdfTools.renderPreview(context, uri, i, 240) }
            } catch (e: Exception) {
                null
            }
            if (bmp != null) thumbs[i] = bmp
        }
    }

    ToolScaffold("Organize pages", back) {
        HintText("Rotate, delete or reorder pages, then save a new PDF.")
        if (pdf == null) {
            BigButton("Choose PDF") { pick() }
        } else {
            PickedFileCard(
                "${FileStore.displayName(context, pdf!!)}  (${edits.size} pages now)",
                FileStore.sizeOf(context, pdf!!)
            ) { pick() }

            edits.forEachIndexed { i, e ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                        val bmp = thumbs[e.sourceIndex]
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Page ${i + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .rotate(e.rotation.toFloat())
                            )
                        } else {
                            Text("…")
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("Page ${i + 1}")
                        if (e.sourceIndex != i) HintText("was page ${e.sourceIndex + 1}")
                    }
                    IconButton(onClick = {
                        edits = edits.toMutableList().also {
                            it[i] = e.copy(rotation = (e.rotation + 90) % 360)
                        }
                    }) { Icon(Icons.Filled.RotateRight, "Rotate") }
                    IconButton(onClick = { edits = edits.swap(i, i - 1) }, enabled = i > 0) {
                        Icon(Icons.Filled.ArrowUpward, "Move up")
                    }
                    IconButton(onClick = { edits = edits.swap(i, i + 1) }, enabled = i < edits.lastIndex) {
                        Icon(Icons.Filled.ArrowDownward, "Move down")
                    }
                    IconButton(onClick = { edits = edits.filterIndexed { j, _ -> j != i } }) {
                        Icon(Icons.Filled.Delete, "Delete page")
                    }
                }
            }

            if (edits.isNotEmpty()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("File name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                BigButton("Save new PDF", enabled = !task.busy) {
                    task.run(scope) {
                        val out = FileStore.namedFile(context, name, "Edited", "pdf")
                        listOf(PdfTools.rebuild(context, pdf!!, edits, out))
                    }
                }
            } else {
                HintText("All pages were removed. Choose the PDF again to start over.")
            }
            TaskPanel(task)
        }
    }
}
