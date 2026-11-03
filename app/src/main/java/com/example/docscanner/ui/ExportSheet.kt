package com.example.docscanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.docscanner.data.DocType
import com.example.docscanner.data.ExportFormat
import com.example.docscanner.data.ExportOptions
import com.example.docscanner.data.ExportQuality
import com.example.docscanner.data.ExportTools
import com.example.docscanner.data.LocalPrefs
import com.example.docscanner.data.ScanDoc
import com.example.docscanner.util.PageSize

/** Bottom sheet where the user picks the export format and options, then exports the document. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(doc: ScanDoc, onDismiss: () -> Unit) {
    val prefs = LocalPrefs.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val task = rememberTaskState()

    var format by remember { mutableStateOf(ExportFormat.PDF) }
    var quality by remember { mutableStateOf(prefs.quality) }
    var pageSize by remember { mutableStateOf(prefs.pageSize) }
    var password by remember { mutableStateOf("") }
    var zip by remember { mutableStateOf(false) }
    var idOnOnePage by remember { mutableStateOf(doc.type == DocType.ID_CARD && doc.pages.size <= 2) }
    var name by remember { mutableStateOf(doc.name) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Export", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("File name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("Format")
            ChoiceChips(ExportFormat.values().toList(), format, { it.label }) { format = it; task.clear() }

            when (format) {
                ExportFormat.PDF -> {
                    if (doc.type == DocType.ID_CARD && doc.pages.size <= 2) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Both sides on one page", Modifier.weight(1f))
                            Switch(checked = idOnOnePage, onCheckedChange = { idOnOnePage = it })
                        }
                    }
                    if (!idOnOnePage) {
                        SectionTitle("Page size")
                        ChoiceChips(PageSize.values().toList(), pageSize, { it.label }) { pageSize = it }
                    }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ExportFormat.JPG, ExportFormat.PNG -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Put all images in one ZIP", Modifier.weight(1f))
                        Switch(checked = zip, onCheckedChange = { zip = it })
                    }
                }
                ExportFormat.TXT, ExportFormat.DOCX -> {
                    HintText("Text is read from the pages on your phone. Language: ${prefs.ocrLanguage.label} (change in Settings). Word export contains the text only.")
                }
            }

            if (format == ExportFormat.PDF || format == ExportFormat.JPG || format == ExportFormat.PNG) {
                SectionTitle("Quality")
                ChoiceChips(ExportQuality.values().toList(), quality, { it.label }) { quality = it }
            }

            BigButton(if (task.busy) "Exporting…" else "Export ${doc.pages.size} page(s)", enabled = !task.busy) {
                task.run(scope) {
                    ExportTools.export(
                        context, doc,
                        ExportOptions(
                            format = format, quality = quality, pageSize = pageSize,
                            password = password, zip = zip, idOnOnePage = idOnOnePage,
                            ocr = prefs.ocrLanguage, fileName = name
                        )
                    )
                }
            }
            TaskPanel(task)
        }
    }
}
