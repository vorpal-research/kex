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

    fun updateTime(startTime: Long) {
        elapsedTime = Duration.of(System.currentTimeMillis() - startTime, ChronoUnit.MILLIS)
    }

    fun log() {
        if (logFile != null) {
            try {
                val writer = Files.newBufferedWriter(logFile!!.toPath(), StandardOpenOption.APPEND)
                writer.write("\"$algorithm\",\"$methodName\",$iterations,${elapsedTime.toMillis()}")
                writer.newLine()
                writer.close()
            } catch (e: IOException) {
                com.abdullin.kthelper.logging.log.warn("IOException $e occurred while writing statistics to log file.")
            }
        } else {
            com.abdullin.kthelper.logging.log.debug(this.toString())
        }
    }

    companion object {
        fun makeLogFile(file: File) {
            if (file.exists()) { file.delete() }
            file.createNewFile()
            logFile = file
            val writer = Files.newBufferedWriter(file.toPath(), StandardOpenOption.WRITE)
            writer.write("\"algorithm\",\"methodName\",iterations,elapsedTimeMillis")
            writer.newLine()
            writer.close()
        }

        var logFile: File? = null
    }
}