package org.jetbrains.research.kex.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.log

fun loggerFor(name: String): Logger = LoggerFactory.getLogger(name)
fun <T> loggerFor(clazz: Class<T>): Logger = LoggerFactory.getLogger(clazz)

val log = loggerFor("util")

fun assert(cond: Boolean, msg: String) {
    if (!cond) {
        exit(msg)
    }
}

fun assert(cond: Boolean, atExit: () -> Unit) {
    if (!cond) {
        exit(atExit)
    }
}

fun exit(atExit: () -> Unit) {
    atExit()
    System.exit(1)
}

fun exit(msg: String) {
    log.error(msg)
    System.exit(1)
}