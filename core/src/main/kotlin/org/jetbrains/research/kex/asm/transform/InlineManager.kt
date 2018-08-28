package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.Method

object InlineManager {
    private val ignorePackages = GlobalConfig.getMultipleStringValue("inliner", "ignore").map { Package(it) }
    private val noinline = hashSetOf<Method>()

    fun isInlinable(method: Method): Boolean {
        return when {
            !method.isFinal -> false
            ignorePackages.any { it.isParent(method.`class`.`package`) } -> false
            else -> method in noinline
        }
    }
}