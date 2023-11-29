package org.vorpal.research.kex.asm.analysis.symbolic

import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.persistentSymbolicState

data class ParametersDescription(
    val initialState: Parameters<Descriptor>?,
    val finalState: Parameters<Descriptor>?,
    val memoryMap: Map<Term, Descriptor>? = null
)

internal typealias UpdateAction = suspend (TraverserState) -> TraverserState
internal typealias ReportAction = suspend (TraverserState, ParametersDescription) -> Unit

sealed class SingleQuery {
    abstract val query: PersistentSymbolicState
    abstract operator fun plus(other: PersistentSymbolicState): SingleQuery
}

data class ReportQuery(
    override val query: PersistentSymbolicState,
    val action: ReportAction
) : SingleQuery() {

    override operator fun plus(other: PersistentSymbolicState): ReportQuery = this.copy(
        query = query + other
    )
}

sealed class UpdateQuery : SingleQuery() {
    abstract val action: UpdateAction

    abstract operator fun plus(other: UpdateQuery): UpdateQuery
    abstract operator fun plus(newAction: UpdateAction): UpdateQuery
    abstract operator fun plus(newAction: ReportAction): UpdateQuery
}

data class UpdateOnlyQuery(
    override val query: PersistentSymbolicState,
    override val action: UpdateAction
) : UpdateQuery() {
    override operator fun plus(other: UpdateQuery): UpdateQuery = when (other) {
        is UpdateOnlyQuery -> UpdateOnlyQuery(this.query + other.query) { state ->
            other.action(this.action(state))
        }

        is UpdateAndReportQuery -> UpdateAndReportQuery(
            this.query + other.query,
            { state -> other.action(this.action(state)) },
            other.reportAction
        )
    }

    override operator fun plus(other: PersistentSymbolicState): UpdateOnlyQuery = this.copy(
        query = query + other
    )


    override operator fun plus(newAction: UpdateAction): UpdateQuery = UpdateOnlyQuery(query) { state ->
        newAction(action(state))
    }

    override fun plus(newAction: ReportAction): UpdateQuery = UpdateAndReportQuery(query, action, newAction)
}

data class UpdateAndReportQuery(
    override val query: PersistentSymbolicState,
    override val action: UpdateAction,
    val reportAction: ReportAction
) : UpdateQuery() {

    override operator fun plus(other: PersistentSymbolicState): UpdateAndReportQuery = this.copy(
        query = query + other
    )
    override operator fun plus(other: UpdateQuery): UpdateQuery = when (other) {
        is UpdateOnlyQuery -> UpdateAndReportQuery(
            this.query + other.query,
            { state -> other.action(this.action(state)) },
            reportAction
        )

        is UpdateAndReportQuery -> UpdateAndReportQuery(
            this.query + other.query,
            { state -> other.action(this.action(state)) },
            { state, parameters ->
                this.reportAction(state, parameters)
                other.reportAction(state, parameters)
            }
        )
    }

    override operator fun plus(newAction: UpdateAction): UpdateQuery =
        UpdateAndReportQuery(query, { state -> newAction(action(state)) }, reportAction)

    override fun plus(newAction: ReportAction): UpdateQuery =
        UpdateAndReportQuery(query, action) { state, parameters ->
            this.reportAction(state, parameters)
            newAction(state, parameters)
        }
}

sealed class IncrementalQuery {
    abstract fun withHandler(newAction: UpdateAction): IncrementalQuery
    abstract fun withHandler(newAction: ReportAction): IncrementalQuery
}

sealed class OptionalErrorCheckQuery : IncrementalQuery() {
    abstract val normalQuery: PersistentSymbolicState

    abstract operator fun plus(other: OptionalErrorCheckQuery): OptionalErrorCheckQuery

    abstract fun addExtraCondition(query: PersistentSymbolicState): OptionalErrorCheckQuery
}

data class EmptyQuery(
    val action: UpdateAction = { it }
) : OptionalErrorCheckQuery() {
    override val normalQuery = persistentSymbolicState()

    override fun plus(other: OptionalErrorCheckQuery): OptionalErrorCheckQuery =
        other.withHandler(action) as OptionalErrorCheckQuery
    override fun withHandler(newAction: UpdateAction): EmptyQuery =
        EmptyQuery { state ->
            newAction(action(state))
        }

    override fun withHandler(newAction: ReportAction): IncrementalQuery =
        ConditionCheckQuery(UpdateAndReportQuery(persistentSymbolicState(), action, newAction))


    override fun addExtraCondition(query: PersistentSymbolicState): OptionalErrorCheckQuery = this
}

data class ExceptionCheckQuery(
    val noErrorQuery: UpdateQuery,
    val errorQueries: List<SingleQuery>
) : OptionalErrorCheckQuery() {
    override val normalQuery = noErrorQuery.query
    constructor(vararg queries: SingleQuery) : this(queries[0] as UpdateQuery, queries.drop(1).toList())

    override fun plus(other: OptionalErrorCheckQuery): OptionalErrorCheckQuery = when (other) {
        is EmptyQuery -> this.withHandler(other.action)
        is ExceptionCheckQuery -> ExceptionCheckQuery(
            this.noErrorQuery + other.noErrorQuery,
            this.errorQueries + other.errorQueries
        )
    }

    override fun withHandler(newAction: UpdateAction): ExceptionCheckQuery =
        ExceptionCheckQuery(noErrorQuery + newAction, errorQueries)
    override fun withHandler(newAction: ReportAction): IncrementalQuery =
        ExceptionCheckQuery(noErrorQuery + newAction, errorQueries)

    override fun addExtraCondition(query: PersistentSymbolicState): ExceptionCheckQuery = this.copy(
        noErrorQuery = (noErrorQuery + query) as UpdateQuery,
        errorQueries = errorQueries.map { it + query }
    )
}

data class ConditionCheckQuery(
    val queries: List<UpdateQuery>
) : IncrementalQuery() {
    constructor(vararg queries: UpdateQuery) : this(queries.toList())

    override fun withHandler(newAction: UpdateAction): ConditionCheckQuery =
        ConditionCheckQuery(queries.map { it + newAction })
    override fun withHandler(newAction: ReportAction): IncrementalQuery =
        ConditionCheckQuery(queries.map { it + newAction })
}
