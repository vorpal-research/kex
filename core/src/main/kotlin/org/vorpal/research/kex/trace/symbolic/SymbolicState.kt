@file:Suppress("unused", "RedundantIf")

package org.vorpal.research.kex.trace.symbolic

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
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
sealed class ClauseList : Iterable<Clause> {
    abstract val state: List<Clause>
    override fun iterator(): Iterator<Clause> = state.iterator()
    fun asState() = BasicState(state.map { it.predicate })

    abstract fun subState(startInclusive: Int, endExclusive: Int): ClauseList

    abstract operator fun plus(other: ClauseList): ClauseList
    abstract operator fun plus(other: Clause): ClauseList
    override fun toString(): String {
        return "ClauseList(state=${state.joinToString(separator = "\n", prefix = "{\n", postfix = "\n}")})"
    }
}

@Serializable
data class ClauseListImpl(
    override val state: List<Clause> = emptyList()
) : ClauseList() {
    override fun subState(startInclusive: Int, endExclusive: Int): ClauseList =
        ClauseListImpl(state.subList(startInclusive, endExclusive))

    override fun plus(other: ClauseList): ClauseList = ClauseListImpl(state + other.state)
    override fun plus(other: Clause): ClauseList = ClauseListImpl(state + other)
}

@Serializable
data class PersistentClauseList(
    override val state: PersistentList<Clause> = persistentListOf()
) : ClauseList(), PersistentList<Clause> {
    override val size: Int
        get() = state.size

    override fun builder(): PersistentList.Builder<Clause> = state.builder()

    override fun add(element: Clause): PersistentClauseList = PersistentClauseList(state.add(element))
    override fun addAll(elements: Collection<Clause>): PersistentClauseList =
        PersistentClauseList(state.addAll(elements))

    override fun remove(element: Clause): PersistentClauseList = PersistentClauseList(state.remove(element))

    override fun removeAll(elements: Collection<Clause>): PersistentClauseList =
        PersistentClauseList(state.removeAll(elements))

    override fun removeAll(predicate: (Clause) -> Boolean): PersistentClauseList =
        PersistentClauseList(state.removeAll(predicate))

    override fun retainAll(elements: Collection<Clause>): PersistentClauseList =
        PersistentClauseList(state.removeAll(elements))

    override fun clear(): PersistentClauseList = PersistentClauseList(state.clear())
    override fun get(index: Int): Clause = state[index]

    override fun isEmpty(): Boolean = state.isEmpty()

    override fun indexOf(element: Clause): Int = state.indexOf(element)

    override fun containsAll(elements: Collection<Clause>): Boolean = state.containsAll(elements)

    override fun contains(element: Clause): Boolean = element in state

    override fun addAll(index: Int, c: Collection<Clause>): PersistentClauseList =
        PersistentClauseList(state.addAll(index, c))

    override fun set(index: Int, element: Clause): PersistentClauseList =
        PersistentClauseList(state.set(index, element))

    override fun add(index: Int, element: Clause): PersistentClauseList =
        PersistentClauseList(state.add(index, element))

    override fun removeAt(index: Int): PersistentClauseList = PersistentClauseList(state.removeAt(index))

    override fun iterator(): Iterator<Clause> = state.iterator()
    override fun listIterator(): ListIterator<Clause> = state.listIterator()

    override fun listIterator(index: Int): ListIterator<Clause> = state.listIterator(index)

    override fun lastIndexOf(element: Clause): Int = state.lastIndexOf(element)

    override fun subState(startInclusive: Int, endExclusive: Int): PersistentClauseList =
        PersistentClauseList(state.subList(startInclusive, endExclusive).toPersistentList())

    override fun subList(fromIndex: Int, toIndex: Int): PersistentList<Clause> =
        state.subList(fromIndex, toIndex).toPersistentList()

    override fun plus(other: ClauseList): PersistentClauseList = PersistentClauseList(state.addAll(other.state))
    override fun plus(other: Clause): PersistentClauseList = PersistentClauseList(state.add(other))
}

@Serializable
sealed class PathCondition : Iterable<PathClause> {
    abstract val path: List<PathClause>
    override fun iterator(): Iterator<PathClause> = path.iterator()
    abstract fun subPath(startInclusive: Int, endExclusive: Int): PathCondition
    fun asState() = BasicState(path.map { it.predicate })

    abstract operator fun plus(other: PathCondition): PathCondition
    abstract operator fun plus(other: PathClause): PathCondition
    override fun toString(): String {
        return "PathCondition(state=${path.joinToString(separator = "\n", prefix = "{\n", postfix = "\n}")})"
    }
}

@Serializable
data class PathConditionImpl(
    override val path: List<PathClause> = emptyList()
) : PathCondition() {
    override fun subPath(startInclusive: Int, endExclusive: Int): PathCondition =
        PathConditionImpl(path.subList(startInclusive, endExclusive))

    override fun plus(other: PathCondition): PathCondition = PathConditionImpl(path + other.path)
    override fun plus(other: PathClause): PathCondition = PathConditionImpl(path + other)
}

@Serializable
data class PersistentPathCondition(
    override val path: PersistentList<PathClause> = persistentListOf()
) : PathCondition(), PersistentList<PathClause> {
    override val size: Int
        get() = path.size

    override fun builder(): PersistentList.Builder<PathClause> = path.builder()

    override fun add(element: PathClause): PersistentPathCondition = PersistentPathCondition(path.add(element))
    override fun addAll(elements: Collection<PathClause>): PersistentPathCondition =
        PersistentPathCondition(path.addAll(elements))

    override fun remove(element: PathClause): PersistentPathCondition = PersistentPathCondition(path.remove(element))

    override fun removeAll(elements: Collection<PathClause>): PersistentPathCondition =
        PersistentPathCondition(path.removeAll(elements))

    override fun removeAll(predicate: (PathClause) -> Boolean): PersistentPathCondition =
        PersistentPathCondition(path.removeAll(predicate))

    override fun retainAll(elements: Collection<PathClause>): PersistentPathCondition =
        PersistentPathCondition(path.removeAll(elements))

    override fun clear(): PersistentPathCondition = PersistentPathCondition(path.clear())
    override fun get(index: Int): PathClause = path[index]

    override fun isEmpty(): Boolean = path.isEmpty()

    override fun indexOf(element: PathClause): Int = path.indexOf(element)

    override fun containsAll(elements: Collection<PathClause>): Boolean = path.containsAll(elements)

    override fun contains(element: PathClause): Boolean = element in path

    override fun addAll(index: Int, c: Collection<PathClause>): PersistentPathCondition =
        PersistentPathCondition(path.addAll(index, c))

    override fun set(index: Int, element: PathClause): PersistentPathCondition =
        PersistentPathCondition(path.set(index, element))

    override fun add(index: Int, element: PathClause): PersistentPathCondition =
        PersistentPathCondition(path.add(index, element))

    override fun removeAt(index: Int): PersistentPathCondition = PersistentPathCondition(path.removeAt(index))

    override fun iterator(): Iterator<PathClause> = path.iterator()
    override fun listIterator(): ListIterator<PathClause> = path.listIterator()

    override fun listIterator(index: Int): ListIterator<PathClause> = path.listIterator(index)

    override fun lastIndexOf(element: PathClause): Int = path.lastIndexOf(element)

    override fun subPath(startInclusive: Int, endExclusive: Int): PersistentPathCondition = PersistentPathCondition(
        subList(startInclusive, endExclusive).toPersistentList()
    )

    override fun subList(fromIndex: Int, toIndex: Int): PersistentList<PathClause> =
        path.subList(fromIndex, toIndex).toPersistentList()

    override fun plus(other: PathCondition): PersistentPathCondition = PersistentPathCondition(path.addAll(other.path))
    override fun plus(other: PathClause): PersistentPathCondition = PersistentPathCondition(path.add(other))
}

@Serializable
data class WrappedValue(val method: @Contextual Method, val value: @Contextual Value)

@Serializable
abstract class SymbolicState {
    abstract val clauses: ClauseList
    abstract val path: PathCondition
    abstract val concreteValueMap: Map<Term, @Contextual Descriptor>
    abstract val termMap: Map<Term, @Contextual WrappedValue>

    operator fun get(term: Term) = termMap.getValue(term)

    fun isEmpty() = clauses.state.isEmpty()
    fun isNotEmpty() = clauses.state.isNotEmpty()

    override fun toString() = clauses.joinToString("\n") { it.predicate.toString() }

    abstract operator fun plus(other: SymbolicState): SymbolicState
    abstract operator fun plus(other: StateClause): SymbolicState
    abstract operator fun plus(other: PathClause): SymbolicState
    abstract operator fun plus(other: ClauseList): SymbolicState
    abstract operator fun plus(other: PathCondition): SymbolicState
}

@Serializable
@SerialName("SymbolicStateImpl")
internal data class SymbolicStateImpl(
    override val clauses: ClauseList,
    override val path: PathCondition,
    override val concreteValueMap: @Contextual Map<Term, @Contextual Descriptor>,
    override val termMap: @Contextual Map<Term, @Contextual WrappedValue>,
) : SymbolicState() {
    override fun plus(other: SymbolicState): SymbolicState = SymbolicStateImpl(
        clauses = clauses + other.clauses,
        path = path + other.path,
        concreteValueMap = concreteValueMap + other.concreteValueMap,
        termMap = termMap + other.termMap
    )
    override fun plus(other: StateClause): SymbolicState = copy(
        clauses = clauses + other
    )
    override fun plus(other: PathClause): SymbolicState = copy(
        path = path + other,
    )
    override fun plus(other: ClauseList): SymbolicState = copy(
        clauses = clauses + other
    )
    override fun plus(other: PathCondition): SymbolicState = copy(
        path = path + other,
    )

    override fun toString(): String {
        return "SymbolicStateImpl(clauses=$clauses, path=$path)"
    }
}

@Serializable
@SerialName("PersistentSymbolicState")
data class PersistentSymbolicState(
    override val clauses: PersistentClauseList,
    override val path: PersistentPathCondition,
    override val concreteValueMap: @Contextual PersistentMap<Term, @Contextual Descriptor>,
    override val termMap: @Contextual PersistentMap<Term, @Contextual WrappedValue>,
) : SymbolicState() {
    override fun toString() = clauses.joinToString("\n") { it.predicate.toString() }

    override fun plus(other: SymbolicState): PersistentSymbolicState = PersistentSymbolicState(
        clauses = clauses + other.clauses,
        path = path + other.path,
        concreteValueMap = concreteValueMap.putAll(other.concreteValueMap),
        termMap = termMap.putAll(other.termMap)
    )
    override fun plus(other: StateClause): PersistentSymbolicState = copy(
        clauses = clauses + other
    )
    override fun plus(other: PathClause): PersistentSymbolicState = copy(
        path = path + other,
    )
    override fun plus(other: ClauseList): PersistentSymbolicState = copy(
        clauses = clauses + other
    )
    override fun plus(other: PathCondition): PersistentSymbolicState = copy(
        path = path + other,
    )
}


fun symbolicState(
    state: ClauseList = ClauseListImpl(),
    path: PathCondition = PathConditionImpl(),
    concreteValueMap: Map<Term, Descriptor> = emptyMap(),
    termMap: Map<Term, WrappedValue> = emptyMap(),
): SymbolicState = SymbolicStateImpl(
    state, path, concreteValueMap, termMap
)

fun persistentSymbolicState(
    state: PersistentClauseList = PersistentClauseList(),
    path: PersistentPathCondition = PersistentPathCondition(),
    concreteValueMap: PersistentMap<Term, Descriptor> = persistentMapOf(),
    termMap: PersistentMap<Term, WrappedValue> = persistentMapOf(),
): PersistentSymbolicState = PersistentSymbolicState(
    state, path, concreteValueMap, termMap
)

fun List<Clause>.toClauseState(): ClauseList = ClauseListImpl(this.toList())
fun List<Clause>.toPersistentClauseState(): PersistentClauseList = PersistentClauseList(this.toPersistentList())
fun PersistentList<Clause>.toClauseState(): PersistentClauseList = PersistentClauseList(this)

fun ClauseList.toPersistentClauseState(): PersistentClauseList = when (this) {
    is PersistentClauseList -> this
    else -> PersistentClauseList(this.toPersistentList())
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

fun persistentClauseStateOf(vararg clause: Clause) = PersistentClauseList(clause.toList().toPersistentList())
fun persistentPathConditionOf(vararg clause: PathClause) = PersistentPathCondition(clause.toList().toPersistentList())
