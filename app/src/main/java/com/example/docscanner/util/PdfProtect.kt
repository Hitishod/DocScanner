package com.example.docscanner.util

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File

/** Password lock and unlock, using PdfBox-Android because Android's own PDF classes cannot encrypt. */
object PdfProtect {

    private var ready = false

    private fun init(context: Context) {
        if (!ready) {
            PDFBoxResourceLoader.init(context.applicationContext)
            ready = true
        }
    }

    /** Lock [input] so it asks for [password] when opened. Printing stays allowed, editing and copying do not. */
    fun lock(context: Context, input: File, output: File, password: String): File {
        init(context)
        val doc = PDFBoxLoad(input, "")
        try {
            val permissions = AccessPermission().apply {
                setCanPrint(true)
                setCanModify(false)
                setCanExtractContent(false)
            }
            // owner password differs from the user password, so only the owner can change permissions
            val policy = StandardProtectionPolicy(password + "_owner", password, permissions).apply {
                encryptionKeyLength = 128
            }
            doc.protect(policy)
            doc.save(output)
        } finally {
            doc.close()
        }
        return output
    }

    /** Remove the password from [input]. Throws if [password] is wrong. */
    fun unlock(context: Context, input: File, output: File, password: String): File {
        init(context)
        val doc = try {
            PDFBoxLoad(input, password)
        } catch (e: Exception) {
            throw IllegalArgumentException("Wrong password, or this PDF cannot be opened")
        }
        try {
            doc.isAllSecurityToBeRemoved = true
            doc.save(output)
        } finally {
            doc.close()
        }
        return output
    }

    @Suppress("FunctionName")
    private fun PDFBoxLoad(file: File, password: String): PDDocument = PDDocument.load(file, password)
}
