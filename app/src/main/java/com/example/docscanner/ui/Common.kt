package com.example.docscanner.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.docscanner.util.FileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------------------------------------------------------------- context helpers

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun Context.toast(message: String) =
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

// ---------------------------------------------------------------- background work state

/** Holds the progress / error / output files of one tool screen. */
class TaskState {
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var results by mutableStateOf<List<File>>(emptyList())

    fun clear() {
        error = null
        results = emptyList()
    }

    /** Run [work] on a background thread. Its returned files become [results]. */
    fun run(scope: CoroutineScope, work: suspend () -> List<File>) {
        if (busy) return
        busy = true
        error = null
        results = emptyList()
        scope.launch {
            try {
                results = withContext(Dispatchers.IO) { work() }
            } catch (e: OutOfMemoryError) {
                error = "Not enough memory. Try fewer pages or a smaller size."
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong"
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun rememberTaskState(): TaskState = remember { TaskState() }

// ---------------------------------------------------------------- scaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary)
}

@Composable
fun HintText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---------------------------------------------------------------- pickers

@Composable
fun rememberImagePicker(multiple: Boolean = true, onPicked: (List<Uri>) -> Unit): () -> Unit {
    val multi = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {
        if (it.isNotEmpty()) onPicked(it)
    }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) onPicked(listOf(it))
    }
    return {
        val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (multiple) multi.launch(req) else single.launch(req)
    }
}

@Composable
fun rememberPdfPicker(multiple: Boolean = false, onPicked: (List<Uri>) -> Unit): () -> Unit {
    val multi = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        if (it.isNotEmpty()) onPicked(it)
    }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) onPicked(listOf(it))
    }
    return {
        if (multiple) multi.launch(arrayOf("application/pdf")) else single.launch(arrayOf("application/pdf"))
    }
}

// ---------------------------------------------------------------- small widgets

@Composable
fun PickedFileCard(name: String, size: Long, onChange: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Medium, maxLines = 2)
                if (size > 0) HintText(FileStore.humanSize(size))
            }
            OutlinedButton(onClick = onChange) { Text("Change") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = { Text(label(opt)) }
            )
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    steps: Int = 0,
    onChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(valueText, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
fun BigButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

@Composable
fun TonalPickButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

/** Up / down / remove buttons for a reorderable row. */
@Composable
fun MoveButtons(
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onUp, enabled = canUp) { Icon(Icons.Filled.ArrowUpward, "Move up") }
        IconButton(onClick = onDown, enabled = canDown) { Icon(Icons.Filled.ArrowDownward, "Move down") }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove") }
        }
    }
}

fun <T> List<T>.swap(a: Int, b: Int): List<T> {
    if (a !in indices || b !in indices) return this
    val m = toMutableList()
    val t = m[a]; m[a] = m[b]; m[b] = t
    return m
}

// ---------------------------------------------------------------- task output

@Composable
fun TaskPanel(state: TaskState) {
    if (state.busy) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(12.dp))
            Text("Working, please wait…")
        }
    }
    state.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
    }
    if (state.results.isNotEmpty()) ResultsCard(state.results)
}

@Composable
fun ResultsCard(files: List<File>) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (files.size == 1) "Done! File saved in the app" else "Done! ${files.size} files saved in the app",
                fontWeight = FontWeight.Bold
            )
            files.take(12).forEach { f ->
                Column {
                    Text(f.name, maxLines = 1)
                    HintText(FileStore.humanSize(f.length()))
                }
            }
            if (files.size > 12) HintText("…and ${files.size - 12} more (see My Files)")
            FlowActions(files)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowActions(files: List<File>) {
    val context = LocalContext.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (files.size == 1) {
            OutlinedButton(onClick = { FileStore.openFile(context, files[0]) }) { Text("Open") }
        }
        OutlinedButton(onClick = { FileStore.share(context, files) }) {
            Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Share")
        }
        OutlinedButton(onClick = {
            val saved = files.mapNotNull { FileStore.saveToDownloads(context, it) }
            context.toast(
                if (saved.isEmpty()) "Could not save" else "Saved to Downloads/DocScanner"
            )
        }) {
            Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Save to Downloads")
        }
    }
}
