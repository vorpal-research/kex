package org.jetbrains.research.kex.generator

import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.ClassVisitor

val Class.externalConstructors get() = ExternalConstructorCollector.externalConstructors.getOrDefault(this, setOf())

class ExternalConstructorCollector(override val cm: ClassManager) : ClassVisitor {
    companion object {
        val externalConstructors = mutableMapOf<Class, Set<Method>>()
    }

    override fun cleanup() {}

    override fun visit(`class`: Class) {
        val klassType = `class`.type
        externalConstructors[`class`] = cm.concreteClasses
                .flatMap { it.allMethods }
                .filter { it.isStatic && it.returnType.isSubtypeOf(klassType) && it.argTypes.all { arg -> !arg.isSubtypeOf(klassType) } }
                .filterNot { it.isSynthetic }
                .toSet()
    }
}