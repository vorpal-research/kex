package org.jetbrains.research.kex.asm.state

import org.jetbrains.research.kthelper.algorithm.NoTopologicalSortingException
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.KtException

class PredicateStateAnalysis(override val cm: ClassManager) : MethodVisitor {
    private val builders = hashMapOf<Method, PredicateStateBuilder>()

    override fun cleanup() {}

    private fun createBuilder(method: Method): PredicateStateBuilder {
        val builder = PredicateStateBuilder(method)
        try {
            builder.init()
        } catch (e: NoTopologicalSortingException) {
            log.error("Can't perform topological sorting of $method")
        } catch (e: KtException) {
            // during loop derolling we can create instructions,
            // that are not convertible into predicates
            log.error("Unexpected exception during PS building for $method: $e")
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