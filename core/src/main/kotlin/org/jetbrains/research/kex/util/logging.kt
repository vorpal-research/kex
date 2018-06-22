package org.jetbrains.research.kex.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

interface Loggable {
    val log: Logger
        get() = loggerFor(javaClass)
}

fun loggerFor(name: String): Logger = LoggerFactory.getLogger(name)
fun <T> loggerFor(clazz: Class<T>): Logger = LoggerFactory.getLogger(clazz)
fun <T : Any> loggerFor(clazz: KClass<T>): Logger = LoggerFactory.getLogger(clazz.java)

fun Logger.trace() = this.trace("")
fun <T> Logger.trace(t: T) = this.trace(t.toString())

fun Logger.info() = this.info("")
fun <T> Logger.info(t: T) = this.info(t.toString())

fun Logger.debug() = this.debug("")
fun <T> Logger.debug(t: T) = this.debug(t.toString())

fun Logger.warn() = this.warn("")
fun <T> Logger.warn(t: T) = this.warn(t.toString())

fun Logger.error() = this.error("")
fun <T> Logger.error(t: T) = this.error(t.toString())