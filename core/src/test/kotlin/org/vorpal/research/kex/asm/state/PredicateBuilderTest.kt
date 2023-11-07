package org.vorpal.research.kex.asm.state

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.vorpal.research.kex.KexTest
import org.vorpal.research.kex.asm.transform.LoopDeroller
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.predicate.ArrayStorePredicate
import org.vorpal.research.kex.state.predicate.CallPredicate
import org.vorpal.research.kex.state.predicate.EqualityPredicate
import org.vorpal.research.kex.state.predicate.FieldStorePredicate
import org.vorpal.research.kex.state.predicate.NewArrayPredicate
import org.vorpal.research.kex.state.predicate.NewPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.PredicateFactory
import org.vorpal.research.kex.state.predicate.PredicateType
import org.vorpal.research.kex.state.predicate.path
import org.vorpal.research.kex.state.term.ArrayIndexTerm
import org.vorpal.research.kex.state.term.ArrayLengthTerm
import org.vorpal.research.kex.state.term.ArrayLoadTerm
import org.vorpal.research.kex.state.term.BinaryTerm
import org.vorpal.research.kex.state.term.ConstIntTerm
import org.vorpal.research.kex.state.term.FieldLoadTerm
import org.vorpal.research.kex.state.term.FieldTerm
import org.vorpal.research.kex.state.term.NegTerm
import org.vorpal.research.kex.state.term.TermFactory
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.state.transformer.TermRenamer
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.analysis.LoopSimplifier
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.instruction.ArrayLoadInst
import org.vorpal.research.kfg.ir.value.instruction.ArrayStoreInst
import org.vorpal.research.kfg.ir.value.instruction.BinaryInst
import org.vorpal.research.kfg.ir.value.instruction.BranchInst
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.CastInst
import org.vorpal.research.kfg.ir.value.instruction.CatchInst
import org.vorpal.research.kfg.ir.value.instruction.CmpInst
import org.vorpal.research.kfg.ir.value.instruction.EnterMonitorInst
import org.vorpal.research.kfg.ir.value.instruction.ExitMonitorInst
import org.vorpal.research.kfg.ir.value.instruction.FieldLoadInst
import org.vorpal.research.kfg.ir.value.instruction.FieldStoreInst
import org.vorpal.research.kfg.ir.value.instruction.Handle
import org.vorpal.research.kfg.ir.value.instruction.InstanceOfInst
import org.vorpal.research.kfg.ir.value.instruction.InvokeDynamicInst
import org.vorpal.research.kfg.ir.value.instruction.JumpInst
import org.vorpal.research.kfg.ir.value.instruction.NewArrayInst
import org.vorpal.research.kfg.ir.value.instruction.NewInst
import org.vorpal.research.kfg.ir.value.instruction.PhiInst
import org.vorpal.research.kfg.ir.value.instruction.ReturnInst
import org.vorpal.research.kfg.ir.value.instruction.SwitchInst
import org.vorpal.research.kfg.ir.value.instruction.TableSwitchInst
import org.vorpal.research.kfg.ir.value.instruction.TerminateInst
import org.vorpal.research.kfg.ir.value.instruction.ThrowInst
import org.vorpal.research.kfg.ir.value.instruction.UnaryInst
import org.vorpal.research.kfg.ir.value.instruction.UnaryOpcode
import org.vorpal.research.kfg.type.commonSupertype
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.collection.mapToArray
import org.vorpal.research.kthelper.collection.zipTo
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.`try`
import kotlin.test.Test
import kotlin.test.assertTrue

class PredicateBuilderTest : KexTest("predicate-builder") {
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
                assertEquals(commonSupertype(setOf(inst.lhv.type, inst.rhv.type))?.kexType, rhv.type)
                assertEquals(rhv.lhv, tf.getValue(inst.lhv))
                assertEquals(rhv.rhv, tf.getValue(inst.rhv))
            }

            override fun visitBranchInst(inst: BranchInst) {
                assertFalse(builder.predicateMap.contains(inst))

                val cond = tf.getValue(inst.cond)
                assertEquals(
                    setOf(pf.getEquality(cond, tf.getTrue(), PredicateType.Path())),
                    builder.terminatorPredicateMap[inst.trueSuccessor to inst]
                )
                assertEquals(
                    setOf(pf.getEquality(cond, tf.getFalse(), PredicateType.Path())),
                    builder.terminatorPredicateMap[inst.falseSuccessor to inst]
                )
            }

            fun Value.asTerm() = when (this) {
                is InvokeDynamicInst -> `try` {
                    val lambdaBases = bootstrapMethodArgs.filterIsInstance<Handle>()
                    ktassert(lambdaBases.size == 1) { log.error("Unknown number of bases of ${print()}") }
                    val lambdaBase = lambdaBases.first()

                    val argParameters =
                        lambdaBase.method.argTypes.withIndex().map { term { arg(it.value.kexType, it.index) } }
                    val lambdaParameters = lambdaBase.method.argTypes.withIndex().map { (index, type) ->
                        term { value(type.kexType, "labmda_${lambdaBase.method.name}_$index") }
                    }
                    val mapping = argParameters.zipTo(lambdaParameters, mutableMapOf())
                    val `this` = term { `this`(lambdaBase.method.klass.kexType) }
                    mapping[`this`] = `this`

                    val expr = lambdaBase.method.asTermExpr()!!

                    term {
                        lambda(type.kexType, lambdaParameters) {
                            TermRenamer("labmda.${lambdaBase.method.name}", mapping)
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
                assertEquals(
                    field.owner, when {
                        inst.isStatic -> tf.getStaticRef(inst.field.klass)
                        else -> tf.getValue(inst.owner)
                    }
                )
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
                assertEquals(
                    rhv.owner, when {
                        inst.isStatic -> tf.getStaticRef(inst.field.klass)
                        else -> tf.getValue(inst.owner)
                    }
                )
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

                val dimensions = inst.dimensions.mapToArray { tf.getValue(it) }
                assertArrayEquals(predicate.dimensions.toTypedArray(), dimensions)
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
                val predicates = hashMapOf<Pair<BasicBlock, TerminateInst>, MutableSet<Predicate>>()
                for ((value, successor) in inst.branches) {
                    predicates.getOrPut(successor to inst, ::hashSetOf).add(
                        pf.getEquality(key, tf.getValue(value), PredicateType.Path())
                    )
                }
                predicates.getOrPut(inst.default to inst, ::hashSetOf).add(
                    pf.getDefaultSwitchPredicate(key, inst.branches.keys.map { tf.getValue(it) }, PredicateType.Path())
                )
                for ((mapKey, value) in predicates) {
                    assertEquals(
                        builder.terminatorPredicateMap[mapKey],
                        value
                    )
                }
            }

            override fun visitTableSwitchInst(inst: TableSwitchInst) {
                assertFalse(builder.predicateMap.contains(inst))

                val key = tf.getValue(inst.index)
                val min = tf.getValue(inst.min)
                val max = tf.getValue(inst.max)
                assertTrue(min is ConstIntTerm)
                assertTrue(max is ConstIntTerm)

                val predicates = hashMapOf<Pair<BasicBlock, TerminateInst>, MutableSet<Predicate>>()
                for ((index, successor) in inst.branches.withIndex()) {
                    predicates.getOrPut(successor to inst, ::hashSetOf).add(
                        path(inst.location) { key equality (min.value + index) }
                    )
                }
                predicates.getOrPut(inst.default to inst, ::hashSetOf).add(
                    path(inst.location) { key `!in` (min.value..max.value).map { const(it) } }
                )
                for ((mapKey, value) in predicates) {
                    assertEquals(
                        builder.terminatorPredicateMap[mapKey],
                        value
                    )
                }
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
        for (klass in cm.getByPackage(`package`)) {
            for (method in klass.allMethods) {
                if (method.isAbstract) continue
                if (method.hasLoops) {
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
