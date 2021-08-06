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

        val state = c eq (ef.if_<Int_>(a gt b).then(zero).`else`(one))

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

        val memory = ef.makeDefaultMemory<Word_>("mem", ef.makeIntConst(0xFF))
        for (i in 0..128) {
            assertTrue(checkExpr(memory[ef.makePtrConst(i)] eq Word_.makeConst(ef.ctx, 0xFF)))
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

        memA.writeWordMemory(ptr, 0, a)
        memB.writeWordMemory(ptr, 0, b)

        val merged = Z3Context.mergeContexts(
            "merged", default, mapOf(
                condA to memA,
                condB to memB
            )
        )

        val c = merged.readWordMemory(ptr, 0)

        val checkExprIn = { e: Bool_, `in`: Dynamic_ ->
            val solver = ef.ctx.mkSolver()
            solver.add(`in`.asAxiom() as BoolExpr)

            val pred = ef.makeBool("\$CHECK$")
            val ptrll = ef.makePtr("ptrll")
            solver.add((ptrll eq c).asAxiom() as BoolExpr)
            solver.add((pred implies !e).asAxiom() as BoolExpr)

            val prede = pred.expr
            val res = solver.check(prede as BoolExpr)
            res == Status.UNSATISFIABLE
        }

        assertTrue(checkExprIn(c eq a, cond eq a))
        assertTrue(checkExprIn(c eq b, cond eq b))
        assertFalse(checkExprIn(c eq a, cond eq z))
    }

    @Test
    fun testLogic() {
        val ctx = Context()
        Z3Engine.initialize()

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

        val a = Word_.makeConst(ctx, 0xFF)
        val b = Word_.makeConst(ctx, 0xFF)
        assertTrue(checkExpr(a eq b))

        val d = Long_.makeConst(ctx, 0xFF)
        val e = Word_.makeConst(ctx, 0xFF)
        assertTrue(checkExpr(d eq e))
    }

    @Test
    fun testString() {
        val ctx = Context()
        Z3Engine.initialize()

        val checkExpr = { expr: Bool_ ->
            val solver = ctx.mkSolver()
            solver.add(expr.axiom as BoolExpr)
            solver.add(ctx.mkNot(expr.expr as BoolExpr))
            solver.check() == Status.UNSATISFIABLE
        }

        val Int__ = { value: Int -> Int_.makeConst(ctx, value) }

        val alphabet = "abcdefghijklmnopqrstuvwxyz"
        val abc = "abc"
        val ghi = "ghi"
        val xyz = "xyz"
        val digits = "0123456789"
        val int = "239"

        val alphabetStr = String_.makeConst(ctx, alphabet)
        val abcStr = String_.makeConst(ctx, abc)
        val ghiStr = String_.makeConst(ctx, ghi)
        val xyzStr = String_.makeConst(ctx, xyz)
        val digitStr = String_.makeConst(ctx, digits)
        val intStr = String_.makeConst(ctx, int)

        assertFalse(checkExpr(alphabetStr.contains(digitStr)))
        assertTrue(checkExpr(alphabetStr.contains(ghiStr)))
        assertTrue(checkExpr(alphabetStr.substring(Int__(0), Int__(3)) eq abcStr))
        assertTrue(checkExpr(alphabetStr.indexOf(ghiStr, Int__(0)) eq Int__(6)))
        assertTrue(
            checkExpr(
                alphabetStr.substring(Int__(3), Int__(8)) eq String_.makeConst(ctx, alphabet.substring(3, 11))
            )
        )
        assertTrue(
            checkExpr(
                alphabetStr.startsWith(abcStr)
            )
        )
        assertFalse(
            checkExpr(
                alphabetStr.startsWith(ghiStr)
            )
        )
        assertTrue(
            checkExpr(
                alphabetStr.endsWith(xyzStr)
            )
        )
        assertFalse(
            checkExpr(
                alphabetStr.endsWith(ghiStr)
            )
        )

        assertTrue(
            checkExpr(
                String_.parseInt(ctx, intStr) eq Int__(239)
            )
        )
        assertTrue(
            checkExpr(
                String_.parseInt(ctx, alphabetStr) eq Int__(-1)
            )
        )
    }

    @Test
    fun testForAll() {
        val ef = Z3ExprFactory()
        val ctx = ef.ctx
        Z3Engine.initialize()

        val checkExpr = { expr: Bool_ ->
            val solver = ctx.mkSolver()
            solver.add(expr.axiom as BoolExpr)
            solver.add(ctx.mkNot(expr.expr as BoolExpr))
            solver.check() == Status.UNSATISFIABLE
        }

        var array = ef.makeDefaultMemory("default", ef.makeStringConst("ttt"))
        assertTrue(checkExpr(
            ef.forAll(
                { listOf(ef.makeIntConst(0)) },
                {
                    val index = Int_.forceCast(it[0])
                    if_ { (ef.makeIntConst(0) le index) and (index lt ef.makeIntConst(10)) }
                        .then_ {
                            array.load(index).startsWith(ef.makeStringConst("ttt"))
                        }.else_ {
                            ef.makeTrue()
                        }
                }
            )
        ))

        array = array.store(ef.makeIntConst(11), ef.makeStringConst("bbb"))
        assertTrue(checkExpr(
            ef.forAll(
                { listOf(ef.makeIntConst(0)) },
                {
                    val index = Int_.forceCast(it[0])
                    if_ { (ef.makeIntConst(0) le index) and (index lt ef.makeIntConst(10)) }
                        .then_ {
                            array.load(index).startsWith(ef.makeStringConst("ttt"))
                        }.else_ {
                            ef.makeTrue()
                        }
                }
            )
        ))

        array = array.store(ef.makeIntConst(3), ef.makeStringConst("vvv"))
        assertFalse(checkExpr(
            ef.forAll(
                { listOf(ef.makeIntConst(0)) },
                {
                    val index = Int_.forceCast(it[0])
                    if_ { (ef.makeIntConst(0) le index) and (index lt ef.makeIntConst(10)) }
                        .then_ {
                            array.load(index).startsWith(ef.makeStringConst("ttt"))
                        }.else_ {
                            ef.makeTrue()
                        }
                }
            )
        ))


        var intArray = ef.makeDefaultMemory<Int_>("default", ef.makeIntConst(17))
        assertTrue(checkExpr(
            ef.forAll(
                { listOf(ef.makeIntConst(0)) },
                {
                    val index = Int_.forceCast(it[0])
                    if_ { (ef.makeIntConst(0) le index) and (index lt ef.makeIntConst(10)) }
                        .then_ {
                            intArray.load(index) eq ef.makeIntConst(17)
                        }.else_ {
                            ef.makeTrue()
                        }
                }
            )
        ))

        intArray = intArray.store(ef.makeIntConst(11), ef.makeIntConst(3))
        assertTrue(checkExpr(
            ef.forAll(
                { listOf(ef.makeIntConst(0)) },
                {
                    val index = Int_.forceCast(it[0])
                    if_ { (ef.makeIntConst(0) le index) and (index lt ef.makeIntConst(10)) }
                        .then_ {
                            intArray.load(index) eq ef.makeIntConst(17)
                        }.else_ {
                            ef.makeTrue()
                        }
                }
            )
        ))

        intArray = intArray.store(ef.makeIntConst(3), ef.makeIntConst(42))
        assertFalse(checkExpr(
            ef.forAll(
                { listOf(ef.makeIntConst(0)) },
                {
                    val index = Int_.forceCast(it[0])
                    if_ { (ef.makeIntConst(0) le index) and (index lt ef.makeIntConst(10)) }
                        .then_ {
                            intArray.load(index) eq ef.makeIntConst(17)
                        }.else_ {
                            ef.makeTrue()
                        }
                }
            )
        ))
    }

    @Test
    fun testZ3Lambda() {
        val ef = Z3ExprFactory()
        val ctx = ef.ctx

    }
}