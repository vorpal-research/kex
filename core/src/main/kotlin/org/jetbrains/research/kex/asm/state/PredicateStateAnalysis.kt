package org.jetbrains.research.kex.asm.state

import org.jetbrains.research.kthelper.algorithm.NoTopologicalSortingException
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.MethodVisitor

class PredicateStateAnalysis(override val cm: ClassManager) : MethodVisitor {
    private val builders = hashMapOf<Method, PredicateStateBuilder>()

    override fun cleanup() {}

    private fun createBuilder(method: Method): PredicateStateBuilder {
        val builder = PredicateStateBuilder(method)
        try {
            builder.init()
        } catch (e: NoTopologicalSortingException) {
            log.error("Can't perform topological sorting of $method")
        }
        return builder
    }

    fun builder(method: Method) = builders.getOrPut(method) { createBuilder(method) }

    override fun visit(method: Method) {
        if (method !in builders) {
            builders[method] = createBuilder(method)
        }
    }
}