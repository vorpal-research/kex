package org.jetbrains.research.kex.state.transformer

import com.abdullin.kthelper.collection.dequeOf
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.FieldStorePredicate
import org.jetbrains.research.kex.state.term.FieldLoadTerm
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Method
import java.util.*

class StaticFieldInliner(val cm: ClassManager, val psa: PredicateStateAnalysis) : RecollectingTransformer<StaticFieldInliner> {
    override val builders = dequeOf(StateBuilder())
    private var inlineIndex = 0

    private val FieldTerm.isFinal get(): Boolean {
        val kfgField = cm[this.klass].getField(this.fieldNameString, this.type.getKfgType(cm.type))
        return kfgField.isFinal
    }

    fun prepareInlinedState(method: Method): PredicateState? {
        if (method.isEmpty()) return null

        val klass = method.`class`.fullname
        val builder = psa.builder(method)
        val endState = builder.methodState ?: return null

        endState.filter {
            when (it) {
                is FieldStorePredicate -> {
                    val field = it.field as? FieldTerm ?: return@filter false
                    field.isStatic && field.klass == klass && !field.isFinal
                }
                else -> true
            }
        }

        return TermRenamer("static.inlined${inlineIndex++}", mapOf()).apply(endState)
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
        staticInitializers.forEach { klass ->
            prepareInlinedState(klass.getMethods("<clinit>").first())?.let { state ->
                currentBuilder += state
            }
        }
        return super.apply(ps)
    }
}