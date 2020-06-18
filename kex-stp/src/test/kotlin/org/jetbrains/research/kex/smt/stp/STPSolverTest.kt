package org.jetbrains.research.kex.smt.stp

import com.abdullin.kthelper.assert.unreachable
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.KexTest
import org.junit.Assert.assertFalse
import org.zhekehz.stpjava.BoolExpr
import org.zhekehz.stpjava.QueryResult
import org.zhekehz.stpjava.ValidityChecker
import kotlin.test.Test
import kotlin.test.assertTrue

class STPSolverTest : KexTest() {
    @Test
    fun testRunnable() {
        val ef = STPExprFactory()

        val a = ef.makeInt("a")
        val b = ef.makeInt("b")
        val c = ef.makeInt("c")

        val zero = ef.makeIntConst(0)
        val one = ef.makeIntConst(1)

        val query = c neq zero

        val state = c eq (ef.if_<Int_>(a gt b).then_(zero).else_(one))

        assertTrue { isSAT(ef.ctx, query, state) }

        ef.ctx.destroy()
    }

    @Test
    fun testDefaultMemory() {
        val ef = STPExprFactory()
        val checkExpr = { e: Bool_ -> isSAT(ef.ctx, e) }
        val memory = ef.makeDefaultMemory<Word_>("mem", 0xFF)
        for (i in 0..128) {
            assertTrue(checkExpr(memory[ef.makePtrConst(i)] eq Word_.makeConst(ef.ctx, 0xFF)))
        }
        ef.ctx.destroy()
    }

    @Test
    fun testMergeMemory() {
        val ef = STPExprFactory()

        val default = STPContext(ef, (1 shl 16) + 1, (2 shl 16) + 1)
        val memA = STPContext(ef, (1 shl 16) + 1, (2 shl 16) + 1)
        val memB = STPContext(ef, (1 shl 16) + 1, (2 shl 16) + 1)

        val ptr = ef.makePtr("ptr")
        val a = ef.makeIntConst(0xDEAD)
        val b = ef.makeIntConst(0xBEEF)
        val z = ef.makeIntConst(0xABCD)

        val cond = ef.makeInt("cond")
        val condA = cond eq a
        val condB = cond eq b

        memA.writeMemory(ptr, a, 0)
        memB.writeMemory(ptr, b, 0)

        val merged = STPContext.mergeContexts("merged", default, mapOf(
                condA to memA,
                condB to memB
        ))

        val c = merged.readMemory<Int_>(ptr, 0)

        val checkExprIn = { e: Bool_, `in`: Bool_ ->
            val pred = ef.makeBool("\$CHECK$")
            val ptrll = ef.makePtr("ptrll")
            val state = (ptrll eq c) and (pred implies !e)

            val toCheck = `in` and state
            !isSAT(ef.ctx, toCheck, additional = pred.expr.asBool())
        }

        assertTrue(checkExprIn(c eq a, cond eq a))
        assertFalse(checkExprIn(c eq a, cond eq z))
        assertTrue(checkExprIn(c eq b, cond eq b))
        ef.ctx.destroy()
    }

    @Test
    fun testLogic() {
        val ctx = ValidityChecker()

        val checkExpr = { expr: Bool_ -> !isSAT(ctx, !expr) }

        val `true` = Bool_.makeConst(ctx, true)
        val `false` = Bool_.makeConst(ctx, false)
        val expr = !(`true` and `false`)
        assertTrue(checkExpr(expr))
        assertTrue(checkExpr(`true` or `false`))
        assertTrue(checkExpr(!(`true` eq `false`)))
        assertTrue(checkExpr(`true` neq `false`))

        val a = Word_.makeConst(ctx, 0xFF)
        val b = Word_.makeConst(ctx, 0xFF)
        assertTrue(checkExpr(a eq b))

        val d = Long_.makeConst(ctx, 0xFF)
        val e = Word_.makeConst(ctx, 0xFF)
        assertTrue(checkExpr(d eq e))
        ctx.destroy()
    }


    private fun isSAT(ctx: ValidityChecker,
                      query: Bool_,
                      state: Bool_ = Bool_.makeConst(ctx, true),
                      additional: BoolExpr = BoolExpr.getTrue(ctx)
    ): Boolean {
        val combined = state and query
        val toCheck = combined.expr.asBool().and(combined.axiom.asBool()).and(additional)
        return when (toCheck.not().query()) {
            QueryResult.INVALID -> true
            QueryResult.VALID -> false
            else -> unreachable { log.error("should not happen") }
        }
    }
}

