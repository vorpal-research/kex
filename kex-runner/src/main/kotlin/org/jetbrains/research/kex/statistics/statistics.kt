package org.jetbrains.research.kex.statistics

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.temporal.ChronoUnit

data class Statistics(val algorithm: String,
                      val methodName: String,
                      var iterations: Int,
                      var elapsedTime: Duration,
                      var satNum: Int) {

    private val startTime = System.currentTimeMillis()

    init {
        methodHolder[methodName] = this
    }

    fun stopTimeMeasurement() {
        elapsedTime = Duration.of(System.currentTimeMillis() - startTime, ChronoUnit.MILLIS)
    }

    fun log() {
        if (logFile != null) {
            try {
                val writer = Files.newBufferedWriter(logFile!!.toPath(), StandardOpenOption.APPEND)
                writer.write("\"$algorithm\",\"$methodName\",$iterations,$satNum,${elapsedTime.toMillis()}")
                writer.newLine()
                writer.flush()
                writer.close()
            } catch (e: IOException) {
                com.abdullin.kthelper.logging.log.warn("IOException $e occurred while writing statistics to log file.")
            }
        } else {
            com.abdullin.kthelper.logging.log.debug(this.toString())
        }
    }

    fun print(): String {
        return "\"$algorithm\",\"$methodName\",$iterations,$satNum,${elapsedTime.toMillis()}"
    }

    companion object {

        fun setLoggingFile(file: File, createNew: Boolean) {
            logFile = file
            if (createNew) {
                if (file.exists()) {
                    file.delete()
                }
                file.createNewFile()
                val writer = Files.newBufferedWriter(file.toPath(), StandardOpenOption.WRITE)
                writer.write("algorithm,methodName,iterations,satNum,elapsedTimeMillis")
                writer.newLine()
                writer.flush()
                writer.close()
            }
        }

        val methodHolder = mutableMapOf<String, Statistics>()

        var logFile: File? = null
            private set
    }
}