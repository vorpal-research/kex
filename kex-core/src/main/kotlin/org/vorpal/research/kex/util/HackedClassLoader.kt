package org.vorpal.research.kex.util

import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kthelper.logging.info
import org.vorpal.research.kthelper.logging.warn
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull

sealed class HackedClassLoader(parent: ClassLoader = getSystemClassLoader()) :
    ClassLoader(parent) {

    init {
        val isJava8 = tryOrNull { System.getProperty("java.version") }?.startsWith("1.8") == true
        if (kexConfig.isMockingEnabled && kexConfig.isMockitoJava8WorkaroundEnabled && isJava8) {
            log.info { "Applying workaround for mockito on java 8" }
            if (applyJava8Workaround()) {
                log.info { "Workaround successfully applied" }
            } else {
                log.warn { "Workaround failed" }
            }
        }
    }

    private fun applyJava8Workaround(): Boolean {
        return tryOrNull {
            definePackage("org.mockito.codegen", "", "", "", "", "", "", null)
        } != null
    }
}