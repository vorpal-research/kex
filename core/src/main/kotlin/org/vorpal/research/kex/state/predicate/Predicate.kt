package org.vorpal.research.kex.state.predicate

import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import org.vorpal.research.kex.BaseType
import org.vorpal.research.kex.InheritanceInfo
import org.vorpal.research.kex.state.InheritanceTypeInfo
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.state.transformer.Transformer
import org.vorpal.research.kfg.ir.Location
import org.vorpal.research.kthelper.assert.fail
import org.vorpal.research.kthelper.logging.log
import kotlin.random.Random

@Serializable
abstract class PredicateType {
    abstract val name: String

    @Serializable
    data class Path(@Required override val name: String = "P") : PredicateType() {
        override fun toString() = "@$name"
    }

    @Serializable
    data class State(@Required override val name: String = "S") : PredicateType() {
        override fun toString() = "@$name"
    }

    @Serializable
    data class Assume(@Required override val name: String = "A") : PredicateType() {
        override fun toString() = "@$name"
    }

    @Serializable
    data class Axiom(@Required override val name: String = "X") : PredicateType() {
        override fun toString() = "@$name"
    }

    @Serializable
    data class Require(@Required override val name: String = "R") : PredicateType() {
        override fun toString() = "@$name"
    }
}

@BaseType("Predicate")
@Serializable
abstract class Predicate : InheritanceTypeInfo {
    abstract val type: PredicateType
    abstract val location: Location
    abstract val operands: List<Term>

    val size: Int
        get() = operands.size

    companion object {
        val predicates = run {
            val loader = Thread.currentThread().contextClassLoader
            val resource = loader.getResourceAsStream("Predicate.json")
                    ?: fail { log.error("Could not load predicate inheritance info") }
            val inheritanceInfo = InheritanceInfo.fromJson(resource.bufferedReader().readText())
            resource.close()

            inheritanceInfo.inheritors.associate {
                it.name to loader.loadClass(it.inheritorClass)
            }
        }

        val reverse = predicates.map { it.value to it.key }.toMap()
    }

    abstract fun print(): String
    abstract fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate

    override fun hashCode() = 31 * type.hashCode() + operands.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Predicate
        return this.type == other.type && this.operands == other.operands
    }

    override val inheritors get() = predicates
    override val reverseMapping get() = reverse
    override fun toString() = "$type ${print()}"
}

val Predicate.hasReceiver
    get() = when (this) {
        is DefaultSwitchPredicate -> true
        is EqualityPredicate -> true
        is NewArrayPredicate -> true
        is InequalityPredicate -> true
        is NewPredicate -> true
        is ArrayInitializerPredicate -> true
        is GenerateArrayPredicate -> true
        is ArrayStorePredicate -> true
        is FieldInitializerPredicate -> true
        is FieldStorePredicate -> true
        is CallPredicate -> this.hasLhv
        else -> false
    }

val Predicate.receiver get() = if (hasReceiver) operands[0] else null

fun Predicate.inverse(random: Random): Predicate = when (this) {
    is EqualityPredicate -> when (rhv) {
        term { const(true) } -> predicate(type, location) { lhv equality false }
        term { const(false) } -> predicate(type, location) { lhv equality true }
        else -> predicate(type, location) { lhv inequality rhv }
    }
    is DefaultSwitchPredicate -> predicate(type, location) { cond equality cases.random(random) }
    is InequalityPredicate -> predicate(type, location) { lhv equality rhv }
    else -> this
}
