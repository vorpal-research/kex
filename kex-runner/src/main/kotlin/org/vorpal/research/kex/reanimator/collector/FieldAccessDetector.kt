package org.vorpal.research.kex.reanimator.collector

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.annotations.AnnotationManager
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.ktype.KexRtManager.isJavaRt
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.transformer.AnnotationAdapter
import org.vorpal.research.kex.state.transformer.ClassAdapter
import org.vorpal.research.kex.state.transformer.ClassMethodAdapter
import org.vorpal.research.kex.state.transformer.ConstEnumAdapter
import org.vorpal.research.kex.state.transformer.ConstStringAdapter
import org.vorpal.research.kex.state.transformer.KexRtAdapter
import org.vorpal.research.kex.state.transformer.MethodInliner
import org.vorpal.research.kex.state.transformer.StringMethodAdapter
import org.vorpal.research.kex.state.transformer.TypeNameAdapter
import org.vorpal.research.kex.state.transformer.collectFieldAccesses
import org.vorpal.research.kex.state.transformer.transform
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Field
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kthelper.tryOrNull

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

        tryOrNull {
            val methodState = psa.builder(method).methodState ?: return
            val preparedState = prepareState(method, methodState)
            val fieldAccessList = collectFieldAccesses(ctx, preparedState)
            methodAccessMap[method] = fieldAccessList
        }
    }


    private fun prepareState(method: Method, ps: PredicateState) = transform(ps) {
        +StringMethodAdapter(ctx.cm)
        +AnnotationAdapter(method, AnnotationManager.defaultLoader)
        +KexRtAdapter(ctx.cm)
        +MethodInliner(psa)
        +ClassAdapter(cm)
        +ClassMethodAdapter(cm)
        +ConstEnumAdapter(ctx)
        +ConstStringAdapter(cm.type)
        +TypeNameAdapter(ctx)
    }
}
