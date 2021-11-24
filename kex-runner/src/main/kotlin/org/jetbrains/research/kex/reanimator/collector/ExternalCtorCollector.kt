package org.jetbrains.research.kex.reanimator.collector

import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.NewInst
import org.jetbrains.research.kfg.ir.value.instruction.ReturnInst
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.visitor.MethodVisitor

val Class.externalCtors: Set<Method> get() = ExternalCtorCollector.externalConstructors.getOrDefault(this, setOf())

class ExternalCtorCollector(override val cm: ClassManager, val visibilityLevel: Visibility) : MethodVisitor {
    companion object {
        val externalConstructors = mutableMapOf<Class, MutableSet<Method>>()
    }

    override fun cleanup() {}

    override fun visit(method: Method) {
        val returnType = (method.returnType as? ClassType) ?: return
        if (!(method.isStatic && method.argTypes.all { !it.isSubtypeOf(returnType) } && !method.isSynthetic)) return
        if (visibilityLevel > method.visibility) return

        var returnClass = returnType.klass
        method.flatten().firstOrNull { it is ReturnInst }?.let {
            it as ReturnInst
            if (it.returnValue is NewInst) {
                returnClass = (it.returnValue.type as ClassType).klass
            }
        }
        externalConstructors.getOrPut(returnClass, ::mutableSetOf) += method
        for (ancestor in returnClass.allAncestors) {
            externalConstructors.getOrPut(ancestor, ::mutableSetOf) += method
        }
    }
}
