package org.vorpal.research.kex.asm.analysis.crash.precondition

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.symbolic.TraverserState
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.state.predicate.path
import org.vorpal.research.kex.state.term.TermBuilder
import org.vorpal.research.kex.trace.symbolic.PathClause
import org.vorpal.research.kex.trace.symbolic.PathClauseType
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.StateClause
import org.vorpal.research.kex.trace.symbolic.persistentClauseStateOf
import org.vorpal.research.kex.trace.symbolic.persistentPathConditionOf
import org.vorpal.research.kex.trace.symbolic.persistentSymbolicState
import org.vorpal.research.kfg.charSequence
import org.vorpal.research.kfg.charWrapper
import org.vorpal.research.kfg.illegalArgumentClass
import org.vorpal.research.kfg.intWrapper
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.numberFormatClass
import org.vorpal.research.kfg.stringClass
import org.vorpal.research.kfg.stringIndexOOB


class ExceptionPreconditionManager(
    val ctx: ExecutionContext
) : TermBuilder {
    private val cm get() = ctx.cm
    private val tf get() = ctx.types
    private val conditions = mutableMapOf<Method, MutableMap<Class, ExceptionPreconditionBuilder>>()


    init {
        val charSequenceClass = cm.charSequence
        val integerClass = cm.intWrapper
        val stringClass = cm.stringClass

        conditions.getOrPut(charSequenceClass.getMethod("charAt", tf.charType, tf.intType)) {
            val contracts = mutableMapOf<Class, ExceptionPreconditionBuilder>()
            contracts[cm.stringIndexOOB] = object : ExceptionPreconditionBuilder {
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
                                StateClause(location, org.vorpal.research.kex.state.predicate.state {
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

        conditions.getOrPut(stringClass.getMethod("charAt", tf.charType, tf.intType)) {
            val contracts = mutableMapOf<Class, ExceptionPreconditionBuilder>()
            contracts[cm.stringIndexOOB] = object : ExceptionPreconditionBuilder {
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
                                StateClause(location, org.vorpal.research.kex.state.predicate.state {
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
            val contracts = mutableMapOf<Class, ExceptionPreconditionBuilder>()
            contracts[cm.stringIndexOOB] = object : ExceptionPreconditionBuilder {
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
                                StateClause(location, org.vorpal.research.kex.state.predicate.state {
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
            val contracts = mutableMapOf<Class, ExceptionPreconditionBuilder>()
            contracts[cm.stringIndexOOB] = object : ExceptionPreconditionBuilder {
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
                                StateClause(location, org.vorpal.research.kex.state.predicate.state {
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
                                StateClause(location, org.vorpal.research.kex.state.predicate.state {
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
            val contracts = mutableMapOf<Class, ExceptionPreconditionBuilder>()
            contracts[cm.numberFormatClass] = object : ExceptionPreconditionBuilder {
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
                                StateClause(location, org.vorpal.research.kex.state.predicate.state { lengthTerm.call(argTerm.call(lengthMethod)) })
                            ),
                            path = persistentPathConditionOf(
                                PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                    (lengthTerm gt 0) equality false
                                })
                            )
                        ),
                        persistentSymbolicState(
                            state = persistentClauseStateOf(
                                StateClause(location, org.vorpal.research.kex.state.predicate.state { lengthTerm.call(argTerm.call(lengthMethod)) })
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
            val contracts = mutableMapOf<Class, ExceptionPreconditionBuilder>()
            contracts[cm.illegalArgumentClass] = object : ExceptionPreconditionBuilder {
                override val targetException get() = cm.illegalArgumentClass

                override fun build(location: Instruction, state: TraverserState): Set<PersistentSymbolicState> {
                    location as CallInst
                    val lengthMethod = stringClass.getMethod("length", tf.intType)
                    val lengthTerm = generate(KexInt)
                    val argTerm = state.mkTerm(location.args[0])

                    return setOf(
                        persistentSymbolicState(
                            state = persistentClauseStateOf(
                                StateClause(location, org.vorpal.research.kex.state.predicate.state { lengthTerm.call(argTerm.call(lengthMethod)) })
                            ),
                            path = persistentPathConditionOf(
                                PathClause(PathClauseType.BOUNDS_CHECK, location, path {
                                    (lengthTerm gt 0) equality false
                                })
                            )
                        ),
                        persistentSymbolicState(
                            state = persistentClauseStateOf(
                                StateClause(location, org.vorpal.research.kex.state.predicate.state { lengthTerm.call(argTerm.call(lengthMethod)) })
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
            val contracts = mutableMapOf<Class, ExceptionPreconditionBuilder>()
            contracts[cm.stringIndexOOB] = object : ExceptionPreconditionBuilder {
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
                                StateClause(location, org.vorpal.research.kex.state.predicate.state { lengthTerm.call(charSeqTerm.call(lengthMethod)) })
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

    fun resolve(callInst: CallInst, exception: Class): ExceptionPreconditionBuilder? =
        conditions.getOrDefault(callInst.method, emptyMap())[exception]
}

