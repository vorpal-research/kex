@file:Suppress("unused")

package org.vorpal.research.kex.util

import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.io.path.exists


fun unzipArchive(zipFile: InputStream, prefix: String): Path {
    val tempDir = Files.createTempDirectory(prefix)
    ZipInputStream(zipFile).use {
        var zipEntry = it.nextEntry

        while (zipEntry != null) {
            val newPath = zipSlipProtect(zipEntry, tempDir)
            if (zipEntry.isDirectory) {
                Files.createDirectories(newPath)
            } else {
                newPath.parent?.let { parent ->
                    if (!parent.exists()) Files.createDirectories(parent)
                }
                Files.copy(it, newPath, StandardCopyOption.REPLACE_EXISTING)
            }
            zipEntry = it.nextEntry
        }
    }
    return tempDir
}

private fun zipSlipProtect(zipEntry: ZipEntry, targetDir: Path): Path {
    val targetDirResolved = targetDir.resolve(zipEntry.name)
    val normalizePath = targetDirResolved.normalize()
    if (!normalizePath.startsWith(targetDir)) {
        throw IOException("Bad zip entry: " + zipEntry.name)
    }
    return normalizePath
}
