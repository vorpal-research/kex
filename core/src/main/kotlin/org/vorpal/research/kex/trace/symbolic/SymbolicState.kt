package org.vorpal.research.kex.trace.symbolic

import kotlinx.collections.immutable.*
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
sealed class ClauseState : Iterable<Clause> {
    abstract val state: List<Clause>
    override fun iterator(): Iterator<Clause> = state.iterator()
    fun asState() = BasicState(state.map { it.predicate })

    abstract fun subState(startInclusive: Int, endExclusive: Int): ClauseState
}

@Serializable
data class ClauseStateImpl(
    override val state: List<Clause> = emptyList()
) : ClauseState() {
    override fun subState(startInclusive: Int, endExclusive: Int): ClauseState =
        ClauseStateImpl(state.subList(startInclusive, endExclusive))
}

@Serializable
data class PersistentClauseState(
    override val state: PersistentList<Clause> = persistentListOf()
) : ClauseState(), PersistentList<Clause> by state {
    override fun subState(startInclusive: Int, endExclusive: Int): PersistentClauseState =
        PersistentClauseState(state.subList(startInclusive, endExclusive).toPersistentList())

    override fun subList(fromIndex: Int, toIndex: Int): PersistentList<Clause> =
        state.subList(fromIndex, toIndex).toPersistentList()

    override fun iterator(): Iterator<Clause> = state.iterator()
}

@Serializable
sealed class PathCondition : Iterable<PathClause> {
    abstract val path: List<PathClause>
    override fun iterator(): Iterator<PathClause> = path.iterator()
    abstract fun subPath(startInclusive: Int, endExclusive: Int): PathCondition
    fun asState() = BasicState(path.map { it.predicate })
}

@Serializable
data class PathConditionImpl(
    override val path: List<PathClause> = emptyList()
) : PathCondition() {
    override fun subPath(startInclusive: Int, endExclusive: Int): PathCondition =
        PathConditionImpl(path.subList(startInclusive, endExclusive))
}

@Serializable
data class PersistentPathCondition(
    override val path: PersistentList<PathClause> = persistentListOf()
) : PathCondition(), PersistentList<PathClause> by path {
    override fun subPath(startInclusive: Int, endExclusive: Int): PersistentPathCondition = PersistentPathCondition(
        subList(startInclusive, endExclusive).toPersistentList()
    )

    override fun subList(fromIndex: Int, toIndex: Int): PersistentList<PathClause> =
        path.subList(fromIndex, toIndex).toPersistentList()

    override fun iterator(): Iterator<PathClause> = path.iterator()
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

    fun isEmpty() = clauses.state.isEmpty()
    fun isNotEmpty() = clauses.state.isEmpty()

    override fun toString() = clauses.joinToString("\n") { it.predicate.toString() }
}

@Serializable
@SerialName("SymbolicStateImpl")
data class SymbolicStateImpl(
    override val clauses: ClauseState,
    override val path: PathCondition,
    override val concreteValueMap: @Contextual Map<Term, @Contextual Descriptor>,
    override val termMap: @Contextual Map<Term, @Contextual WrappedValue>,
) : SymbolicState()

@Serializable
@SerialName("PersistentSymbolicState")
data class PersistentSymbolicState(
    override val clauses: PersistentClauseState,
    override val path: PersistentPathCondition,
    override val concreteValueMap: @Contextual PersistentMap<Term, @Contextual Descriptor>,
    override val termMap: @Contextual PersistentMap<Term, @Contextual WrappedValue>,
) : SymbolicState() {
    override fun toString() = clauses.joinToString("\n") { it.predicate.toString() }
}


fun symbolicState(
    state: ClauseState = ClauseStateImpl(),
    path: PathCondition = PathConditionImpl(),
    concreteValueMap: Map<Term, Descriptor> = emptyMap(),
    termMap: Map<Term, WrappedValue> = emptyMap(),
): SymbolicState = SymbolicStateImpl(
    state, path, concreteValueMap, termMap
)

fun persistentSymbolicState(
    state: PersistentClauseState = PersistentClauseState(),
    path: PersistentPathCondition = PersistentPathCondition(),
    concreteValueMap: PersistentMap<Term, Descriptor> = persistentMapOf(),
    termMap: PersistentMap<Term, WrappedValue> = persistentMapOf(),
): PersistentSymbolicState = PersistentSymbolicState(
    state, path, concreteValueMap, termMap
)

operator fun PersistentPathCondition.plus(clause: PathClause) = PersistentPathCondition(
    path.add(clause)
)

operator fun PersistentClauseState.plus(clause: Clause) = PersistentClauseState(
    state.add(clause)
)

fun List<Clause>.toClauseState(): ClauseState = ClauseStateImpl(this.toList())
fun List<Clause>.toPersistentClauseState(): PersistentClauseState = PersistentClauseState(this.toPersistentList())
fun PersistentList<Clause>.toClauseState(): PersistentClauseState = PersistentClauseState(this)

fun ClauseState.toPersistentClauseState(): PersistentClauseState = when (this) {
    is PersistentClauseState -> this
    else -> PersistentClauseState(this.toPersistentList())
}


fun List<PathClause>.toPathCondition(): PathCondition = PathConditionImpl(this.toList())
fun List<PathClause>.toPersistentPathCondition(): PersistentPathCondition =
    PersistentPathCondition(this.toPersistentList())

fun PersistentList<PathClause>.toPathCondition(): PersistentPathCondition = PersistentPathCondition(this)


fun PathCondition.toPersistentPathCondition(): PersistentPathCondition = when (this) {
    is PersistentPathCondition -> this
    else -> PersistentPathCondition(this.toPersistentList())
}

fun SymbolicState.toPersistentState(): PersistentSymbolicState = when (this) {
    is PersistentSymbolicState -> this
    else -> PersistentSymbolicState(
        this.clauses.toPersistentClauseState(),
        this.path.toPersistentPathCondition(),
        this.concreteValueMap.toPersistentMap(),
        this.termMap.toPersistentMap()
    )
}
