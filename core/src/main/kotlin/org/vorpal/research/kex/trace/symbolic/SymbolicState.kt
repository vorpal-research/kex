package org.vorpal.research.kex.trace.symbolic

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.state.BasicState
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.instruction.Instruction

enum class PathClauseType {
    NULL_CHECK,
    TYPE_CHECK,
    OVERLOAD_CHECK,
    CONDITION_CHECK,
    BOUNDS_CHECK
}

@Serializable
sealed class Clause {
    @Contextual
    abstract val instruction: Instruction
    abstract val predicate: Predicate
}

@Serializable
data class StateClause(
    @Contextual
    override val instruction: Instruction,
    override val predicate: Predicate
) : Clause() {
    private var hashCode: Int = 0
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StateClause

        if (instruction != other.instruction) return false
        if (predicate != other.predicate) return false

        return true
    }

    override fun hashCode(): Int {
        if (hashCode == 0) {
            hashCode = 31 * instruction.hashCode() + predicate.hashCode()
        }
        return hashCode
    }

}

@Serializable
data class PathClause(
    val type: PathClauseType,
    @Contextual
    override val instruction: Instruction,
    override val predicate: Predicate
) : Clause() {
    private var hashCode: Int = 0
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PathClause

        if (type != other.type) return false
        if (instruction != other.instruction) return false
        if (predicate != other.predicate) return false

        return true
    }

    override fun hashCode(): Int {
        if (hashCode == 0) {
            hashCode = 31 * (type.hashCode() + 31 * instruction.hashCode()) + predicate.hashCode()
        }
        return hashCode
    }
}

@Serializable
data class ClauseState(val state: List<Clause> = emptyList()) : List<Clause> by state {
    override fun iterator() = state.iterator()
    fun asState() = BasicState(state.map { it.predicate })
}

@Serializable
data class PathCondition(val path: List<PathClause> = emptyList()) : List<PathClause> by path {
    override fun iterator() = path.iterator()

    fun subPath(clause: PathClause) = path.subList(0, path.indexOf(clause) + 1)

    fun asState() = BasicState(path.map { it.predicate })
}

@Serializable
data class WrappedValue(val method: @Contextual Method, val value: @Contextual Value)

@Serializable
abstract class SymbolicState {
    abstract val clauses: ClauseState
    abstract val path: PathCondition
    abstract val concreteValueMap: Map<Term, @Contextual Descriptor>
    abstract val termMap: Map<Term, @Contextual WrappedValue>

    operator fun get(term: Term) = termMap.getValue(term)

    fun subPath(clause: PathClause): List<PathClause> = path.subList(0, path.indexOf(clause) + 1)

    fun isEmpty() = clauses.isEmpty()
    fun isNotEmpty() = clauses.isNotEmpty()
}

@Serializable
@SerialName("SymbolicStateImpl")
data class SymbolicStateImpl(
    override val clauses: ClauseState,
    override val path: PathCondition,
    override val concreteValueMap: @Contextual Map<Term, @Contextual Descriptor>,
    override val termMap: @Contextual Map<Term, @Contextual WrappedValue>,
) : SymbolicState() {
    override fun toString() = "${clauses.asState()}"
}

fun symbolicState(
    state: ClauseState = ClauseState(emptyList()),
    path: PathCondition = PathCondition(emptyList()),
    concreteValueMap: Map<Term, Descriptor> = emptyMap(),
    termMap: Map<Term, WrappedValue> = emptyMap(),
) = SymbolicStateImpl(
    state, path, concreteValueMap, termMap
)
