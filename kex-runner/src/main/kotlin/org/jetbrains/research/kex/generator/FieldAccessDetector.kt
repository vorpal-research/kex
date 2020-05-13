package org.jetbrains.research.kex.generator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.annotations.AnnotationManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.AnnotationIncluder
import org.jetbrains.research.kex.state.transformer.MethodInliner
import org.jetbrains.research.kex.state.transformer.collectFieldAccesses
import org.jetbrains.research.kex.state.transformer.transform
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.MethodVisitor

private val isInliningEnabled by lazy { kexConfig.getBooleanValue("smt", "ps-inlining", true) }
private val annotationsEnabled by lazy { kexConfig.getBooleanValue("annotations", "enabled", false) }

val Method.fieldAccesses get() = MethodFieldAccessDetector.fieldAccessMap.getOrDefault(this, setOf())

class MethodFieldAccessDetector(val ctx: ExecutionContext, val psa: PredicateStateAnalysis) : MethodVisitor {
    override val cm: ClassManager get() = ctx.cm
    companion object {
        val fieldAccessMap: Map<Method, Set<Field>> get() = methodAccessMap

        private val methodAccessMap = hashMapOf<Method, Set<Field>>()
    }

    override fun cleanup() {}


    override fun visit(method: Method) {
        val methodState = psa.builder(method).methodState ?: return
        val preparedState = prepareState(method, methodState)
        val fieldAccessList = collectFieldAccesses(ctx, preparedState)
        methodAccessMap[method] = fieldAccessList
    }


    private fun prepareState(method: Method, ps: PredicateState) = transform(ps) {
        if (annotationsEnabled) +AnnotationIncluder(method, AnnotationManager.defaultLoader)
        if (isInliningEnabled) +MethodInliner(psa)
    }
}
