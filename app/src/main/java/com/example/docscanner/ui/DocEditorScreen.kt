package com.example.docscanner.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.docscanner.data.DocStore
import com.example.docscanner.data.ExportFormat
import com.example.docscanner.data.ExportOptions
import com.example.docscanner.data.ExportTools
import com.example.docscanner.data.LocalPrefs
import com.example.docscanner.data.PageParams
import com.example.docscanner.data.ScanDoc
import com.example.docscanner.util.FileStore
import com.example.docscanner.util.ImageFilter
import com.example.docscanner.util.ImageTools
import com.example.docscanner.util.OcrTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ------------------------------------------------------------------ page images

@Composable
private fun PageImage(doc: ScanDoc, index: Int, maxDim: Int, modifier: Modifier, scale: ContentScale) {
    val context = LocalContext.current
    val page = doc.pages.getOrNull(index)
    val bmp by produceState<Bitmap?>(null, doc.id, page) {
        value = if (page == null) null else withContext(Dispatchers.IO) { DocStore.render(context, doc, index, maxDim) }
    }
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), "Page ${index + 1}", modifier, contentScale = scale)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
    }
}

@Composable
private fun FilterThumb(doc: ScanDoc, index: Int, filter: ImageFilter, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val page = doc.pages[index]
    val bmp by produceState<Bitmap?>(null, page.file, page.rotation, filter) {
        value = withContext(Dispatchers.IO) {
            ImageTools.decode(context, Uri.fromFile(DocStore.pageFile(context, doc, index)), 240)
                ?.let { ImageTools.process(it, page.rotation, filter, 0f, 1f) }
        }
    }
    Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(60.dp, 80.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    BorderStroke(if (selected) 3.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    RoundedCornerShape(10.dp)
                )
        ) {
            bmp?.let { Image(it.asImageBitmap(), filter.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Text(
            filter.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(64.dp).padding(top = 4.dp),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ------------------------------------------------------------------ the editor

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DocEditorScreen(docId: String, onBack: () -> Unit, onCrop: (Int) -> Unit) {
    val context = LocalContext.current
    val prefs = LocalPrefs.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var doc by remember { mutableStateOf<ScanDoc?>(null) }
    var busy by remember { mutableStateOf(false) }
    var applyAll by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showPages by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var ocrText by remember { mutableStateOf<String?>(null) }

    val pagerState = rememberPagerState { doc?.pages?.size ?: 0 }
    val current = pagerState.currentPage

    LaunchedEffect(docId) {
        doc = withContext(Dispatchers.IO) { DocStore.load(context, docId) }
    }

    fun update(new: ScanDoc) {
        doc = new
        scope.launch(Dispatchers.IO) { DocStore.save(context, new) }
    }

    fun editPage(all: Boolean, change: (PageParams) -> PageParams) {
        val d = doc ?: return
        update(d.copy(pages = d.pages.mapIndexed { i, p -> if (all || i == current) change(p) else p }))
    }

    fun addImages(uris: List<Uri>) {
        val d = doc ?: return
        busy = true
        scope.launch {
            try {
                val updated = withContext(Dispatchers.IO) { DocStore.addPages(context, d, uris, prefs.autoEnhance) }
                doc = updated
                pagerState.scrollToPage(updated.pages.lastIndex)
            } catch (e: Exception) {
                context.toast("Could not add pages")
            } finally {
                busy = false
            }
        }
    }

    val scanMore = rememberScanLauncher(
        ScanMode.values().firstOrNull { it.docType == doc?.type } ?: ScanMode.DOCUMENT
    ) { addImages(it) }
    val pickGallery = rememberImagePicker(multiple = true) { addImages(it) }

    BackHandler(onBack = onBack)

    val d = doc
    if (d == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (d.pages.isEmpty()) {
        // every page was removed: nothing left to show
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { DocStore.delete(context, d.id) }; onBack() }
        return
    }
    val page = d.pages[current.coerceIn(0, d.pages.lastIndex)]

    // ----- dialogs

    if (showRename) {
        var text by remember { mutableStateOf(d.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) update(d.copy(name = text.trim()))
                    showRename = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete page ${current + 1}?") },
            text = { if (d.pages.size == 1) Text("This is the last page, so the whole document will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch(Dispatchers.IO) { DocStore.deletePageFile(context, d.id, d.pages[current]) }
                    update(d.copy(pages = d.pages.filterIndexed { i, _ -> i != current }))
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    ocrText?.let { text ->
        AlertDialog(
            onDismissRequest = { ocrText = null },
            title = { Text("Text on page ${current + 1}") },
            text = {
                SelectionContainer {
                    Text(text.ifBlank { "No text found on this page." }, Modifier.verticalScroll(rememberScrollState()))
                }
            },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(text)); context.toast("Copied"); ocrText = null }) {
                    Text("Copy")
                }
            },
            dismissButton = { TextButton(onClick = { ocrText = null }) { Text("Close") } }
        )
    }

    if (showPages) PagesDialog(
        doc = d,
        onChange = { update(it) },
        onDeletePage = { p -> scope.launch(Dispatchers.IO) { DocStore.deletePageFile(context, d.id, p) } },
        onDismiss = { showPages = false }
    )

    if (showExport) ExportSheet(d) { showExport = false }

    // ----- screen

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Column(Modifier.clickable { showRename = true }) {
                        Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${d.pages.size} page(s) • tap to rename",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val text = withContext(Dispatchers.IO) {
                                    OcrTools.recognizePages(
                                        prefs.ocrLanguage, sequenceOf(DocStore.render(context, d, current, 2200))
                                    ).firstOrNull().orEmpty()
                                }
                                ocrText = text
                            } catch (e: Exception) {
                                context.toast("Could not read text")
                            } finally {
                                busy = false
                            }
                        }
                    }) { Icon(Icons.Filled.TextFields, "Extract text") }
                    IconButton(onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    ExportTools.export(
                                        context, d,
                                        ExportOptions(ExportFormat.PDF, prefs.quality, prefs.pageSize)
                                    ).first()
                                }
                                FileStore.share(context, listOf(file))
                            } catch (e: Exception) {
                                context.toast("Could not share: ${e.message}")
                            } finally {
                                busy = false
                            }
                        }
                    }) { Icon(Icons.Filled.Share, "Share as PDF") }
                    IconButton(onClick = { showExport = true }) { Icon(Icons.Filled.FileDownload, "Export") }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().padding(top = 8.dp)) {
                    if (showAdjust) {
                        var b by remember(page.file) { mutableStateOf(page.brightness) }
                        var c by remember(page.file) { mutableStateOf(page.contrast) }
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Brightness"); Text("${b.roundToInt()}", fontWeight = FontWeight.SemiBold)
                            }
                            Slider(b, { b = it }, valueRange = -100f..100f,
                                onValueChangeFinished = { editPage(applyAll) { it.copy(brightness = b) } })
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Contrast"); Text("${(c * 100).roundToInt()}%", fontWeight = FontWeight.SemiBold)
                            }
                            Slider(c, { c = it }, valueRange = 0.5f..2f,
                                onValueChangeFinished = { editPage(applyAll) { it.copy(contrast = c) } })
                            TextButton(onClick = {
                                b = 0f; c = 1f
                                editPage(applyAll) { it.copy(brightness = 0f, contrast = 1f) }
                            }) { Text("Reset") }
                        }
                    } else {
                        LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(ImageFilter.values().toList()) { f ->
                                FilterThumb(d, current.coerceIn(0, d.pages.lastIndex), f, page.filter == f) {
                                    editPage(applyAll) { it.copy(filter = f) }
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Apply to all pages", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = applyAll, onCheckedChange = { applyAll = it })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Box {
                            ActionButton(Icons.Filled.AddPhotoAlternate, "Add") { showAddMenu = true }
                            DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                                DropdownMenuItem(text = { Text("Scan more pages") }, onClick = { showAddMenu = false; scanMore() })
                                DropdownMenuItem(text = { Text("From gallery") }, onClick = { showAddMenu = false; pickGallery() })
                            }
                        }
                        ActionButton(Icons.Filled.Crop, "Crop") { onCrop(current) }
                        ActionButton(Icons.Filled.RotateRight, "Rotate") {
                            editPage(false) { it.copy(rotation = (it.rotation + 90) % 360) }
                        }
                        ActionButton(Icons.Filled.Tune, if (showAdjust) "Filters" else "Adjust", active = showAdjust) {
                            showAdjust = !showAdjust
                        }
                        ActionButton(Icons.Filled.ViewModule, "Pages") { showPages = true }
                        ActionButton(Icons.Filled.Delete, "Delete") { confirmDelete = true }
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 12.dp,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) { i ->
                PageImage(d, i, 1600, Modifier.fillMaxSize(), ContentScale.Fit)
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f)
            ) {
                Text(
                    "${current + 1} / ${d.pages.size}",
                    Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (busy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                            Text("  Working…")
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ reorder / delete pages

@Composable
private fun PagesDialog(
    doc: ScanDoc,
    onChange: (ScanDoc) -> Unit,
    onDeletePage: (PageParams) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pages") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                doc.pages.forEachIndexed { i, p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PageImage(doc, i, 200, Modifier.size(52.dp, 68.dp).clip(RoundedCornerShape(6.dp)), ContentScale.Crop)
                        Text("Page ${i + 1}", Modifier.weight(1f).padding(start = 12.dp))
                        MoveButtons(
                            canUp = i > 0,
                            canDown = i < doc.pages.lastIndex,
                            onUp = { onChange(doc.copy(pages = doc.pages.swap(i, i - 1))) },
                            onDown = { onChange(doc.copy(pages = doc.pages.swap(i, i + 1))) },
                            onRemove = if (doc.pages.size > 1) {
                                { onDeletePage(p); onChange(doc.copy(pages = doc.pages.filterIndexed { j, _ -> j != i })) }
                            } else null
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
