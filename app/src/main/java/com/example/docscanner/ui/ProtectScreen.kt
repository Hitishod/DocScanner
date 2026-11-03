package com.example.docscanner.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.docscanner.util.FileStore
import com.example.docscanner.util.PdfProtect
import java.io.File

/** Add a password to a PDF ([unlock] = false) or remove it ([unlock] = true). */
@Composable
fun ProtectScreen(unlock: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdf by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val task = rememberTaskState()

    val pick = rememberPdfPicker { pdf = it.first(); task.clear() }
    val mismatch = !unlock && confirm.isNotEmpty() && confirm != password
    val ready = pdf != null && password.isNotEmpty() && (unlock || password == confirm)

    ToolScaffold(if (unlock) "Unlock PDF" else "Lock PDF", onBack) {
        HintText(
            if (unlock) "Enter the PDF's current password. A new copy without a password is saved, so the original stays locked."
            else "Anyone opening the new PDF must type this password. Keep it safe: it cannot be recovered."
        )
        if (pdf == null) {
            BigButton("Choose PDF") { pick() }
        } else {
            PickedFileCard(FileStore.displayName(context, pdf!!), FileStore.sizeOf(context, pdf!!)) { pick() }
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; task.clear() },
            label = { Text(if (unlock) "Current password" else "New password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        if (!unlock) {
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text("Repeat password") },
                singleLine = true,
                isError = mismatch,
                supportingText = { if (mismatch) Text("Passwords do not match") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
        }
        BigButton(if (unlock) "Unlock" else "Lock", enabled = ready && !task.busy) {
            val uri = pdf ?: return@BigButton
            task.run(scope) {
                val input = FileStore.copyToTemp(context, uri, "pdf")
                try {
                    val base = FileStore.displayName(context, uri).removeSuffix(".pdf")
                        .replace(Regex("[^A-Za-z0-9 _.-]"), "_").take(40)
                    val out = File(
                        FileStore.docsDir(context),
                        "${base}_${if (unlock) "unlocked" else "locked"}_${FileStore.timestamp()}.pdf"
                    )
                    listOf(
                        if (unlock) PdfProtect.unlock(context, input, out, password)
                        else PdfProtect.lock(context, input, out, password)
                    )
                } finally {
                    input.delete()
                }
            }
        }
        TaskPanel(task)
    }
}
