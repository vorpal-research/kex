package org.jetbrains.research.kex.annotations

import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateFactory
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermFactory
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode

sealed class PredicateExpression {
    abstract val type: PredicateType

    val pf get() = PredicateFactory
    val tf get() = TermFactory

    infix fun Term.eq(other: Term): List<Predicate> =
            AnnotationInfo.single(pf.getEquality(this, other, this@PredicateExpression.type))
    infix fun Term.eq(@Suppress("UNUSED_PARAMETER") other: Nothing?): List<Predicate> =
            AnnotationInfo.single(pf.getEquality(this, tf.getNull(), this@PredicateExpression.type))
    infix fun Nothing?.eq(other: Term): List<Predicate> =
            AnnotationInfo.single(pf.getEquality(tf.getNull(), other, this@PredicateExpression.type))
    infix fun Boolean.eq(other: Boolean): List<Predicate> =
            AnnotationInfo.single(pf.getEquality(tf.getBool(this), tf.getBool(other),
                    this@PredicateExpression.type))

    infix fun Term.neq(other: Term): List<Predicate> =
            AnnotationInfo.single(pf.getInequality(this, other, this@PredicateExpression.type))
    infix fun Term.neq(@Suppress("UNUSED_PARAMETER") other: Nothing?): List<Predicate> =
            AnnotationInfo.single(pf.getInequality(this, tf.getNull(), this@PredicateExpression.type))
    infix fun Nothing?.neq(other: Term): List<Predicate> =
            AnnotationInfo.single(pf.getInequality(tf.getNull(), other, this@PredicateExpression.type))

    infix fun <T> ClosedRange<T>.has(value: Term): List<Predicate> where  T: Number, T: Comparable<T> = listOf(
            pf.getEquality(tf.getCmp(CmpOpcode.Ge(), value, tf.getConstant(start)),
                    tf.getTrue(), this@PredicateExpression.type),
            pf.getEquality(tf.getCmp(CmpOpcode.Le(), value, tf.getConstant(endInclusive)),
                    tf.getTrue(), this@PredicateExpression.type)
    )

    object Assume : PredicateExpression() {
        override val type get() = PredicateType.Assume()
    }
    object State : PredicateExpression() {
        override val type get() = PredicateType.State()
    }
    object Path : PredicateExpression() {
        override val type get() = PredicateType.Path()
    }
}