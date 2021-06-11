package org.jetbrains.research.kex

import org.jetbrains.research.kthelper.logging.debug
import org.jetbrains.research.kthelper.logging.log


fun main(args: Array<String>) {
    KexExecutor().main(args)
}

class KexExecutor {
    fun main(args: Array<String>) {
        System.setProperty("kex-executor.log.name", "kex-executor.log")
        log.debug(args)
    }
}