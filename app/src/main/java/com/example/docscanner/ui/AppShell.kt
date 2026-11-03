package com.example.docscanner.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.docscanner.data.DocStore
import com.example.docscanner.data.DocType
import com.example.docscanner.data.ExportOptions
import com.example.docscanner.data.ExportFormat
import com.example.docscanner.data.ExportTools
import com.example.docscanner.data.LocalPrefs
import com.example.docscanner.data.ScanDoc
import com.example.docscanner.util.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    TOOLS("Tools", Icons.Filled.Apps),
    FILES("Files", Icons.Filled.Folder),
    SETTINGS("Settings", Icons.Filled.Settings)
}

private fun modeIcon(m: ScanMode): ImageVector = when (m) {
    ScanMode.DOCUMENT -> Icons.Filled.DocumentScanner
    ScanMode.ID_CARD -> Icons.Filled.CreditCard
    ScanMode.BOOK -> Icons.Filled.AutoStories
    ScanMode.WHITEBOARD -> Icons.Filled.Dashboard
}

/** Home screen with bottom navigation. [navigate] opens any other screen by route. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(tab: String, onTab: (String) -> Unit, navigate: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = LocalPrefs.current
    val scope = rememberCoroutineScope()
    var showScanSheet by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    /** Runs [work] to create a document, then opens it in the editor. */
    fun createAndOpen(work: () -> ScanDoc) {
        importing = true
        scope.launch {
            try {
                val doc = withContext(Dispatchers.IO) { work() }
                navigate("doc/${doc.id}")
            } catch (e: Exception) {
                context.toast(e.message ?: "Could not create the document")
            } finally {
                importing = false
            }
        }
    }

    val launchers = ScanMode.values().associateWith { mode ->
        rememberScanLauncher(mode) { uris ->
            createAndOpen { DocStore.createFromImages(context, uris, mode.docType, prefs.autoEnhance) }
        }
    }
    val importImages = rememberImagePicker(multiple = true) { uris ->
        createAndOpen { DocStore.createFromImages(context, uris, DocType.IMPORTED, false) }
    }
    val importPdf = rememberPdfPicker { uris ->
        createAndOpen {
            DocStore.createFromPdf(
                context, uris.first(),
                FileStore.displayName(context, uris.first()).removeSuffix(".pdf").ifBlank { DocStore.defaultName() }
            )
        }
    }

    fun startScan(mode: ScanMode) { launchers.getValue(mode).invoke() }

    if (showScanSheet) {
        ModalBottomSheet(onDismissRequest = { showScanSheet = false }) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("New scan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Pick what you are scanning", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ScanMode.values().forEach { m ->
                        SheetRow(modeIcon(m), m.label, m.hint) { showScanSheet = false; startScan(m) }
                    }
                    SheetRow(Icons.Filled.Image, "Import images", "Make a scan from gallery photos") {
                        showScanSheet = false; importImages()
                    }
                    SheetRow(Icons.Filled.PictureAsPdf, "Import PDF", "Edit, filter and re-export a PDF") {
                        showScanSheet = false; importPdf()
                    }
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.values().forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.name,
                        onClick = { onTab(t.name) },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab != Tab.SETTINGS.name) {
                ExtendedFloatingActionButton(
                    onClick = { showScanSheet = true },
                    icon = { Icon(Icons.Filled.CameraAlt, null) },
                    text = { Text("Scan", fontWeight = FontWeight.SemiBold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.HOME.name -> HomeTab(
                    onOpenDoc = { navigate("doc/$it") },
                    onQuickScan = { startScan(it) },
                    onImport = { showScanSheet = true },
                    onOpenRoute = navigate
                )
                Tab.TOOLS.name -> ToolsTab(onOpen = { r ->
                    if (r.startsWith("scan:")) startScan(ScanMode.valueOf(r.removePrefix("scan:"))) else navigate(r)
                })
                Tab.FILES.name -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(16.dp).padding(bottom = 80.dp)
                ) {
                    Text("Files", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    HintText("PDFs, images and text files you exported or created with the tools.")
                    Box(Modifier.padding(top = 12.dp)) { FilesList() }
                }
                else -> SettingsTab()
            }
            if (importing) {
                Box(
                    Modifier.fillMaxSize().background(Color(0x66000000)).clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp) {
                        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                            Text("   Preparing your pages…")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------ Home tab

@Composable
private fun HomeTab(
    onOpenDoc: (String) -> Unit,
    onQuickScan: (ScanMode) -> Unit,
    onImport: () -> Unit,
    onOpenRoute: (String) -> Unit
) {
    val context = LocalContext.current
    val prefs = LocalPrefs.current
    val scope = rememberCoroutineScope()
    var docs by remember { mutableStateOf<List<ScanDoc>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var renaming by remember { mutableStateOf<ScanDoc?>(null) }
    var deleting by remember { mutableStateOf<ScanDoc?>(null) }

    fun refresh() {
        scope.launch {
            docs = withContext(Dispatchers.IO) { DocStore.list(context) }
            loaded = true
        }
    }
    LaunchedEffect(Unit) { refresh() }

    renaming?.let { d ->
        var text by remember(d.id) { mutableStateOf(d.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) scope.launch {
                        withContext(Dispatchers.IO) { DocStore.save(context, d.copy(name = text.trim())) }
                        refresh()
                    }
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }
    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${d.name}\"?") },
            text = { Text("This removes the scan and all its pages from the app.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { withContext(Dispatchers.IO) { DocStore.delete(context, d.id) }; refresh() }
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    val shown = docs.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            Column {
                Text("Doc Scanner", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Scan, edit and share documents. Everything stays on your phone.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(span = { GridItemSpan(2) }) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search your scans") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item(span = { GridItemSpan(2) }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuickAction(Icons.Filled.DocumentScanner, "Document") { onQuickScan(ScanMode.DOCUMENT) }
                QuickAction(Icons.Filled.CreditCard, "ID card") { onQuickScan(ScanMode.ID_CARD) }
                QuickAction(Icons.Filled.Image, "Import") { onImport() }
                QuickAction(Icons.Filled.TextFields, "Text OCR") { onOpenRoute("ocr") }
            }
        }
        item(span = { GridItemSpan(2) }) {
            Text(
                if (query.isBlank()) "Recent scans" else "Results (${shown.size})",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (loaded && shown.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.DocumentScanner, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                    Text(if (query.isBlank()) "No scans yet" else "No scan matches \"$query\"", fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp))
                    if (query.isBlank()) HintText("Tap Scan to capture your first document.")
                }
            }
        }
        items(shown, key = { it.id }) { d ->
            DocCard(
                doc = d,
                onOpen = { onOpenDoc(d.id) },
                onRename = { renaming = d },
                onDelete = { deleting = d },
                onShare = {
                    scope.launch {
                        try {
                            val f = withContext(Dispatchers.IO) {
                                ExportTools.export(context, d, ExportOptions(ExportFormat.PDF, prefs.quality, prefs.pageSize)).first()
                            }
                            FileStore.share(context, listOf(f))
                        } catch (e: Exception) {
                            context.toast("Could not share: ${e.message}")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocCard(
    doc: ScanDoc,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val thumb by produceState<Bitmap?>(null, doc.id, doc.pages.firstOrNull()) {
        value = withContext(Dispatchers.IO) { DocStore.render(context, doc, 0, 420) }
    }
    var menu by remember { mutableStateOf(false) }

    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.78f).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            thumb?.let { Image(it.asImageBitmap(), doc.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter) }
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.75f)
            ) {
                Text("${doc.pages.size}", Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelMedium)
            }
        }
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                Text(
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(doc.createdAt)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Share as PDF") }, onClick = { menu = false; onShare() })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
