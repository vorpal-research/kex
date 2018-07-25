package org.jetbrains.research.kex.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

val log: Logger
    inline get() = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

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

inline fun Logger.debug(message: () -> String) =
        if(isDebugEnabled) debug(message()) else {}
inline fun Logger.trace(message: () -> String) =
        if(isTraceEnabled) trace(message()) else {}
inline fun Logger.info(message: () -> String) =
        if(isInfoEnabled) info(message()) else {}
inline fun Logger.warn(message: () -> String) =
        if(isWarnEnabled) warn(message()) else {}
inline fun Logger.error(message: () -> String) =
        if(isErrorEnabled) error(message()) else {}