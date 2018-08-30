package org.jetbrains.research.kex.asm.manager

import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.ReturnInst

private val inliningEnabled = GlobalConfig.getBooleanValue("inliner", "enabled", true)

private val ignores = GlobalConfig.getMultipleStringValue("inliner", "ignore", ",")
        .map { it.replace(".", "/") }

object MethodManager {
    private val ignorePackages = hashSetOf<Package>()
    private val ignoreClasses = hashSetOf<Class>()
    private val ignoreMethods = hashSetOf<Method>()

    init {
        for (name in ignores) {
            when {
                name.endsWith("*") -> ignorePackages.add(Package(name))
                else -> ignoreClasses.add(CM.getByName(name))
            }
        }
    }

    fun isInlinable(method: Method): Boolean {
        return when {
            !inliningEnabled -> false
            !method.isFinal -> false
            ignorePackages.any { it.isParent(method.`class`.`package`) } -> false
            ignoreClasses.any { it == method.`class` } -> false
            ignoreMethods.contains(method) -> false
            method.flatten().all { it !is ReturnInst } -> false
            else -> true
        }
    }
}