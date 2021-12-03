package org.jetbrains.research.kex.util

import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.util.*

private val dot by lazy { kexConfig.getStringValue("view", "dot") ?: unreachable { log.error("Could not find dot") } }
private val viewer by lazy {
    kexConfig.getStringValue("view", "viewer") ?: unreachable { log.error("Could not find viewer") }
}

fun Method.view() {
    this.view(dot, viewer)
}

infix fun <A, B> A.with(b: B): Pair<A, B> = this to b
infix fun <A, B, C> Pair<A, B>.with(c: C): Triple<A, B, C> = Triple(first, second, c)
infix fun <A, B, C> A.with(pair: Pair<B, C>): Triple<A, B, C> = Triple(this, pair.first, pair.second)

@Suppress("NOTHING_TO_INLINE")
inline fun log(name: String): Logger = LoggerFactory.getLogger(name)

fun Path.resolve(vararg paths: Path): Path {
    var result = this
    for (element in paths)
        result = result.resolve(element)
    return result
}

fun Path.resolve(vararg paths: String): Path {
    var result = this
    for (element in paths)
        result = result.resolve(element)
    return result
}

fun String.kapitalize() =
    this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

fun String.dekapitalize() =
    this.replaceFirstChar { it.lowercase(Locale.getDefault()) }

fun String.splitAtLast(char: Char): Pair<String, String> {
    val split = this.lastIndexOf(char)
    if (split < 0) return this to ""
    return substring(0, split) to substring(split + 1, length)
}

fun String.splitAtLast(str: String): Pair<String, String> {
    val split = this.lastIndexOf(str)
    if (split < 0) return this to ""
    return substring(0, split) to substring(split + 1, length)
}

fun <T : Any> T.asList() = listOf(this)

fun deleteDirectory(directoryToBeDeleted: Path) = deleteDirectory(directoryToBeDeleted.toFile())
fun deleteDirectory(directoryToBeDeleted: File): Boolean {
    val allContents = directoryToBeDeleted.listFiles()
    if (allContents != null) {
        for (file in allContents) {
            deleteDirectory(file)
        }
    }
    return directoryToBeDeleted.delete()
}