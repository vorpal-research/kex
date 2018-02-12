package org.jetbrains.research.kex.util

val log = loggerFor("util")

fun assert(cond: Boolean, msg: String) {
    if (cond) {
        exit(msg)
    }
}

fun assert(cond: Boolean, atExit: () -> Unit) {
    if (cond) {
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