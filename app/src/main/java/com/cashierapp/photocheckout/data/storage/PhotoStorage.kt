package com.cashierapp.photocheckout.data.storage

import android.content.Context
import java.io.File
import java.util.UUID

public class PhotoStorage(
    context: Context,
) {
    private val rootDirectory = File(context.filesDir, DIRECTORY_NAME)

    init {
        rootDirectory.mkdirs()
    }

    public fun save(
        bytes: ByteArray,
        fileName: String,
    ): String {
        val extension = fileName.substringAfterLast(delimiter = '.', missingDelimiterValue = "jpg")
        val relativePath = "${UUID.randomUUID()}.$extension"
        val file = resolve(relativePath)
        file.writeBytes(bytes)
        return relativePath
    }

    public fun read(path: String): ByteArray = resolve(path).readBytes()

    public fun absolutePath(path: String): String = resolve(path).absolutePath

    public fun delete(path: String): Boolean = resolve(path).delete()

    public fun exists(path: String): Boolean = resolve(path).exists()

    private fun resolve(path: String): File {
        require(!path.contains("..")) { "Photo path must be relative." }
        return File(rootDirectory, path)
    }

    private companion object {
        const val DIRECTORY_NAME = "product_photos"
    }
}
