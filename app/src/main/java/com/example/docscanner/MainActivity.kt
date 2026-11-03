package com.example.docscanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.docscanner.data.AppPrefs
import com.example.docscanner.data.DocStore
import com.example.docscanner.data.LocalPrefs
import com.example.docscanner.data.ThemeMode
import com.example.docscanner.ui.AppShell
import com.example.docscanner.ui.CompressPdfScreen
import com.example.docscanner.ui.CropScreen
import com.example.docscanner.ui.DocEditorScreen
import com.example.docscanner.ui.IdCardScreen
import com.example.docscanner.ui.ImagesToPdfScreen
import com.example.docscanner.ui.InsertImageScreen
import com.example.docscanner.ui.MergeScreen
import com.example.docscanner.ui.MyFilesScreen
import com.example.docscanner.ui.OcrScreen
import com.example.docscanner.ui.OrganizeScreen
import com.example.docscanner.ui.PdfToImagesScreen
import com.example.docscanner.ui.ProtectScreen
import com.example.docscanner.ui.QrScreen
import com.example.docscanner.ui.ResizeImageScreen
import com.example.docscanner.ui.SplitScreen
import com.example.docscanner.ui.StampScreen
import com.example.docscanner.ui.theme.AppTheme
import com.example.docscanner.util.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /** A PDF opened from another app ("Open with Doc Scanner"). */
    private var incomingPdf by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        val prefs = AppPrefs(this)
        setContent {
            val dark = when (prefs.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            AppTheme(dark) {
                CompositionLocalProvider(LocalPrefs provides prefs) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AppRoot(incomingPdf) { incomingPdf = null }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) incomingPdf = intent.data
    }
}

/**
 * Screens are identified by a route string:
 * "home", a tool name such as "merge", "doc/<id>" for the editor, "crop/<id>/<pageIndex>".
 */
@Composable
private fun AppRoot(incomingPdf: Uri?, onIncomingHandled: () -> Unit) {
    val context = LocalContext.current
    var route by rememberSaveable { mutableStateOf("home") }
    var tab by rememberSaveable { mutableStateOf("HOME") }
    val home = { route = "home" }

    // A PDF opened from another app becomes a scan and opens in the editor
    if (incomingPdf != null) {
        LaunchedEffect(incomingPdf) {
            try {
                val doc = withContext(Dispatchers.IO) {
                    DocStore.createFromPdf(
                        context, incomingPdf,
                        FileStore.displayName(context, incomingPdf).removeSuffix(".pdf")
                    )
                }
                route = "doc/${doc.id}"
            } catch (e: Exception) {
                // not a readable PDF, stay on home
            }
            onIncomingHandled()
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val parts = route.split("/")
    when (parts[0]) {
        "doc" -> DocEditorScreen(parts[1], onBack = home, onCrop = { route = "crop/${parts[1]}/$it" })
        "crop" -> CropScreen(parts[1], parts[2].toInt(), onBack = { route = "doc/${parts[1]}" })
        "img2pdf" -> ImagesToPdfScreen(home)
        "pdf2img" -> PdfToImagesScreen(home)
        "resize" -> ResizeImageScreen(home)
        "compress" -> CompressPdfScreen(home)
        "merge" -> MergeScreen(home)
        "split" -> SplitScreen(home)
        "organize" -> OrganizeScreen(back = home)
        "insert" -> InsertImageScreen(home)
        "stamp" -> StampScreen(home)
        "ocr" -> OcrScreen(home)
        "qr" -> QrScreen(home)
        "idcard" -> IdCardScreen(home)
        "protect" -> ProtectScreen(unlock = false, onBack = home)
        "unlock" -> ProtectScreen(unlock = true, onBack = home)
        "files" -> MyFilesScreen(home)
        else -> AppShell(tab = tab, onTab = { tab = it }, navigate = { route = it })
    }
}
