package com.example.docscanner.util

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a minimal but valid Word (.docx) file from plain text.
 * A .docx is a zip of a few XML files, so no extra library is needed.
 */
object DocxWriter {

    private fun esc(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        // drop characters XML 1.0 cannot hold
        .filter { it == '\t' || it == '\n' || it == '\r' || it.code >= 0x20 }

    /** [pages] is one text block per scanned page. A page break is inserted between them. */
    fun write(pages: List<String>, out: File): File {
        val body = StringBuilder()
        pages.forEachIndexed { i, text ->
            if (i > 0) body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
            text.lines().forEach { line ->
                body.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(esc(line)).append("</w:t></w:r></w:p>")
            }
        }
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body</w:body></w:document>"""

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""

        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""

        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", contentTypes)
            entry("_rels/.rels", rels)
            entry("word/document.xml", document)
        }
        return out
    }
}
