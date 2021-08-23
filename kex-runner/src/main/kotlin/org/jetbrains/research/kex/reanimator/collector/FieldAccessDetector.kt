package org.jetbrains.research.kex.reanimator.collector

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.annotations.AnnotationManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.ktype.KexRtManager.isJavaRt
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.MethodVisitor

val Method.fieldAccesses get() = MethodFieldAccessCollector.fieldAccessMap.getOrDefault(this, setOf())

class MethodFieldAccessCollector(val ctx: ExecutionContext, val psa: PredicateStateAnalysis) : MethodVisitor {
    override val cm: ClassManager get() = ctx.cm
    companion object {
        val fieldAccessMap: Map<Method, Set<Field>> get() = methodAccessMap

        private val methodAccessMap = hashMapOf<Method, Set<Field>>()
    }

    override fun cleanup() {}


    override fun visit(method: Method) {
        if (method.klass.isJavaRt) return

        val methodState = psa.builder(method).methodState ?: return
        val preparedState = prepareState(method, methodState)
        val fieldAccessList = collectFieldAccesses(ctx, preparedState)
        methodAccessMap[method] = fieldAccessList
    }


    private fun prepareState(method: Method, ps: PredicateState) = transform(ps) {
        +AnnotationAdapter(method, AnnotationManager.defaultLoader)
        +KexRtAdapter(ctx)
        +MethodInliner(psa)
    }
}
