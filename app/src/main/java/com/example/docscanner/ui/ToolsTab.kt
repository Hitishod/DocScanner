package com.example.docscanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private data class Tool(val route: String, val title: String, val subtitle: String, val icon: ImageVector)
private data class ToolGroup(val name: String, val tools: List<Tool>)

/** Routes starting with "scan:" start the camera in that [ScanMode]; every other route opens a screen. */
private val toolGroups = listOf(
    ToolGroup(
        "Scan",
        listOf(
            Tool("scan:DOCUMENT", "Document", "Auto edge detect + crop", Icons.Filled.DocumentScanner),
            Tool("scan:ID_CARD", "ID card", "Front and back", Icons.Filled.CreditCard),
            Tool("scan:BOOK", "Book", "Many pages in a row", Icons.Filled.AutoStories),
            Tool("scan:WHITEBOARD", "Whiteboard", "Sharp, high contrast", Icons.Filled.Dashboard),
            Tool("ocr", "Image to text", "Copy text from photos", Icons.Filled.TextFields),
            Tool("qr", "QR code", "Scan or create", Icons.Filled.QrCode2)
        )
    ),
    ToolGroup(
        "Convert",
        listOf(
            Tool("img2pdf", "Images to PDF", "Photos into one PDF", Icons.Filled.PictureAsPdf),
            Tool("pdf2img", "PDF to images", "JPG or PNG pages", Icons.Filled.Image)
        )
    ),
    ToolGroup(
        "Reduce or enlarge",
        listOf(
            Tool("resize", "Resize image", "Percent, pixels or KB", Icons.Filled.AspectRatio),
            Tool("compress", "Compress PDF", "Make a PDF smaller", Icons.Filled.Compress)
        )
    ),
    ToolGroup(
        "Edit PDF",
        listOf(
            Tool("merge", "Merge", "Join several PDFs", Icons.Filled.CallMerge),
            Tool("split", "Split", "Extract pages", Icons.Filled.CallSplit),
            Tool("organize", "Organize", "Rotate, delete, reorder", Icons.Filled.ViewModule),
            Tool("insert", "Sign / add image", "Signature, stamp, photo", Icons.Filled.AddPhotoAlternate),
            Tool("stamp", "Watermark", "Text and page numbers", Icons.Filled.WaterDrop),
            Tool("idcard", "ID on one page", "From two photos", Icons.Filled.CreditCard)
        )
    ),
    ToolGroup(
        "Security",
        listOf(
            Tool("protect", "Lock PDF", "Add a password", Icons.Filled.Lock),
            Tool("unlock", "Unlock PDF", "Remove a password", Icons.Filled.LockOpen)
        )
    )
)

@Composable
fun ToolsTab(onOpen: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            Text("Tools", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        toolGroups.forEach { group ->
            item(span = { GridItemSpan(2) }) {
                Text(
                    group.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(group.tools) { tool -> ToolCard(tool) { onOpen(tool.route) } }
        }
    }
}

@Composable
private fun ToolCard(tool: Tool, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(tool.icon, null, tint = MaterialTheme.colorScheme.primary) }
        Column(Modifier.padding(start = 10.dp)) {
            Text(tool.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
            Text(tool.subtitle, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
