package com.example.docscanner.data

import android.content.Context
import com.example.docscanner.util.FileStore
import com.example.docscanner.util.ImageFormat
import com.example.docscanner.util.ImageTools
import com.example.docscanner.util.OcrTools
import com.example.docscanner.util.DocxWriter
import com.example.docscanner.util.PageSize
import com.example.docscanner.util.PdfProtect
import com.example.docscanner.util.PdfTools
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat(val label: String, val ext: String) {
    PDF("PDF", "pdf"),
    JPG("JPG images", "jpg"),
    PNG("PNG images", "png"),
    TXT("Text (.txt)", "txt"),
    DOCX("Word (.docx)", "docx")
}

data class ExportOptions(
    val format: ExportFormat = ExportFormat.PDF,
    val quality: ExportQuality = ExportQuality.MEDIUM,
    val pageSize: PageSize = PageSize.A4,
    val password: String = "",
    val zip: Boolean = false,
    val idOnOnePage: Boolean = false,
    val ocr: OcrLanguage = OcrLanguage.LATIN,
    val fileName: String = ""
)

/** Turns a document into a file in one of the export formats. Blocking, so run it off the main thread. */
object ExportTools {

    private fun maxDim(q: ExportQuality) = (1100 * q.scale).toInt()

    fun export(context: Context, doc: ScanDoc, o: ExportOptions): List<File> {
        require(doc.pages.isNotEmpty()) { "This document has no pages" }
        val base = o.fileName.ifBlank { doc.name }
        return when (o.format) {
            ExportFormat.PDF -> listOf(pdf(context, doc, o, base))
            ExportFormat.JPG, ExportFormat.PNG -> images(context, doc, o, base)
            ExportFormat.TXT -> listOf(text(context, doc, o, base))
            ExportFormat.DOCX -> listOf(docx(context, doc, o, base))
        }
    }

    private fun pdf(context: Context, doc: ScanDoc, o: ExportOptions, base: String): File {
        val finalFile = FileStore.namedFile(context, base, "Scan", "pdf")
        val target = if (o.password.isNotEmpty()) FileStore.newTempFile(context, "plain", "pdf") else finalFile

        if (o.idOnOnePage && doc.pages.size in 1..2) {
            val front = DocStore.render(context, doc, 0, 1800) ?: error("Cannot read page 1")
            val back = if (doc.pages.size == 2) DocStore.render(context, doc, 1, 1800) else null
            PdfTools.idCardToPdf(front, back, target, o.quality.jpeg)
        } else {
            PdfTools.pagesToPdf(doc.pages.size, target, o.pageSize, o.quality.jpeg) { i ->
                DocStore.render(context, doc, i, maxDim(o.quality))
            }
        }
        if (o.password.isNotEmpty()) {
            try {
                PdfProtect.lock(context, target, finalFile, o.password)
            } finally {
                target.delete()
            }
        }
        return finalFile
    }

    private fun images(context: Context, doc: ScanDoc, o: ExportOptions, base: String): List<File> {
        val format = if (o.format == ExportFormat.PNG) ImageFormat.PNG else ImageFormat.JPG
        val safe = base.trim().replace(Regex("[^A-Za-z0-9 _.-]"), "_").ifEmpty { "Scan" }
        val stamp = FileStore.timestamp()
        val dir = if (o.zip) FileStore.tempDir(context) else FileStore.docsDir(context)

        val files = doc.pages.indices.mapNotNull { i ->
            val bmp = DocStore.render(context, doc, i, maxDim(o.quality)) ?: return@mapNotNull null
            val f = File(dir, "${safe}_${stamp}_${i + 1}.${format.ext}")
            ImageTools.save(bmp, f, format, o.quality.jpeg)
            bmp.recycle()
            f
        }
        require(files.isNotEmpty()) { "No readable pages" }
        if (!o.zip) return files

        val zipFile = FileStore.namedFile(context, base, "Scan", "zip")
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            files.forEach { f ->
                zip.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        files.forEach { it.delete() }
        return listOf(zipFile)
    }

    private fun readText(context: Context, doc: ScanDoc, o: ExportOptions): List<String> {
        val pages = doc.pages.indices.asSequence().map { DocStore.render(context, doc, it, 2200) }
        val result = OcrTools.recognizePages(o.ocr, pages)
        require(result.any { it.isNotBlank() }) { "No text found in this document" }
        return result
    }

    private fun text(context: Context, doc: ScanDoc, o: ExportOptions, base: String): File {
        val out = FileStore.namedFile(context, base, "Scan", "txt")
        out.writeText(readText(context, doc, o).joinToString("\n\n"))
        return out
    }

    private fun docx(context: Context, doc: ScanDoc, o: ExportOptions, base: String): File {
        val out = FileStore.namedFile(context, base, "Scan", "docx")
        return DocxWriter.write(readText(context, doc, o), out)
    }
}
