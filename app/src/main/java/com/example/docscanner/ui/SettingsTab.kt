package com.example.docscanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.docscanner.data.DocStore
import com.example.docscanner.data.ExportQuality
import com.example.docscanner.data.LocalPrefs
import com.example.docscanner.data.OcrLanguage
import com.example.docscanner.data.ThemeMode
import com.example.docscanner.util.FileStore
import com.example.docscanner.util.PageSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
fun SettingsTab() {
    val context = LocalContext.current
    val prefs = LocalPrefs.current
    var scans by remember { mutableStateOf(0) }
    var bytes by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            scans = DocStore.list(context).size
            bytes = DocStore.storageBytes(context)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        SettingsCard("Appearance") {
            ChoiceChips(ThemeMode.values().toList(), prefs.theme, { it.label }) { prefs.updateTheme(it) }
        }

        SettingsCard("Scanning") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto enhance new scans")
                    HintText("Applies the Auto color filter to documents and books. You can change it per page.")
                }
                Switch(checked = prefs.autoEnhance, onCheckedChange = { prefs.updateAutoEnhance(it) })
            }
        }

        SettingsCard("Export defaults") {
            Text("Quality")
            ChoiceChips(ExportQuality.values().toList(), prefs.quality, { it.label }) { prefs.updateQuality(it) }
            Text("PDF page size")
            ChoiceChips(PageSize.values().toList(), prefs.pageSize, { it.label }) { prefs.updatePageSize(it) }
        }

        SettingsCard("Text recognition language") {
            ChoiceChips(OcrLanguage.values().toList(), prefs.ocrLanguage, { it.label }) { prefs.updateOcrLanguage(it) }
            HintText("Used by Extract text, TXT and Word export, and Image to text.")
        }

        SettingsCard("Storage") {
            Text("$scans scan(s) using ${FileStore.humanSize(bytes)}")
            OutlinedButton(onClick = {
                FileStore.tempDir(context).deleteRecursively()
                context.toast("Temporary files cleared")
            }) { Text("Clear temporary files") }
        }

        SettingsCard("About") {
            Text("Doc Scanner 1.0")
            HintText("All scanning, editing and text recognition happen on your phone. Nothing is uploaded and there is no account.")
        }
    }
}
