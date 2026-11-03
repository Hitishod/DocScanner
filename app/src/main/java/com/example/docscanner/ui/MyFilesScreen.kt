package com.example.docscanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.docscanner.util.FileStore
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun MyFilesScreen(onBack: () -> Unit) {
    ToolScaffold("My files", onBack) { FilesList() }
}

/** The list of exported files with rename, share, save and delete. Used on the Files tab. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FilesList() {
    val context = LocalContext.current
    var files by remember { mutableStateOf(FileStore.listDocs(context)) }
    var toDelete by remember { mutableStateOf<File?>(null) }
    var toRename by remember { mutableStateOf<File?>(null) }
    var newName by remember { mutableStateOf("") }

    fun refresh() { files = FileStore.listDocs(context) }

    toDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete file?") },
            text = { Text(f.name) },
            confirmButton = {
                TextButton(onClick = { f.delete(); toDelete = null; refresh() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }

    toRename?.let { f ->
        AlertDialog(
            onDismissRequest = { toRename = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("New name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val clean = newName.trim().replace(Regex("[^A-Za-z0-9 _.-]"), "_").removeSuffix(".${f.extension}")
                    if (clean.isNotEmpty()) {
                        val target = File(f.parentFile, "$clean.${f.extension}")
                        if (target.exists()) context.toast("That name is already used") else f.renameTo(target)
                    }
                    toRename = null
                    refresh()
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { toRename = null }) { Text("Cancel") } }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (files.isEmpty()) {
            HintText("Nothing here yet. Scans and converted files you create will appear in this list.")
        }
        files.forEach { f ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                onClick = { FileStore.openFile(context, f) }
            ) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (f.extension.lowercase() in listOf("jpg", "jpeg", "png")) {
                        AsyncImage(
                            f, f.name, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            f.extension.uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp).padding(top = 14.dp)
                        )
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(f.name, maxLines = 2, fontWeight = FontWeight.Medium)
                        HintText(
                            FileStore.humanSize(f.length()) + "  •  " +
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                    .format(Date(f.lastModified()))
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            TextButton(onClick = { newName = f.nameWithoutExtension; toRename = f }) { Text("Rename") }
                        }
                    }
                    Column {
                        IconButton(onClick = { FileStore.share(context, listOf(f)) }) {
                            Icon(Icons.Filled.Share, "Share")
                        }
                        IconButton(onClick = {
                            context.toast(
                                if (FileStore.saveToDownloads(context, f) != null) "Saved to Downloads/DocScanner"
                                else "Could not save"
                            )
                        }) { Icon(Icons.Filled.Download, "Save to Downloads") }
                        IconButton(onClick = { toDelete = f }) { Icon(Icons.Filled.Delete, "Delete") }
                    }
                }
            }
        }
    }
}
