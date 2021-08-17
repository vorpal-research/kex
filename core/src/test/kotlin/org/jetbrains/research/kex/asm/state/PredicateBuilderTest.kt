package org.jetbrains.research.kex.asm.state

import org.jetbrains.research.kex.KexTest
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.state.transformer.TermRenamer
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.analysis.LoopAnalysis
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.mergeTypes
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.`try`
import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.logging.log
import org.junit.Assert.*
import kotlin.test.Test
import kotlin.test.assertTrue

class PredicateBuilderTest : KexTest() {
    val tf = TermFactory
    val pf = PredicateFactory

    private val predicateChecker = { builder: PredicateBuilder ->
        object : MethodVisitor {
            override val cm: ClassManager
                get() = this@PredicateBuilderTest.cm

            override fun cleanup() {}

            override fun visitArrayLoadInst(inst: ArrayLoadInst) {
                assertTrue(builder.predicateMap.contains(inst))

                val predicate = builder.predicateMap.getValue(inst)
                assertTrue(predicate is EqualityPredicate)

                val lhv = predicate.lhv
                assertEquals(lhv, tf.getValue(inst))

                val rhv = predicate.rhv
                assertTrue(rhv is ArrayLoadTerm)

                val ref = rhv.arrayRef
                assertTrue(ref is ArrayIndexTerm)
                assertEquals(ref.arrayRef, tf.getValue(inst.arrayRef))
                assertEquals(ref.index, tf.getValue(inst.index))
            }

            override fun visitArrayStoreInst(inst: ArrayStoreInst) {
                assertTrue(builder.predicateMap.contains(inst))

                val predicate = builder.predicateMap.getValue(inst)
                assertTrue(predicate is ArrayStorePredicate)

                val value = predicate.value
                assertEquals(value, tf.getValue(inst.value))

                val ref = predicate.arrayRef
                assertTrue(ref is ArrayIndexTerm)
                assertEquals(ref.arrayRef, tf.getValue(inst.arrayRef))
                assertEquals(ref.index, tf.getValue(inst.index))
            }

            override fun visitBinaryInst(inst: BinaryInst) {
                assertTrue(builder.predicateMap.contains(inst))

                val predicate = builder.predicateMap.getValue(inst)
                assertTrue(predicate is EqualityPredicate)

                assertEquals(predicate.lhv, tf.getValue(inst))

                val rhv = predicate.rhv
                assertTrue(rhv is BinaryTerm)

                assertEquals(rhv.opcode, inst.opcode)
                assertEquals(mergeTypes(cm.type, setOf(inst.lhv.type, inst.rhv.type))?.kexType, rhv.type)
                assertEquals(rhv.lhv, tf.getValue(inst.lhv))
                assertEquals(rhv.rhv, tf.getValue(inst.rhv))
            }

            override fun visitBranchInst(inst: BranchInst) {
                assertFalse(builder.predicateMap.contains(inst))

                val cond = tf.getValue(inst.cond)
                assertEquals(
                        pf.getEquality(cond, tf.getTrue(), PredicateType.Path()),
                        builder.terminatorPredicateMap[inst.trueSuccessor to inst]
                )
                assertEquals(
                        pf.getEquality(cond, tf.getFalse(), PredicateType.Path()),
                        builder.terminatorPredicateMap[inst.falseSuccessor to inst]
                )
            }

            fun Value.asTerm() = when (this) {
                is InvokeDynamicInst -> `try` {
                    val lambdaBases = bootstrapMethodArgs.filterIsInstance<Handle>()
                    ktassert(lambdaBases.size == 1) { log.error("Unknown number of bases of ${print()}") }
                    val lambdaBase = lambdaBases.first()

                    val argParameters = lambdaBase.method.argTypes.withIndex().map { term { arg(it.value.kexType, it.index) } }
                    val lambdaParameters = lambdaBase.method.argTypes.withIndex().map { (index, type) ->
                        term { value(type.kexType, "labmda_${lambdaBase.method.name}_$index") }
                    }

                    val expr = lambdaBase.method.asTermExpr()!!

                    term {
                        lambda(type.kexType, lambdaParameters) {
                            TermRenamer("labmda.${lambdaBase.method.name}", argParameters.zip(lambdaParameters).toMap())
                                .transform(expr)
                        }
                    }
                }.getOrElse { tf.getValue(this) }
                else -> tf.getValue(this)
            }

            override fun visitCallInst(inst: CallInst) {
                assertTrue(builder.predicateMap.contains(inst))

                val predicate = builder.predicateMap.getValue(inst)
                assertTrue(predicate is CallPredicate)

                val lhv = if (inst.type.isVoid) null else tf.getValue(inst)
                assertEquals(lhv, predicate.lhvUnsafe)

                val args = inst.args.map { it.asTerm() }
                val callTerm = if (inst.isStatic) {
                    tf.getCall(inst.method, args)
                } else {
                    val callee = tf.getValue(inst.callee)
                    tf.getCall(inst.method, callee, args)
                }
                assertEquals(callTerm, predicate.call)
            }

            override fun visitCastInst(inst: CastInst) {
                assertTrue(builder.predicateMap.contains(inst))

                val predicate = builder.predicateMap.getValue(inst)
                assertTrue(predicate is EqualityPredicate)

                assertEquals(predicate.lhv, tf.getValue(inst))
                assertEquals(predicate.rhv, tf.getCast(inst.type.kexType, tf.getValue(inst.operand)))
            }

            override fun visitCmpInst(inst: CmpInst) {
                assertTrue(builder.predicateMap.contains(inst))

                val predicate = builder.predicateMap.getValue(inst)
                assertTrue(predicate is EqualityPredicate)

                assertEquals(predicate.lhv, tf.getValue(inst))
                assertEquals(predicate.rhv, tf.getCmp(inst.opcode, tf.getValue(inst.lhv), tf.getValue(inst.rhv)))
            }

            override fun visitFieldLoadInst(inst: FieldLoadInst) {
                assertTrue(builder.predicateMap.contains(inst))

                val predicate = builder.predicateMap.getValue(inst)
                assertTrue(predicate is EqualityPredicate)

                assertEquals(predicate.lhv, tf.getValue(inst))

                val rhv = predicate.rhv
                assertTrue(rhv is FieldLoadTerm)

                val field = rhv.field
                assertTrue(field is FieldTerm)

                assertEquals(rhv.isStatic, inst.isStatic)
                assertEquals(field.owner, when {
                    inst.isStatic -> tf.getClass(inst.field.klass)
                    else -> tf.getValue(inst.owner)
                })
                assertEquals(field.fieldName, inst.field.name)
            }

            override fun visitFieldStoreInst(inst: FieldStoreInst) {
                assertTrue(builder.predicateMap.contains(inst))

                val predicate = builder.predicateMap.getValue(inst)
                assertTrue(predicate is FieldStorePredicate)

                assertEquals(predicate.value, tf.getValue(inst.value))

                val rhv = predicate.field
                assertTrue(rhv is FieldTerm)

                assertEquals(rhv.isStatic, inst.isStatic)
                assertEquals(rhv.owner, when {
                    inst.isStatic -> tf.getClass(inst.field.klass)
                    else -> tf.getValue(inst.owner)
                })
                assertEquals(rhv.fieldName, inst.field.name)
            }

            override fun visitInstanceOfInst(inst: InstanceOfInst) {
                assertTrue(builder.predicateMap.contains(inst))

                val predicate = builder.predicateMap.getValue(inst)
                assertTrue(predicate is EqualityPredicate)

                assertEquals(predicate.lhv, tf.getValue(inst))
                assertEquals(predicate.rhv, tf.getInstanceOf(inst.targetType.kexType, tf.getValue(inst.operand)))
            }

            override fun visitNewArrayInst(inst: NewArrayInst) {
                assertTrue(builder.predicateMap.contains(inst))

                val predicate = builder.predicateMap.getValue(inst)
                assertTrue(predicate is NewArrayPredicate)

                assertEquals(predicate.lhv, tf.getValue(inst))

                val dimensions = inst.dimensions.map { tf.getValue(it) }
                assertArrayEquals(predicate.dimensions.toTypedArray(), dimensions.toTypedArray())
            }

            override fun visitNewInst(inst: NewInst) {
                assertTrue(builder.predicateMap.contains(inst))

                val predicate = builder.predicateMap.getValue(inst)
                assertTrue(predicate is NewPredicate)

                assertEquals(predicate.lhv, tf.getValue(inst))
            }

            override fun visitPhiInst(inst: PhiInst) {
                assertFalse(builder.predicateMap.contains(inst))

                inst.incomings.forEach { (from, value) ->
                    assertEquals(
                            builder.phiPredicateMap[from to inst],
                            pf.getEquality(tf.getValue(inst), tf.getValue(value))
                    )
                }
            }

            override fun visitUnaryInst(inst: UnaryInst) {
                assertTrue(builder.predicateMap.contains(inst))

                val predicate = builder.predicateMap.getValue(inst)
                assertTrue(predicate is EqualityPredicate)

                assertEquals(predicate.lhv, tf.getValue(inst))
                val rhv = predicate.rhv
                when (inst.opcode) {
                    UnaryOpcode.NEG -> {
                        assertTrue(rhv is NegTerm)
                        assertEquals(rhv.operand, tf.getValue(inst.operand))
                    }
                    UnaryOpcode.LENGTH -> {
                        assertTrue(rhv is ArrayLengthTerm)
                        assertEquals(rhv.arrayRef, tf.getValue(inst.operand))
                    }
                }
            }

            override fun visitSwitchInst(inst: SwitchInst) {
                val key = tf.getValue(inst.key)
                for ((value, successor) in inst.branches) {
                    assertEquals(
                            builder.terminatorPredicateMap[successor to inst],
                            pf.getEquality(key, tf.getValue(value), PredicateType.Path())
                    )
                }
                assertEquals(
                        builder.terminatorPredicateMap[inst.default to inst],
                        pf.getDefaultSwitchPredicate(key, inst.branches.keys.map { tf.getValue(it) }, PredicateType.Path())
                )
            }

            override fun visitTableSwitchInst(inst: TableSwitchInst) {
                assertFalse(builder.predicateMap.contains(inst))

                val key = tf.getValue(inst.index)
                val min = tf.getValue(inst.min)
                val max = tf.getValue(inst.max)
                assertTrue(min is ConstIntTerm)
                assertTrue(max is ConstIntTerm)

                inst.branches.withIndex().forEach { (index, bb) ->
                    when (bb) {
                        inst.default ->
                            assertEquals(
                                    builder.terminatorPredicateMap[inst.default to inst],
                                    pf.getDefaultSwitchPredicate(key, (min.value..max.value).map { tf.getInt(it) }, PredicateType.Path())
                            )
                        else -> assertEquals(
                                builder.terminatorPredicateMap[bb to inst],
                                pf.getEquality(key, tf.getInt(min.value + index), PredicateType.Path())
                        )
                    }

                }
                assertEquals(
                        builder.terminatorPredicateMap[inst.default to inst],
                        pf.getDefaultSwitchPredicate(key, (min.value..max.value).map { tf.getInt(it) }, PredicateType.Path())
                )
            }

            override fun visitReturnInst(inst: ReturnInst) {
                if (inst.hasReturnValue) {
                    assertTrue(builder.predicateMap.contains(inst))
                    val predicate = builder.predicateMap.getValue(inst)

                    assertTrue(predicate is EqualityPredicate)

                    val returnableValue = predicate.rhv

                    assertEquals(returnableValue, tf.getValue(inst.returnValue))
                } else {
                    assertFalse(builder.predicateMap.contains(inst))
                }
            }

            // ignored instructions
            override fun visitCatchInst(inst: CatchInst) {
                assertFalse(builder.predicateMap.contains(inst))
            }

            override fun visitEnterMonitorInst(inst: EnterMonitorInst) {
                assertFalse(builder.predicateMap.contains(inst))
            }

            override fun visitExitMonitorInst(inst: ExitMonitorInst) {
                assertFalse(builder.predicateMap.contains(inst))
            }

            override fun visitJumpInst(inst: JumpInst) {
                assertFalse(builder.predicateMap.contains(inst))
            }

            override fun visitThrowInst(inst: ThrowInst) {
                assertFalse(builder.predicateMap.contains(inst))
            }
        }
    }

    @Test
    fun testPredicateBuilder() {
        for (klass in cm.concreteClasses) {
            for (method in klass.allMethods) {
                if (method.isAbstract) continue
                val loops = LoopAnalysis(cm).invoke(method)
                if (loops.isNotEmpty()) {
                    LoopSimplifier(cm).visit(method)
                    LoopDeroller(cm).visit(method)
                }
                val predicateBuilder = PredicateBuilder(cm)
                predicateBuilder.visit(method)

                predicateChecker(predicateBuilder).visit(method)
            }
        }
    }
}