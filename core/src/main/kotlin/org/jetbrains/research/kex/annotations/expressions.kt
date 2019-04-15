package org.jetbrains.research.kex.annotations

import org.jetbrains.research.kex.ktype.KexBool
import org.jetbrains.research.kex.ktype.KexNull
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateFactory
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermFactory
import org.jetbrains.research.kex.state.term.ValueTerm
import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class ExpressionBuilder : TermExpression() {
    val predicates = mutableListOf<Predicate>()

    inline val pf get() = PredicateFactory

    inline fun assume(lambda: PredicateExpression.() -> Predicate): Predicate {
        val predicate = PredicateExpression.Assume.lambda()
        predicates += predicate
        return predicate
    }
    inline fun state(lambda: PredicateExpression.() -> Predicate): Predicate {
        val predicate = PredicateExpression.State.lambda()
        predicates += predicate
        return predicate
    }
    inline fun path(lambda: PredicateExpression.() -> Predicate): Predicate {
        val predicate = PredicateExpression.Path.lambda()
        predicates += predicate
        return predicate
    }

    inline fun assumeTrue(lambda: TermExpression.() -> Term): Predicate {
        val term = TermExpression.lambda()
        val predicate = pf.getEquality(term, tf.getTrue(), PredicateType.Assume())
        predicates += predicate
        return predicate
    }
    inline fun assumeFalse(lambda: TermExpression.() -> Term): Predicate {
        val term = TermExpression.lambda()
        val predicate = pf.getEquality(term, tf.getFalse(), PredicateType.Assume())
        predicates += predicate
        return predicate
    }
    infix fun Term.load(term: Term): Predicate {
        val predicate = pf.getEquality(this, term, PredicateType.State())
        predicates += predicate
        return predicate
    }
    fun value(type: KexType, name: String) = tf.getValue(type, name)
    fun value(type: KClass<*>, name: String): ValueTerm = when (type) {
        KexBool::class, Boolean::class -> tf.getValue(KexBool, name)
        KexNull::class, Nothing::class -> tf.getValue(KexNull, name)
        else -> throw IllegalStateException("Unsupported value term type")
    }
    inline fun <reified T> value(name: String) = value(T::class, name)

    operator fun Term.setValue(thisRef: Any?, property: KProperty<*>, value: Term) {
        predicates += pf.getLoad(this, value)
    }

    operator fun Term.getValue(thisRef: Any?, property: KProperty<*>): Term = this

    companion object {
        inline fun build(lambda: ExpressionBuilder.() -> Unit) = ExpressionBuilder().apply { lambda() }.apply()
    }

    fun apply(): List<Predicate> = predicates
}

sealed class TermExpression {
    inline val tf get() = TermFactory

    infix fun Term.and(other: Term) = tf.getBinary(type, BinaryOpcode.And(), this, other)
    infix fun Term.or(other: Term) = tf.getBinary(type, BinaryOpcode.Or(), this, other)

    infix fun Term.ge(other: Term) = tf.getCmp(CmpOpcode.Ge(), this, other)
    infix fun Term.ge(other: Number) = this ge tf.getConstant(other)
    infix fun Number.ge(other: Term) = tf.getConstant(this) ge other

    infix fun Term.le(other: Term) = tf.getCmp(CmpOpcode.Le(), this, other)
    infix fun Term.le(other: Number) = this ge tf.getConstant(other)
    infix fun Number.le(other: Term) = tf.getConstant(this) ge other

    infix fun Term.eq(other: Term) = tf.getCmp(CmpOpcode.Eq(), this, other)
    infix fun Term.neq(other: Term) = tf.getCmp(CmpOpcode.Neq(), this, other)

    fun not(term: Term) = term neq const(true)

    operator fun Term.unaryMinus() = tf.getNegTerm(this)
    operator fun Term.unaryPlus() = this
    operator fun Term.minus(other: Term) = tf.getBinary(type, BinaryOpcode.Sub(), this, other)
    operator fun Term.plus(other: Term) = tf.getBinary(type, BinaryOpcode.Add(), this, other)

    operator fun Term.get(index: Term) = tf.getArrayIndex(this, index)

    fun const(@Suppress("UNUSED_PARAMETER") value: Nothing?) = tf.getNull()
    fun const(value: Number) = tf.getConstant(value)
    fun const(value: Boolean) = tf.getBool(value)

    infix fun <T> ClosedRange<T>.has(value: Term): Term where  T: Number, T: Comparable<T> =
            (value ge start) and (value le endInclusive)

    companion object : TermExpression()
}

sealed class PredicateExpression : TermExpression() {
    abstract val type: PredicateType

    inline val pf get() = PredicateFactory

    infix fun Term.equality(other: Term) =
            pf.getEquality(this, other, this@PredicateExpression.type)
    infix fun Term.equality(@Suppress("UNUSED_PARAMETER") other: Nothing?) =
            pf.getEquality(this, tf.getNull(), this@PredicateExpression.type)
    infix fun Nothing?.equality(other: Term) =
            pf.getEquality(tf.getNull(), other, this@PredicateExpression.type)
    infix fun Term.equality(other: Boolean) =
            pf.getEquality(this, tf.getBool(other), this@PredicateExpression.type)
    infix fun Boolean.equality(other: Term) =
            pf.getEquality(tf.getBool(this), other, this@PredicateExpression.type)

    infix fun Term.inequality(other: Term) =
            pf.getInequality(this, other, this@PredicateExpression.type)
    infix fun Term.inequality(@Suppress("UNUSED_PARAMETER") other: Nothing?) =
            pf.getInequality(this, tf.getNull(), this@PredicateExpression.type)
    infix fun Nothing?.inequality(other: Term) =
            pf.getInequality(tf.getNull(), other, this@PredicateExpression.type)
    infix fun Term.inequality(other: Boolean) =
            pf.getInequality(this, tf.getBool(other), this@PredicateExpression.type)
    infix fun Boolean.inequality(other: Term) =
            pf.getInequality(tf.getBool(this), other, this@PredicateExpression.type)

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