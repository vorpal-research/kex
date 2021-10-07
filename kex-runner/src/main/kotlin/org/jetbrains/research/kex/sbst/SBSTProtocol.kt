package org.jetbrains.research.kex.sbst

import org.jetbrains.research.kthelper.logging.log
import java.io.*

class SBSTProtocol(input: Reader, output: Writer) {
    private val output: PrintWriter
    private val input: BufferedReader

    init {
        this.input = BufferedReader(input)
        this.output = PrintWriter(output)
    }

    fun token(string: String) {
        log.debug("expecting: $string")
        val line = input.readLine()
        log.debug("<< $line")
        if (string != line) {
            throw IOException("Unexpected: $line expecting: $string")
        }
    }

    fun directory(): File {
        log.debug("expecting existing directory")
        val line = input.readLine()
        log.debug("<< $line")
        val file = File(line)
        return if (file.exists() && file.isDirectory) {
            file
        } else {
            throw IOException("Not a valid directory name: $line")
        }
    }

    fun number(): Int {
        log.debug("expecting number")
        val line = input.readLine()
        log.debug("<< $line")
        return try {
            line.toInt()
        } catch (e: NumberFormatException) {
            throw IOException("Not a valid number: $line")
        }
    }

    fun longNumber(): Long {
        log.debug("expecting number")
        val line = input.readLine()
        log.debug("<< $line")
        return try {
            line.toLong()
        } catch (e: NumberFormatException) {
            throw IOException("Not a valid number: $line")
        }
    }

    fun directoryJarFile(): File {
        log.debug("expecting directory or jar file")
        val line = input.readLine()
        log.debug("<< $line")
        val file = File(line)
        return if (file.exists()) {
            if (file.isDirectory || file.isFile && file.name.endsWith(".jar")) {
                file
            } else {
                throw IOException("Not a valid directory/jar file name: $line")
            }
        } else {
            throw IOException("File/Directory does not exist: $line")
        }
    }

    fun className(): String {
        log.debug("expecting fully qualified class name")
        val line = input.readLine()
        log.debug("<< $line")
        return if (line.matches(Regex("[a-zA-Z_][a-zA-Z_0-9]*(\\.[a-zA-Z_][a-zA-Z_0-9]*)*"))) {
            line
        } else {
            throw IOException("Not a valid class name: $line")
        }
    }

    fun emit(string: String) {
        log.debug(">> $string")
        output.println(string)
        output.flush()
    }

    fun emit(k: Int) {
        emit("" + k)
    }

    fun emit(file: File) {
        emit(file.getAbsolutePath())
    }

    fun readLine(): String? {
        val line = input.readLine()
        log.debug("<< $line")
        return line
    }
}