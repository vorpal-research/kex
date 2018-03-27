package org.jetbrains.research.kex.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface Loggable {
    val log: Logger
        get() = loggerFor(javaClass)
}

fun loggerFor(name: String): Logger = LoggerFactory.getLogger(name)
fun <T> loggerFor(clazz: Class<T>): Logger = LoggerFactory.getLogger(clazz)

fun <T> Logger.trace(t: T) = this.trace(t.toString())
fun <T> Logger.info(t: T) = this.info(t.toString())
fun <T> Logger.debug(t: T) = this.debug(t.toString())
fun <T> Logger.warn(t: T) = this.warn(t.toString())
fun <T> Logger.error(t: T) = this.error(t.toString())