package org.vorpal.research.kex.asm.analysis.crash

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.symbolic.TraverserState
import org.vorpal.research.kex.descriptor.ArrayDescriptor
import org.vorpal.research.kex.descriptor.ConstantDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.FieldContainingDescriptor
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.state.predicate.path
import org.vorpal.research.kex.state.predicate.state
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.TermBuilder
import org.vorpal.research.kex.trace.symbolic.PathClause
import org.vorpal.research.kex.trace.symbolic.PathClauseType
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.StateClause
import org.vorpal.research.kex.trace.symbolic.persistentClauseStateOf
import org.vorpal.research.kex.trace.symbolic.persistentPathConditionOf
import org.vorpal.research.kex.trace.symbolic.persistentSymbolicState
import org.vorpal.research.kex.util.arrayIndexOOBClass
import org.vorpal.research.kex.util.asSet
import org.vorpal.research.kex.util.classCastClass
import org.vorpal.research.kex.util.illegalArgumentClass
import org.vorpal.research.kex.util.negativeArrayClass
import org.vorpal.research.kex.util.nullptrClass
import org.vorpal.research.kex.util.numberFormatClass
import org.vorpal.research.kex.util.stringIndexOOB
import org.vorpal.research.kfg.charWrapper
import org.vorpal.research.kfg.intWrapper
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.ArrayLoadInst
import org.vorpal.research.kfg.ir.value.instruction.ArrayStoreInst
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.CastInst
import org.vorpal.research.kfg.ir.value.instruction.FieldLoadInst
import org.vorpal.research.kfg.ir.value.instruction.FieldStoreInst
import org.vorpal.research.kfg.ir.value.instruction.NewArrayInst
import org.vorpal.research.kfg.ir.value.instruction.ThrowInst
import org.vorpal.research.kfg.stringClass
import org.vorpal.research.kfg.type.SystemTypeNames
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

interface ExceptionPreConditionBuilder {
    val targetException: Class
    fun build(location: Instruction, state: TraverserState): Set<PersistentSymbolicState>
}

class ExceptionPreConditionBuilderImpl(
    val ctx: ExecutionContext,
    override val targetException: Class,
) : ExceptionPreConditionBuilder {
    val cm get() = ctx.cm
    private val preconditionManager = ExceptionPreconditionManager(ctx)
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
                                (indexTerm ge 0) equality false
                            })
                        )
                    ),
                    persistentSymbolicState(
                        path = persistentPathConditionOf(
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
                is ThrowInst -> when (location.throwable.type) {
                    targetException.asType -> persistentSymbolicState().asSet()
                    else -> emptySet()
                }

                is CallInst -> preconditionManager.resolve(location, targetException)
                    ?.build(location, state)
                    ?: persistentSymbolicState().asSet()

                else -> unreachable { log.error("Instruction ${location.print()} does not throw target exception") }
            }
        }
}

class ExceptionPreconditionManager(
    val ctx: ExecutionContext
) : TermBuilder {
    private val cm get() = ctx.cm
    private val tf get() = ctx.types
    private val conditions = mutableMapOf<Method, MutableMap<Class, ExceptionPreConditionBuilder>>()


    init {
        val charSequenceClass = cm[SystemTypeNames.charSequence]
        val integerClass = cm.intWrapper
        val stringClass = cm.stringClass

        conditions.getOrPut(charSequenceClass.getMethod("charAt", tf.charType, tf.intType)) {
            val contracts = mutableMapOf<Class, ExceptionPreConditionBuilder>()
            contracts[cm.stringIndexOOB] = object : ExceptionPreConditionBuilder {
                override val targetException get() = cm.stringIndexOOB

                override fun build(location: Instruction, state: TraverserState): Set<PersistentSymbolicState> {
                    location as CallInst
                    val lengthMethod = charSequenceClass.getMethod("length", tf.intType)
                    val lengthTerm = generate(KexInt)
                    return setOf(
                        persistentSymbolicState(
                            path = persistentPathConditionOf(
                                PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                    (state.mkTerm(location.args[0]) ge 0) equality false
                                })
                            )
                        ),
                        persistentSymbolicState(
                            state = persistentClauseStateOf(
                                StateClause(location, state {
                                    lengthTerm.call(state.mkTerm(location.callee).call(lengthMethod))
                                })
                            ),
                            path = persistentPathConditionOf(
                                PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                    (state.mkTerm(location.args[0]) lt lengthTerm) equality false
                                })
                            )
                        )
                    )
                }
            }
            contracts
        }

        conditions.getOrPut(stringClass.getMethod("substring", stringClass.asType, tf.intType)) {
            val contracts = mutableMapOf<Class, ExceptionPreConditionBuilder>()
            contracts[cm.stringIndexOOB] = object : ExceptionPreConditionBuilder {
                override val targetException get() = cm.stringIndexOOB

                override fun build(location: Instruction, state: TraverserState): Set<PersistentSymbolicState> {
                    location as CallInst
                    val lengthMethod = stringClass.getMethod("length", tf.intType)
                    val lengthTerm = generate(KexInt)
                    return setOf(
                        persistentSymbolicState(
                            path = persistentPathConditionOf(
                                PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                    (state.mkTerm(location.args[0]) ge 0) equality false
                                })
                            )
                        ),
                        persistentSymbolicState(
                            state = persistentClauseStateOf(
                                StateClause(location, state {
                                    lengthTerm.call(state.mkTerm(location.callee).call(lengthMethod))
                                })
                            ),
                            path = persistentPathConditionOf(
                                PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                    (state.mkTerm(location.args[0]) lt lengthTerm) equality false
                                })
                            )
                        )
                    )
                }
            }
            contracts
        }

        conditions.getOrPut(stringClass.getMethod("substring", stringClass.asType, tf.intType, tf.intType)) {
            val contracts = mutableMapOf<Class, ExceptionPreConditionBuilder>()
            contracts[cm.stringIndexOOB] = object : ExceptionPreConditionBuilder {
                override val targetException get() = cm.stringIndexOOB

                override fun build(location: Instruction, state: TraverserState): Set<PersistentSymbolicState> {
                    location as CallInst
                    val lengthMethod = stringClass.getMethod("length", tf.intType)
                    val lengthTerm = generate(KexInt)
                    return setOf(
                        persistentSymbolicState(
                            path = persistentPathConditionOf(
                                PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                    (state.mkTerm(location.args[0]) ge 0) equality false
                                })
                            )
                        ),
                        persistentSymbolicState(
                            state = persistentClauseStateOf(
                                StateClause(location, state {
                                    lengthTerm.call(state.mkTerm(location.callee).call(lengthMethod))
                                })
                            ),
                            path = persistentPathConditionOf(
                                PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                    (state.mkTerm(location.args[0]) lt lengthTerm) equality false
                                })
                            )
                        ),
                        persistentSymbolicState(
                            state = persistentClauseStateOf(
                                StateClause(location, state {
                                    lengthTerm.call(state.mkTerm(location.callee).call(lengthMethod))
                                })
                            ),
                            path = persistentPathConditionOf(
                                PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                    (state.mkTerm(location.args[1]) le lengthTerm) equality false
                                })
                            )
                        ),
                        persistentSymbolicState(
                            path = persistentPathConditionOf(
                                PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                    (state.mkTerm(location.args[0]) lt state.mkTerm(location.args[1])) equality false
                                })
                            )
                        )
                    )
                }
            }
            contracts
        }

        conditions.getOrPut(integerClass.getMethod("decode", integerClass.asType, stringClass.asType)) {
            val contracts = mutableMapOf<Class, ExceptionPreConditionBuilder>()
            contracts[cm.numberFormatClass] = object : ExceptionPreConditionBuilder {
                override val targetException get() = cm.numberFormatClass

                override fun build(location: Instruction, state: TraverserState): Set<PersistentSymbolicState> {
                    location as CallInst
//                    val startsWithMethod = stringClass.getMethod("startsWith", tf.boolType, stringClass.asType)
                    val lengthMethod = stringClass.getMethod("length", tf.intType)
//                    val prefixConditionTerm = generate(KexBool)
                    val lengthTerm = generate(KexInt)
                    val argTerm = state.mkTerm(location.args[0])

                    return setOf(
                        persistentSymbolicState(
                            state = persistentClauseStateOf(
                                StateClause(location, state { lengthTerm.call(argTerm.call(lengthMethod)) })
                            ),
                            path = persistentPathConditionOf(
                                PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                    (lengthTerm gt 0) equality false
                                })
                            )
                        ),
                        persistentSymbolicState(
                            state = persistentClauseStateOf(
                                StateClause(location, state { lengthTerm.call(argTerm.call(lengthMethod)) })
                            ),
                            path = persistentPathConditionOf(
                                PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                    (lengthTerm le 32) equality false
                                })
                            )
                        ),
                    )
                }
            }
            contracts
        }


        val inetAddressClass = cm["java/net/InetAddress"]
        conditions.getOrPut(
            inetAddressClass.getMethod(
                "getAllByName",
                inetAddressClass.asType.asArray,
                stringClass.asType
            )
        ) {
            val contracts = mutableMapOf<Class, ExceptionPreConditionBuilder>()
            contracts[cm.illegalArgumentClass] = object : ExceptionPreConditionBuilder {
                override val targetException get() = cm.illegalArgumentClass

                override fun build(location: Instruction, state: TraverserState): Set<PersistentSymbolicState> {
                    location as CallInst
                    val lengthMethod = stringClass.getMethod("length", tf.intType)
                    val lengthTerm = generate(KexInt)
                    val argTerm = state.mkTerm(location.args[0])

                    return setOf(
                        persistentSymbolicState(
                            state = persistentClauseStateOf(
                                StateClause(location, state { lengthTerm.call(argTerm.call(lengthMethod)) })
                            ),
                            path = persistentPathConditionOf(
                                PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                    (lengthTerm gt 0) equality false
                                })
                            )
                        ),
                        persistentSymbolicState(
                            state = persistentClauseStateOf(
                                StateClause(location, state { lengthTerm.call(argTerm.call(lengthMethod)) })
                            ),
                            path = persistentPathConditionOf(
                                PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                    (lengthTerm le 32) equality false
                                })
                            )
                        ),
                    )
                }
            }
            contracts
        }

        val charClass = cm.charWrapper
        conditions.getOrPut(charClass.getMethod("codePointAt", tf.intType, charSequenceClass.asType, tf.intType)) {
            val contracts = mutableMapOf<Class, ExceptionPreConditionBuilder>()
            contracts[cm.stringIndexOOB] = object : ExceptionPreConditionBuilder {
                override val targetException get() = cm.stringIndexOOB

                override fun build(location: Instruction, state: TraverserState): Set<PersistentSymbolicState> {
                    location as CallInst
                    val lengthMethod = charSequenceClass.getMethod("length", tf.intType)
                    val lengthTerm = generate(KexInt)
                    val charSeqTerm = state.mkTerm(location.args[0])
                    val indexTerm = state.mkTerm(location.args[1])

                    return setOf(
                        persistentSymbolicState(
                            path = persistentPathConditionOf(
                                PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                    (indexTerm gt 0) equality false
                                })
                            )
                        ),
                        persistentSymbolicState(
                            state = persistentClauseStateOf(
                                StateClause(location, state { lengthTerm.call(charSeqTerm.call(lengthMethod)) })
                            ),
                            path = persistentPathConditionOf(
                                PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                    (indexTerm lt lengthTerm) equality false
                                })
                            )
                        ),
                    )
                }
            }
            contracts
        }
    }

    fun resolve(callInst: CallInst, exception: Class): ExceptionPreConditionBuilder? =
        conditions.getOrDefault(callInst.method, emptyMap())[exception]
}

class DescriptorPreconditionBuilder(
    val ctx: ExecutionContext,
    override val targetException: Class,
    private val parameterSet: Set<Parameters<Descriptor>>,
) : ExceptionPreConditionBuilder {
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
            }
            for (descriptor in parameters.asList) {
                result += descriptor.asSymbolicState(callInst, mapping)
            }
            result
        }
    }

    private fun Descriptor.asSymbolicState(
        location: Instruction,
        mapping: Map<Term, Term>
    ): PersistentSymbolicState = when (this) {
        is ConstantDescriptor -> this.asSymbolicState(location, mapping)
        is FieldContainingDescriptor<*> -> this.asSymbolicState(location, mapping)
        is ArrayDescriptor -> this.asSymbolicState(location, mapping)
    }

    private fun ConstantDescriptor.asSymbolicState(
        location: Instruction,
        @Suppress("UNUSED_PARAMETER")
        mapping: Map<Term, Term>
    ) = persistentSymbolicState(
        path = persistentPathConditionOf(
            PathClause(
                PathClauseType.CONDITION_CHECK,
                location,
                path {
                    (this@asSymbolicState.term eq when (this@asSymbolicState) {
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
    )

    private fun FieldContainingDescriptor<*>.asSymbolicState(
        location: Instruction,
        mapping: Map<Term, Term>
    ): PersistentSymbolicState {
        var current = persistentSymbolicState()
        val objectTerm = mapping.getOrDefault(this.term, this.term)
        for ((field, descriptor) in this.fields) {
            current += descriptor.asSymbolicState(location, mapping)
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
        mapping: Map<Term, Term>
    ): PersistentSymbolicState {
        var current = persistentSymbolicState()
        val arrayTerm = mapping.getOrDefault(this.term, this.term)
        for ((index, descriptor) in this.elements) {
            current += descriptor.asSymbolicState(location, mapping)
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
