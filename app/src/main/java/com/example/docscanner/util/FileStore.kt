package com.example.docscanner.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** File helpers: app storage, copying from content URIs, saving to Downloads and sharing. */
object FileStore {

    /** Folder that holds every document the app creates. */
    fun docsDir(context: Context): File =
        File(context.filesDir, "docs").apply { mkdirs() }

    fun tempDir(context: Context): File =
        File(context.cacheDir, "tmp").apply { mkdirs() }

    fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    fun newFile(context: Context, prefix: String, ext: String): File =
        File(docsDir(context), "${prefix}_${timestamp()}.$ext")

    /** File in the docs folder named after what the user typed. Adds a timestamp if the name is empty or taken. */
    fun namedFile(context: Context, name: String, fallbackPrefix: String, ext: String): File {
        val clean = name.trim().replace(Regex("[^A-Za-z0-9 _.-]"), "_").removeSuffix(".$ext")
        if (clean.isEmpty()) return newFile(context, fallbackPrefix, ext)
        val f = File(docsDir(context), "$clean.$ext")
        return if (f.exists()) File(docsDir(context), "${clean}_${timestamp()}.$ext") else f
    }

    fun newTempFile(context: Context, prefix: String, ext: String): File =
        File.createTempFile(prefix, ".$ext", tempDir(context))

    /** Every saved document, newest first. */
    fun listDocs(context: Context): List<File> =
        docsDir(context).listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment ?: "file"
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            } ?: (uri.lastPathSegment ?: "file")
        } catch (e: Exception) {
            uri.lastPathSegment ?: "file"
        }
    }

    fun sizeOf(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") return File(uri.path ?: return 0L).length()
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && c.moveToFirst()) c.getLong(idx) else 0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /** Copy any content/file URI into the temp folder so it can be opened as a real file. */
    fun copyToTemp(context: Context, uri: Uri, ext: String = "tmp"): File {
        val out = newTempFile(context, "in", ext)
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        } ?: error("Cannot open $uri")
        return out
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun mimeFor(file: File): String = when (file.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "txt" -> "text/plain"
        "zip" -> "application/zip"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "*/*"
    }

    fun share(context: Context, files: List<File>) {
        if (files.isEmpty()) return
        val uris = ArrayList(files.map { uriFor(context, it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeFor(files[0])
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openFile(context: Context, file: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, file), mimeFor(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Copy a file to the public Downloads/DocScanner folder.
     * Returns a short human readable location, or null on failure.
     */
    fun saveToDownloads(context: Context, file: File): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, mimeFor(file))
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DocScanner")
                }
                val target = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                context.contentResolver.openOutputStream(target)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                "Downloads/DocScanner/${file.name}"
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "DocScanner"
                ).apply { mkdirs() }
                file.copyTo(File(dir, file.name), overwrite = true)
                "Downloads/DocScanner/${file.name}"
            }
        } catch (e: Exception) {
            null
        }
    }

    fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
}
