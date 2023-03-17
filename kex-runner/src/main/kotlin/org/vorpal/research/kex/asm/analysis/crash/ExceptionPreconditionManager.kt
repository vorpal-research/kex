package org.vorpal.research.kex.asm.analysis.crash

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.symbolic.TraverserState
import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.state.predicate.path
import org.vorpal.research.kex.state.predicate.state
import org.vorpal.research.kex.state.term.TermBuilder
import org.vorpal.research.kex.trace.symbolic.*
import org.vorpal.research.kfg.intWrapper
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.stringClass
import org.vorpal.research.kfg.type.SystemTypeNames

interface ExceptionPreConditionBuilder {
    fun build(location: CallInst, state: TraverserState): List<PersistentSymbolicState>
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
            contracts[cm["java/lang/StringIndexOutOfBoundsException"]] = object : ExceptionPreConditionBuilder {
                override fun build(location: CallInst, state: TraverserState): List<PersistentSymbolicState> {
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
            contracts[cm["java/lang/StringIndexOutOfBoundsException"]] = object : ExceptionPreConditionBuilder {
                override fun build(location: CallInst, state: TraverserState): List<PersistentSymbolicState> {
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
            contracts[cm["java/lang/StringIndexOutOfBoundsException"]] = object : ExceptionPreConditionBuilder {
                override fun build(location: CallInst, state: TraverserState): List<PersistentSymbolicState> {
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
            contracts[cm["java/lang/NumberFormatException"]] = object : ExceptionPreConditionBuilder {
                override fun build(location: CallInst, state: TraverserState): List<PersistentSymbolicState> {
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
            contracts[cm["java/lang/IllegalArgumentException"]] = object : ExceptionPreConditionBuilder {
                override fun build(location: CallInst, state: TraverserState): List<PersistentSymbolicState> {
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
    }

    fun resolve(callInst: CallInst, exception: Class): ExceptionPreConditionBuilder? =
        conditions.getOrDefault(callInst.method, emptyMap())[exception]
}
