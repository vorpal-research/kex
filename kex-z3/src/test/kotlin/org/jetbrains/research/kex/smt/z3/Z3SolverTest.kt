package org.jetbrains.research.kex.smt.z3

import com.microsoft.z3.BoolExpr
import com.microsoft.z3.Context
import com.microsoft.z3.Status
import org.jetbrains.research.kex.KexTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import kotlin.test.Test
import kotlin.test.assertTrue

class Z3SolverTest : KexTest() {
    @Test
    fun testRunnable() {
        val ef = Z3ExprFactory()

        val a = ef.makeInt("a")
        val b = ef.makeInt("b")
        val c = ef.makeInt("c")

        val zero = ef.makeIntConst(0)
        val one = ef.makeIntConst(1)

        val query = c neq zero

        val state = c eq (ef.if_<Int_>(a gt b).then_(zero).else_(one))

        val ctx = ef.ctx
        val solver = ctx.mkSolver()
        solver.add(query.asAxiom() as BoolExpr)
        solver.add(state.asAxiom() as BoolExpr)

        val res = solver.check()
        assertEquals(Status.SATISFIABLE, res)
    }

    @Test
    fun testDefaultMemory() {
        val ef = Z3ExprFactory()
        val checkExpr = { e: Dynamic_ ->
            val solver = ef.ctx.mkSolver()
            solver.add(e.axiom as BoolExpr)
            solver.add(Z3Engine.negate(ef.ctx, e.expr) as BoolExpr)
            solver.check() == Status.UNSATISFIABLE
        }

        val memory = ef.makeDefaultMemory("mem", 0xFF)
        for (i in 0..128) {
            assertTrue(checkExpr(memory[ef.makePtrConst(i)] eq Z3BV32.makeConst(ef.ctx, 0xFF)))
        }
    }

    @Test
    fun testMergeMemory() {
        val ef = Z3ExprFactory()

        val default = Z3Context(ef, (1 shl 16) + 1, (2 shl 16) + 1)
        val memA = Z3Context(ef, (1 shl 16) + 1, (2 shl 16) + 1)
        val memB = Z3Context(ef, (1 shl 16) + 1, (2 shl 16) + 1)

        val ptr = ef.makePtr("ptr")
        val a = ef.makeIntConst(0xDEAD)
        val b = ef.makeIntConst(0xBEEF)
        val z = ef.makeIntConst(0xABCD)

        val cond = ef.makeInt("cond")
        val condA = cond eq a
        val condB = cond eq b

        memA.writeMemory(ptr, a, 0)
        memB.writeMemory(ptr, b, 0)

        val merged = Z3Context.mergeContexts("merged", default, mapOf(
                condA to memA,
                condB to memB
        ))

        val c = merged.readMemory<Int_>(ptr, 0)

        val checkExprIn = { e: Bool_, `in`: Dynamic_ ->
            val solver = ef.ctx.mkSolver()
            solver.add(`in`.asAxiom() as BoolExpr)

            val pred = ef.makeBool("\$CHECK$")
            val ptrll = ef.makePtr("ptrll")
            solver.add((ptrll eq c).asAxiom() as BoolExpr)
            solver.add((pred implies !e).asAxiom() as BoolExpr)

            val prede = pred.expr
            val res = solver.check(prede)
            res == Status.UNSATISFIABLE
        }

        assertTrue(checkExprIn(c eq a, cond eq a))
        assertTrue(checkExprIn(c eq b, cond eq b))
        assertFalse(checkExprIn(c eq a, cond eq z))
    }

    @Test
    fun testLogic() {
        val ctx = Context()

        val checkExpr = { expr: Bool_ ->
            val solver = ctx.mkSolver()
            solver.add(expr.axiom as BoolExpr)
            solver.add(ctx.mkNot(expr.expr as BoolExpr))
            solver.check() == Status.UNSATISFIABLE
        }

        val `true` = Bool_.makeConst(ctx, true)
        val `false` = Bool_.makeConst(ctx, false)

        assertTrue(checkExpr(!(`true` and `false`)))
        assertTrue(checkExpr(`true` or `false`))
        assertTrue(checkExpr(!(`true` eq `false`)))
        assertTrue(checkExpr(`true` neq `false`))

        val a = Byte_.makeConst(ctx, 0xFF)
        val b = Byte_.makeConst(ctx, 0xFF)
        assertTrue(checkExpr(a eq b))

        val d = Long_.makeConst(ctx, 0xFF)
        val e = Byte_.makeConst(ctx, 0xFF)
        assertTrue(checkExpr(d eq e))
    }
}