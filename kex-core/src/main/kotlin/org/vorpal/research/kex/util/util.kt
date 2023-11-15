@file:Suppress("unused")

package org.vorpal.research.kex.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.graph.Viewable
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively


private val dot by lazy { kexConfig.getStringValue("view", "dot") ?: unreachable { log.error("Could not find dot") } }
private val viewer by lazy {
    kexConfig.getStringValue("view", "viewer") ?: unreachable { log.error("Could not find viewer") }
}

fun Viewable.view() {
    this.view("", dot, viewer)
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

fun String.lowercased() = this.lowercase(Locale.getDefault())

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
fun <T : Any> T.asSet() = setOf(this)


fun deleteOnExit(file: File) = deleteOnExit(file.toPath())
fun deleteOnExit(directoryToBeDeleted: Path) = Runtime.getRuntime().addShutdownHook(Thread {
    tryOrNull {
        Files.walkFileTree(directoryToBeDeleted, object : SimpleFileVisitor<Path>() {
            override fun visitFile(
                file: Path,
                @SuppressWarnings("unused") attrs: BasicFileAttributes
            ): FileVisitResult {
                file.toFile().deleteOnExit()
                return FileVisitResult.CONTINUE
            }

            override fun preVisitDirectory(
                dir: Path,
                @SuppressWarnings("unused") attrs: BasicFileAttributes
            ): FileVisitResult {
                dir.toFile().deleteOnExit()
                return FileVisitResult.CONTINUE
            }
        })
    }
})

fun deleteDirectory(directoryToBeDeleted: Path) = deleteDirectory(directoryToBeDeleted.toFile())

@OptIn(ExperimentalPathApi::class)
fun deleteDirectory(directoryToBeDeleted: File) {
    directoryToBeDeleted.toPath().deleteRecursively()
}

fun <T> Iterable<T>.dropLast(n: Int) = take(count() - n)
fun <T> Iterator<T>.nextOrNull() = if (hasNext()) next() else null

val IntRange.length get() = maxOf(last - start, 0)
