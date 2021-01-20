package org.jetbrains.research.kex.reanimator.collector

import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.visitor.MethodVisitor

val Class.externalConstructors: Set<Method> get() = ExternalConstructorCollector.externalConstructors.getOrDefault(this, setOf())
private val visibilityLevel by lazy { kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC) }

class ExternalConstructorCollector(override val cm: ClassManager) : MethodVisitor {
    companion object {
        val externalConstructors = mutableMapOf<Class, MutableSet<Method>>()
    }

    override fun cleanup() {}

    override fun visit(method: Method) {
        val returnType = (method.returnType as? ClassType) ?: return
        if (!(method.isStatic && method.argTypes.all { !it.isSubtypeOf(returnType) } && !method.isSynthetic)) return
        if (visibilityLevel > method.visibility) return

        externalConstructors.getOrPut(returnType.`class`, ::mutableSetOf) += method
        returnType.`class`.allAncestors.forEach {
            externalConstructors.getOrPut(it, ::mutableSetOf) += method
        }
    }
}