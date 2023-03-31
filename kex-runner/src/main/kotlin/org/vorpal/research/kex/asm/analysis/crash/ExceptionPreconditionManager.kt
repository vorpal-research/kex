package org.vorpal.research.kex.asm.analysis.crash

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.symbolic.TraverserState
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.predicate.path
import org.vorpal.research.kex.state.predicate.state
import org.vorpal.research.kex.state.term.TermBuilder
import org.vorpal.research.kex.trace.symbolic.*
import org.vorpal.research.kex.util.*
import org.vorpal.research.kfg.charWrapper
import org.vorpal.research.kfg.intWrapper
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.*
import org.vorpal.research.kfg.stringClass
import org.vorpal.research.kfg.type.SystemTypeNames
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

interface ExceptionPreConditionBuilder {
    fun build(location: Instruction, state: TraverserState): List<PersistentSymbolicState>
}

class ExceptionPreConditionBuilderImpl(
    val ctx: ExecutionContext,
    private val targetException: Class,
) : ExceptionPreConditionBuilder {
    val cm get() = ctx.cm
    private val preconditionManager = ExceptionPreconditionManager(ctx)
    override fun build(location: Instruction, state: TraverserState): List<PersistentSymbolicState> =
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
            ).asList()

            cm.arrayIndexOOBClass -> {
                val (arrayTerm, indexTerm) = when (location) {
                    is ArrayLoadInst -> state.mkTerm(location.arrayRef) to state.mkTerm(location.index)
                    is ArrayStoreInst -> state.mkTerm(location.arrayRef) to state.mkTerm(location.index)
                    else -> unreachable { log.error("Instruction ${location.print()} does not throw array index out of bounds") }
                }
                listOf(
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
                is NewArrayInst -> location.dimensions.map { length ->
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
                is CastInst -> listOf(
                    persistentSymbolicState(
                        path = persistentPathConditionOf(
                            PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                (state.mkTerm(location.operand) `is` location.type.kexType) equality false
                            }),
                        )
                    )
                )

                else -> unreachable { log.error("Instruction ${location.print()} does not throw class cast") }
            }

            else -> when (location) {
                is ThrowInst -> when (location.throwable.type) {
                    targetException.asType -> persistentSymbolicState().asList()
                    else -> emptyList()
                }

                is CallInst -> preconditionManager.resolve(location, targetException)
                    ?.build(location, state)
                    ?: persistentSymbolicState().asList()

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
                override fun build(location: Instruction, state: TraverserState): List<PersistentSymbolicState> {
                    location as CallInst
                    val lengthMethod = charSequenceClass.getMethod("length", tf.intType)
                    val lengthTerm = generate(KexInt)
                    return listOf(
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
                override fun build(location: Instruction, state: TraverserState): List<PersistentSymbolicState> {
                    location as CallInst
                    val lengthMethod = stringClass.getMethod("length", tf.intType)
                    val lengthTerm = generate(KexInt)
                    return listOf(
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
                override fun build(location: Instruction, state: TraverserState): List<PersistentSymbolicState> {
                    location as CallInst
                    val lengthMethod = stringClass.getMethod("length", tf.intType)
                    val lengthTerm = generate(KexInt)
                    return listOf(
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
                override fun build(location: Instruction, state: TraverserState): List<PersistentSymbolicState> {
                    location as CallInst
//                    val startsWithMethod = stringClass.getMethod("startsWith", tf.boolType, stringClass.asType)
                    val lengthMethod = stringClass.getMethod("length", tf.intType)
//                    val prefixConditionTerm = generate(KexBool)
                    val lengthTerm = generate(KexInt)
                    val argTerm = state.mkTerm(location.args[0])

                    return listOf(
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
        conditions.getOrPut(inetAddressClass.getMethod("getAllByName", inetAddressClass.asType.asArray, stringClass.asType)) {
            val contracts = mutableMapOf<Class, ExceptionPreConditionBuilder>()
            contracts[cm.illegalArgumentClass] = object : ExceptionPreConditionBuilder {
                override fun build(location: Instruction, state: TraverserState): List<PersistentSymbolicState> {
                    location as CallInst
                    val lengthMethod = stringClass.getMethod("length", tf.intType)
                    val lengthTerm = generate(KexInt)
                    val argTerm = state.mkTerm(location.args[0])

                    return listOf(
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
                override fun build(location: Instruction, state: TraverserState): List<PersistentSymbolicState> {
                    location as CallInst
                    val lengthMethod = charSequenceClass.getMethod("length", tf.intType)
                    val lengthTerm = generate(KexInt)
                    val charSeqTerm = state.mkTerm(location.args[0])
                    val indexTerm = state.mkTerm(location.args[1])

                    return listOf(
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
