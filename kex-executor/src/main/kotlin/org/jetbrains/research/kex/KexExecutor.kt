package org.jetbrains.research.kex

import org.jetbrains.research.kthelper.logging.debug
import org.jetbrains.research.kthelper.logging.log


fun main(args: Array<String>) {
    KexExecutor().main(args)
}

class KexExecutor {
    fun main(args: Array<String>) {
        log.debug(args)
    }
}