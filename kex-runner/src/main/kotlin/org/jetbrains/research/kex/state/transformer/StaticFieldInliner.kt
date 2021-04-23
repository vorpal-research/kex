package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.FieldStorePredicate
import org.jetbrains.research.kex.state.term.FieldLoadTerm
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kex.util.isFinal
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.collection.dequeOf
import java.util.*

val ignores by lazy {
    kexConfig.getMultipleStringValue("inliner", "static-ignore")
        .map { it.replace(".", "/") }
        .toSet()
}

class StaticFieldInliner(val cm: ClassManager, val psa: PredicateStateAnalysis) :
    RecollectingTransformer<StaticFieldInliner> {
    override val builders = dequeOf(StateBuilder())
    private var inlineIndex = 0

    fun prepareInlinedState(method: Method): PredicateState? {
        if (method.isEmpty()) return null

        val klass = method.`class`.fullname
        val builder = psa.builder(method)
        val endState = builder.methodState ?: return null

        val finalState = endState.filter {
            when (it) {
                is FieldStorePredicate -> {
                    val field = it.field as? FieldTerm ?: return@filter false
                    field.isStatic && field.klass == klass && !field.isFinal(cm)
                }
                else -> true
            }
        }

        return TermRenamer("static.inlined${inlineIndex++}", mapOf()).apply(finalState)
    }

    override fun apply(ps: PredicateState): PredicateState {
        val staticInitializers = TermCollector.getFullTermSet(ps)
            .filterIsInstance<FieldLoadTerm>()
            .mapNotNull {
                val field = it.field as FieldTerm
                val kfgField = cm[field.klass].getField(field.fieldNameString, field.type.getKfgType(cm.type))
                if (kfgField.isStatic && kfgField.isFinal) {
                    kfgField.`class`
                } else {
                    null
                }
            }.toSet()
            .filterNot { it.fullname in ignores }
            .toSet()
        staticInitializers.forEach { klass ->
            prepareInlinedState(klass.getMethods("<clinit>").first())?.let { state ->
                currentBuilder += state
            }
        }
        return super.apply(ps)
    }
}