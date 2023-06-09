package org.vorpal.research.kex.smt.ksmt

import kotlinx.collections.immutable.persistentListOf
import org.junit.AfterClass
import org.junit.BeforeClass
import org.ksmt.KContext
import org.ksmt.expr.KExpr
import org.ksmt.solver.KSolverStatus
import org.ksmt.solver.runner.KSolverRunnerManager
import org.ksmt.solver.z3.KZ3Solver
import org.ksmt.sort.KBoolSort
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.KexTest
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.random.StubRandomizer
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.smt.ksmt.KSMTEngine.asExpr
import org.vorpal.research.kex.state.PredicateQuery
import org.vorpal.research.kex.state.basic
import org.vorpal.research.kex.state.predicate.path
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kfg.ClassManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Suppress("UNCHECKED_CAST")
class KSMTSolverTest : KexTest("ksmt-solver") {
    companion object {
        private lateinit var solverManager: KSolverRunnerManager

        @JvmStatic
        @BeforeClass
        fun initSolverManager() {
            solverManager = KSolverRunnerManager(workerPoolSize = 1)
        }

        @JvmStatic
        @AfterClass
        fun closeSolverManager() {
            solverManager.close()
        }
    }

    @Test
    fun testRunnable() {
        val ef = KSMTExprFactory()

        val a = ef.makeInt("a")
        val b = ef.makeInt("b")
        val c = ef.makeInt("c")

        val zero = ef.makeIntConst(0)
        val one = ef.makeIntConst(1)

        val query = c neq zero

        val state = c eq (ef.if_<Int_>(a gt b).then(zero).`else`(one))

        val ctx = ef.ctx
        val solver = solverManager.createSolver(ctx, KZ3Solver::class)
        solver.assert(query.asAxiom() as KExpr<KBoolSort>)
        solver.assert(state.asAxiom() as KExpr<KBoolSort>)

        val res = solver.check()
        assertEquals(KSolverStatus.SAT, res)
        solver.close()
    }

    @Test
    fun testDefaultMemory() {
        val ef = KSMTExprFactory()
        val checkExpr = { e: Dynamic_ ->
            val ctx = ef.ctx
            val solver = solverManager.createSolver(ctx, KZ3Solver::class)
            solver.assert(e.axiom as KExpr<KBoolSort>)
            solver.assert(KSMTEngine.negate(ef.ctx, e.expr) as KExpr<KBoolSort>)
            val res = solver.check() == KSolverStatus.UNSAT
            solver.close()
            res
        }

        val memory = ef.makeDefaultMemory<Word_>("mem", ef.makeIntConst(0xFF))
        for (i in 0..128) {
            assertTrue(checkExpr(memory[ef.makePtrConst(i)] eq Word_.makeConst(ef.ctx, 0xFF)))
        }
    }

    @Test
    fun testMergeMemory() {
        val ef = KSMTExprFactory()

        val default = KSMTContext(ef)
        val memA = KSMTContext(ef)
        val memB = KSMTContext(ef)

        val ptr = ef.makePtr("ptr")
        val a = ef.makeIntConst(0xDEAD)
        val b = ef.makeIntConst(0xBEEF)
        val z = ef.makeIntConst(0xABCD)

        val cond = ef.makeInt("cond")
        val condA = cond eq a
        val condB = cond eq b

        memA.writeWordMemory(ptr, 0, a)
        memB.writeWordMemory(ptr, 0, b)

        val merged = KSMTContext.mergeContexts(
            "merged", default, mapOf(
                condA to memA,
                condB to memB
            )
        )

        val c = merged.readWordMemory(ptr, 0)

        val checkExprIn = { e: Bool_, `in`: Dynamic_ ->
            val ctx = ef.ctx
            val solver = solverManager.createSolver(ctx, KZ3Solver::class)
            solver.assert(`in`.asAxiom() as KExpr<KBoolSort>)

            val pred = ef.makeBool("\$CHECK$")
            val ptrll = ef.makePtr("ptrll")
            solver.assert((ptrll eq c).asAxiom() as KExpr<KBoolSort>)
            solver.assert((pred implies !e).asAxiom() as KExpr<KBoolSort>)

            val prede = pred.expr
            val res = solver.checkWithAssumptions(listOf(prede.asExpr(ctx) as KExpr<KBoolSort>)) == KSolverStatus.UNSAT
            solver.close()
            res
        }

        assertTrue(checkExprIn(c eq a, cond eq a))
        assertTrue(checkExprIn(c eq b, cond eq b))
        assertFalse(checkExprIn(c eq a, cond eq z))
    }

    @Test
    fun testLogic() {
        val ctx = KContext()

        val checkExpr = { expr: Bool_ ->
            val solver = solverManager.createSolver(ctx, KZ3Solver::class)
            solver.assert(expr.axiom as KExpr<KBoolSort>)
            solver.assert(ctx.mkNot(expr.expr as KExpr<KBoolSort>))
            val res = solver.check() == KSolverStatus.UNSAT
            solver.close()
            res
        }

        val `true` = Bool_.makeConst(ctx, true)
        val `false` = Bool_.makeConst(ctx, false)

        assertTrue(checkExpr(!(`true` and `false`)))
        assertTrue(checkExpr(`true` or `false`))
        assertTrue(checkExpr(!(`true` eq `false`)))
        assertTrue(checkExpr(`true` neq `false`))

        val a = Word_.makeConst(ctx, 0xFF)
        val b = Word_.makeConst(ctx, 0xFF)
        assertTrue(checkExpr(a eq b))

        val d = Long_.makeConst(ctx, 0xFF)
        val e = Word_.makeConst(ctx, 0xFF)
        assertTrue(checkExpr(d eq e))
    }

    @Test
    fun testIncremental() {
        val x = term { generate(KexInt) }
        val y = term { generate(KexInt) }
        val z = term { generate(KexInt) }
        val state = basic {
            state { x equality (y + z) }
            state { y equality (2 * z) }
        }
        val paths = mutableListOf(
            PredicateQuery(basic {
                path { (x eq (3 * z)) equality true }
            }),
            PredicateQuery(basic {
                path { (x neq (3 * z)) equality true }
            }),
            PredicateQuery(basic {
                path { (x eq 0) equality true }
            }),
            PredicateQuery(basic {
                path { (x gt 0) equality true }
            }),
            PredicateQuery(basic {
                path { (x lt 0) equality true }
            }),
        )

        val solver = KSMTSolver(
            ExecutionContext(
                ClassManager(),
                this.javaClass.classLoader,
                StubRandomizer(),
                emptyList()
            )
        )

        val results = solver.isSatisfiable(state, paths)
        assertIs<Result.SatResult>(results[0])
        assertIs<Result.UnsatResult>(results[1])
        assertIs<Result.SatResult>(results[2])
        assertIs<Result.SatResult>(results[3])
        assertIs<Result.SatResult>(results[4])
    }

    @Test
    fun testSoftConstraints() {
        val x = term { generate(KexInt) }
        val y = term { generate(KexInt) }
        val z = term { generate(KexInt) }
        val state = basic {
            state { x equality (y + z) }
            state { y equality (2 * z) }
        }
        val paths = mutableListOf(
            PredicateQuery(
                basic {
                    path { (x eq (3 * z)) equality true }
                },
                persistentListOf(
                    path { (y gt 0) equality true },
                    path { (z gt 0) equality true },
                    path { (x gt 0) equality true }
                )
            ),
            PredicateQuery(
                basic {
                    path { (x neq (3 * z)) equality true }
                },
                persistentListOf(
                    path { (y gt 0) equality true },
                    path { (z gt 0) equality true },
                    path { (x gt 0) equality true }
                )
            ),
            PredicateQuery(
                basic {
                    path { (x eq (3 * z)) equality true }
                },
                persistentListOf(
                    path { (y gt 0) equality true },
                    path { (z gt 0) equality true },
                    path { (x lt 0) equality true }
                )
            ),
            PredicateQuery(
                basic {
                    path { (x eq (3 * z)) equality true }
                }, persistentListOf(
                    path { (y gt 0) equality true },
                    path { (y lt 0) equality true },
                )
            ),
        )

        val solver = KSMTSolver(
            ExecutionContext(
                ClassManager(),
                this.javaClass.classLoader,
                StubRandomizer(),
                emptyList()
            )
        )

        val results = solver.isSatisfiable(state, paths)
        assertIs<Result.SatResult>(results[0])
        assertIs<Result.UnsatResult>(results[1])
        assertIs<Result.SatResult>(results[2])
        assertIs<Result.SatResult>(results[3])
    }
}

