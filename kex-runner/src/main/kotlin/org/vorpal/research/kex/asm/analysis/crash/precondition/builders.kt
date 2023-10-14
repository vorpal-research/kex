package org.vorpal.research.kex.asm.analysis.crash.precondition

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.symbolic.TraverserState
import org.vorpal.research.kex.descriptor.*
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.state.predicate.path
import org.vorpal.research.kex.state.predicate.state
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.state.transformer.SymbolicStateTermRemapper
import org.vorpal.research.kex.trace.symbolic.*
import org.vorpal.research.kex.util.asSet
import org.vorpal.research.kfg.arrayIndexOOBClass
import org.vorpal.research.kfg.classCastClass
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.value.instruction.*
import org.vorpal.research.kfg.negativeArrayClass
import org.vorpal.research.kfg.nullptrClass
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

class ExceptionPreconditionBuilderImpl<T>(
    val ctx: ExecutionContext,
    override val targetException: Class,
) : ExceptionPreconditionBuilder<T> {
    val cm get() = ctx.cm
    private val preconditionManager = ExceptionPreconditionManager<T>(ctx)

    override fun addPrecondition(precondition: T) = false

    override fun build(location: Instruction, state: TraverserState): Set<PersistentSymbolicState> =
        when (targetException) {
            cm.nullptrClass -> persistentSymbolicState(
                path = when (location) {
                    is ArrayLoadInst -> persistentPathConditionOf(
                        PathClause(PathClauseType.NULL_CHECK, location, path {
                            (state.mkTerm(location.arrayRef) eq null) equality true
                        })
                    )

                    is ArrayStoreInst -> persistentPathConditionOf(
                        PathClause(PathClauseType.NULL_CHECK, location, path {
                            (state.mkTerm(location.arrayRef) eq null) equality true
                        })
                    )

                    is FieldLoadInst -> when {
                        location.isStatic -> persistentPathConditionOf()
                        else -> persistentPathConditionOf(
                            PathClause(PathClauseType.NULL_CHECK, location, path {
                                (state.mkTerm(location.owner) eq null) equality true
                            })
                        )
                    }

                    is FieldStoreInst -> when {
                        location.isStatic -> persistentPathConditionOf()
                        else -> persistentPathConditionOf(
                            PathClause(PathClauseType.NULL_CHECK, location, path {
                                (state.mkTerm(location.owner) eq null) equality true
                            })
                        )
                    }

                    is CallInst -> when {
                        location.isStatic -> persistentPathConditionOf()
                        else -> persistentPathConditionOf(
                            PathClause(PathClauseType.NULL_CHECK, location, path {
                                (state.mkTerm(location.callee) eq null) equality true
                            })
                        )
                    }

                    else -> unreachable { log.error("Instruction ${location.print()} does not throw null pointer") }
                }
            ).asSet()

            cm.arrayIndexOOBClass -> {
                val (arrayTerm, indexTerm) = when (location) {
                    is ArrayLoadInst -> state.mkTerm(location.arrayRef) to state.mkTerm(location.index)
                    is ArrayStoreInst -> state.mkTerm(location.arrayRef) to state.mkTerm(location.index)
                    else -> unreachable { log.error("Instruction ${location.print()} does not throw array index out of bounds") }
                }
                setOf(
                    persistentSymbolicState(
                        path = persistentPathConditionOf(
                            PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                (arrayTerm eq null) equality false
                            }),
                            PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                (indexTerm ge 0) equality false
                            })
                        )
                    ),
                    persistentSymbolicState(
                        path = persistentPathConditionOf(
                            PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                (arrayTerm eq null) equality false
                            }),
                            PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                (indexTerm lt arrayTerm.length()) equality false
                            }),
                        )
                    )
                )
            }

            cm.negativeArrayClass -> when (location) {
                is NewArrayInst -> location.dimensions.mapTo(mutableSetOf()) { length ->
                    persistentSymbolicState(
                        path = persistentPathConditionOf(
                            PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                (state.mkTerm(length) ge 0) equality false
                            }),
                        )
                    )
                }

                else -> unreachable { log.error("Instruction ${location.print()} does not throw negative array size") }
            }

            cm.classCastClass -> when (location) {
                is CastInst -> persistentSymbolicState(
                    path = persistentPathConditionOf(
                        PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                            (state.mkTerm(location.operand) `is` location.type.kexType) equality false
                        }),
                    )
                ).asSet()

                else -> unreachable { log.error("Instruction ${location.print()} does not throw class cast") }
            }

            else -> when (location) {
                is ThrowInst -> when {
                    targetException.asType.isSubtypeOf(location.throwable.type) -> persistentSymbolicState().asSet()
                    else -> emptySet()
                }

                is NewInst -> when (location.type) {
                    targetException.asType -> persistentSymbolicState().asSet()
                    else -> emptySet()
                }

                is CallInst -> preconditionManager.resolve(location, targetException)
                    ?.build(location, state)
                    ?: emptySet<PersistentSymbolicState>().also {
                        log.warn("Could not resolve exception type $targetException in ${location.print()}")
                    }

                else -> unreachable { log.error("Instruction ${location.print()} does not throw target exception") }
            }
        }
}

class DescriptorExceptionPreconditionBuilder(
    val ctx: ExecutionContext,
    override val targetException: Class,
    parameterSet: Set<Parameters<Descriptor>>,
) : ExceptionPreconditionBuilder<Parameters<Descriptor>> {
    private val parameterSet = parameterSet.toMutableSet()

    override fun addPrecondition(precondition: Parameters<Descriptor>): Boolean {
        return parameterSet.add(precondition)
    }

    override fun build(location: Instruction, state: TraverserState): Set<PersistentSymbolicState> {
        val callInst = (location as? CallInst)
            ?: unreachable { log.error("Descriptor precondition is not valid for non-call instructions") }

        return parameterSet.mapTo(mutableSetOf()) { parameters ->
            var result = persistentSymbolicState()

            val mapping = buildMap {
                if (!callInst.isStatic)
                    this[parameters.instance!!.term] = state.mkTerm(callInst.callee)
                for ((argValue, argDescriptor) in callInst.args.zip(parameters.arguments)) {
                    this[argDescriptor.term] = state.mkTerm(argValue)
                }
            }.toMutableMap()
            for (descriptor in parameters.asList) {
                result += descriptor.asSymbolicState(callInst, mapping)
            }
            result
        }
    }

    private fun Descriptor.asSymbolicState(
        location: Instruction,
        mapping: MutableMap<Term, Term>,
        visited: MutableSet<Descriptor> = mutableSetOf()
    ): PersistentSymbolicState = when (this) {
        in visited -> persistentSymbolicState()
        is ConstantDescriptor -> this.asSymbolicState(location, mapping, visited)
        is FieldContainingDescriptor<*> -> this.asSymbolicState(location, mapping, visited)
        is ArrayDescriptor -> this.asSymbolicState(location, mapping, visited)
        is MockDescriptor -> TODO("Mock. Implement later")
    }

    private fun ConstantDescriptor.asSymbolicState(
        location: Instruction,
        mapping: MutableMap<Term, Term>,
        visited: MutableSet<Descriptor>
    ) = persistentSymbolicState(
        path = persistentPathConditionOf(
            PathClause(
                PathClauseType.CONDITION_CHECK,
                location,
                path {
                    (mapping.getOrDefault(
                        this@asSymbolicState.term,
                        this@asSymbolicState.term
                    ) eq when (this@asSymbolicState) {
                        is ConstantDescriptor.Null -> const(null)
                        is ConstantDescriptor.Bool -> const(this@asSymbolicState.value)
                        is ConstantDescriptor.Byte -> const(this@asSymbolicState.value)
                        is ConstantDescriptor.Short -> const(this@asSymbolicState.value)
                        is ConstantDescriptor.Char -> const(this@asSymbolicState.value)
                        is ConstantDescriptor.Int -> const(this@asSymbolicState.value)
                        is ConstantDescriptor.Long -> const(this@asSymbolicState.value)
                        is ConstantDescriptor.Float -> const(this@asSymbolicState.value)
                        is ConstantDescriptor.Double -> const(this@asSymbolicState.value)
                    }) equality true
                }
            )
        )
    ).also { visited += this }

    private fun FieldContainingDescriptor<*>.asSymbolicState(
        location: Instruction,
        mapping: MutableMap<Term, Term>,
        visited: MutableSet<Descriptor>
    ): PersistentSymbolicState {
        visited += this
        var current = persistentSymbolicState()
        val objectTerm = run {
            val objectTerm = mapping.getOrDefault(this.term, this.term)
            when {
                objectTerm.type != this.type -> term { generate(this@asSymbolicState.type) }.also { replacement ->
                    current += persistentSymbolicState(
                        state = persistentClauseStateOf(
                            StateClause(location, state {
                                replacement equality (objectTerm `as` this@asSymbolicState.type)
                            })
                        )
                    )
                    mapping[this.term] = replacement
                }

                else -> objectTerm
            }
        }
        current += persistentSymbolicState(
            path = persistentPathConditionOf(
                PathClause(PathClauseType.NULL_CHECK, location, path {
                    (objectTerm eq null) equality false
                })
            )
        )
        for ((field, descriptor) in this.fields) {
            current += descriptor.asSymbolicState(location, mapping, visited)
            current += persistentSymbolicState(
                path = persistentPathConditionOf(
                    PathClause(PathClauseType.CONDITION_CHECK, location, path {
                        val fieldTerm = mapping.getOrDefault(descriptor.term, descriptor.term)
                        (objectTerm.field(field).load() eq fieldTerm) equality true
                    })
                )
            )
        }
        return current
    }

    private fun ArrayDescriptor.asSymbolicState(
        location: Instruction,
        mapping: MutableMap<Term, Term>,
        visited: MutableSet<Descriptor>
    ): PersistentSymbolicState {
        visited += this
        var current = persistentSymbolicState()
        val arrayTerm = run {
            val arrayTerm = mapping.getOrDefault(this.term, this.term)
            when {
                arrayTerm.type != this.type -> term { generate(this@asSymbolicState.type) }.also { replacement ->
                    current += persistentSymbolicState(
                        state = persistentClauseStateOf(
                            StateClause(location, state {
                                replacement equality (arrayTerm `as` this@asSymbolicState.type)
                            })
                        )
                    )
                    mapping[this.term] = replacement
                }

                else -> arrayTerm
            }
        }
        current += persistentSymbolicState(
            path = persistentPathConditionOf(
                PathClause(PathClauseType.NULL_CHECK, location, path {
                    (arrayTerm eq null) equality false
                })
            )
        )
        current += persistentSymbolicState(
            path = persistentPathConditionOf(
                PathClause(PathClauseType.CONDITION_CHECK, location, path {
                    (arrayTerm.length() eq this@asSymbolicState.length) equality true
                })
            )
        )
        for ((index, descriptor) in this.elements) {
            current += descriptor.asSymbolicState(location, mapping, visited)
            current += persistentSymbolicState(
                path = persistentPathConditionOf(
                    PathClause(PathClauseType.CONDITION_CHECK, location, path {
                        val elementTerm = mapping.getOrDefault(descriptor.term, descriptor.term)
                        (arrayTerm[index].load() eq elementTerm) equality true
                    })
                )
            )
        }
        return current
    }
}


data class ConstraintExceptionPrecondition(
    val parameters: Parameters<Term>,
    val precondition: PersistentSymbolicState
)


class ConstraintExceptionPreconditionBuilder(
    val ctx: ExecutionContext,
    override val targetException: Class,
    parameterSet: Set<ConstraintExceptionPrecondition>,
) : ExceptionPreconditionBuilder<ConstraintExceptionPrecondition> {
    private val parameterSet = parameterSet.toMutableSet()

    override fun addPrecondition(precondition: ConstraintExceptionPrecondition): Boolean {
        return parameterSet.add(precondition)
    }

    override fun build(location: Instruction, state: TraverserState): Set<PersistentSymbolicState> {
        val callInst = (location as? CallInst)
            ?: unreachable { log.error("Descriptor precondition is not valid for non-call instructions") }

        return parameterSet.mapTo(mutableSetOf()) { (parameters, precondition) ->
            var typePrecondition = persistentSymbolicState()

            val mapping = buildMap {
                val buildMapping = { original: Term, mapped: Term ->
                    if (original.type == mapped.type) {
                        this[original] = mapped
                    } else {
                        val casted = term { generate(original.type) }
                        typePrecondition += PathClause(
                            PathClauseType.TYPE_CHECK,
                            location,
                            state { (mapped `is` original.type) equality true }
                        )
                        typePrecondition += StateClause(location, state { casted equality (mapped `as` original.type) })
                        this[original] = casted
                    }
                }

                if (!callInst.isStatic) {
                    buildMapping(parameters.instance!!, state.mkTerm(callInst.callee))
                }
                for ((argValue, argDescriptor) in callInst.args.zip(parameters.arguments)) {
                    buildMapping(argDescriptor, state.mkTerm(argValue))
                }
            }

            SymbolicStateTermRemapper(mapping).apply(precondition)
        }
    }
}
