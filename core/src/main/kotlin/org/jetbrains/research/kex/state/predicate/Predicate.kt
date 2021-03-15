package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kthelper.assert.fail
import org.jetbrains.research.kthelper.defaultHashCode
import org.jetbrains.research.kthelper.logging.log
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.BaseType
import org.jetbrains.research.kex.InheritanceInfo
import org.jetbrains.research.kex.state.TypeInfo
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Location

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
abstract class Predicate : TypeInfo {
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

            inheritanceInfo?.inheritors?.map {
                it.name to loader.loadClass(it.inheritorClass)
            }?.toMap() ?: mapOf()
        }

        val reverse = predicates.map { it.value to it.key }.toMap()
    }

    abstract fun print(): String
    abstract fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate

    override fun hashCode() = defaultHashCode(type, *operands.toTypedArray())
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
        is ArrayStorePredicate -> true
        is FieldInitializerPredicate -> true
        is FieldStorePredicate -> true
        is CallPredicate -> this.hasLhv
        else -> false
    }

val Predicate.receiver get() = if (hasReceiver) operands[0] else null

fun Predicate.inverse(): Predicate = when (this) {
    is EqualityPredicate -> when (rhv) {
        term { const(true) } -> predicate(type, location) { lhv equality false }
        term { const(false) } -> predicate(type, location) { lhv equality true }
        else -> predicate(type, location) { lhv inequality rhv }
    }
    is DefaultSwitchPredicate -> predicate(type, location) { cond equality cases.random() }
    is InequalityPredicate -> predicate(type, location) { lhv equality rhv }
    else -> this
}