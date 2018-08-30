package org.jetbrains.research.kex.asm.state

import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.asm.transform.MethodInliner
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.util.error
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.analysis.IRVerifier
import org.jetbrains.research.kfg.analysis.LoopAnalysis
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.ir.Method

object PredicateStateAnalysis {
    val builders = hashMapOf<Method, PredicateStateBuilder>()
    private val irInliningEnabled = GlobalConfig.getBooleanValue("inliner", "ir-inlining", false)

    private fun prepareMethod(method: Method) {
        if (irInliningEnabled) {
            MethodInliner(method).visit()
        }

        val la = LoopAnalysis(method)
        la.visit()

        if (la.loops.isNotEmpty()) {
            val simplifier = LoopSimplifier(method)
            simplifier.visit()
            val deroller = LoopDeroller(method)
            deroller.visit()
        }
        IRVerifier(method).visit()
    }

    private fun createMethodBuilder(method: Method): PredicateStateBuilder {
        prepareMethod(method)

        val psb = PredicateStateBuilder(method)
        try {
            psb.visit()
        } catch (e: NoTopologicalSortingError) {
            log.error(e)
        }
        return psb
    }

    fun builder(method: Method) = builders.getOrPut(method) {
        createMethodBuilder(method)
    }
}