package org.jetbrains.research.kex.smt

import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.util.defaultHashCode
import kotlin.math.max

class SMTImpl<Context_t : Any, Expr_t : Any, Sort_t : Any, Function_t : Any> : Loggable {
    val engine = SMTEngineProxy<Context_t, Expr_t, Sort_t, Function_t>()

    abstract class SMTExpr

    fun defaultAxiom(ctx: Context_t) = engine.makeBoolConst(ctx, true)

    fun spliceAxioms(ctx: Context_t, lhv: Expr_t, rhv: Expr_t) =
            if (engine.equality(ctx, lhv, defaultAxiom(ctx))) rhv
            else if (engine.equality(ctx, rhv, defaultAxiom(ctx))) lhv
            else engine.conjunction(ctx, lhv, rhv)

    fun spliceAxioms(ctx: Context_t, e0: Expr_t, e1: Expr_t, e2: Expr_t) =
            if (engine.equality(ctx, e0, defaultAxiom(ctx))) spliceAxioms(ctx, e1, e2)
            else if (engine.equality(ctx, e1, defaultAxiom(ctx))) spliceAxioms(ctx, e0, e2)
            else if (engine.equality(ctx, e2, defaultAxiom(ctx))) spliceAxioms(ctx, e0, e1)
            else engine.conjunction(ctx, e0, e1, e2)

    open inner class ValueExpr : SMTExpr {
        val ctx: Context_t
        val expr: Expr_t
        val axiom: Expr_t

        constructor(ctx: Context_t, expr: Expr_t, axiom: Expr_t) : super() {
            this.ctx = ctx
            this.expr = expr
            this.axiom = axiom
        }

        constructor(ctx: Context_t, expr: Expr_t) : super() {
            this.ctx = ctx
            this.expr = expr
            this.axiom = defaultAxiom(ctx)
        }

        constructor(other: ValueExpr) : this(other.ctx, other.expr, other.axiom)

        override fun toString() = engine.toString(ctx, expr)
        override fun hashCode() = defaultHashCode(engine.hash(ctx, expr), engine.hash(ctx, axiom))
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != this.javaClass) return false
            other as SMTImpl<*, *, *, *>.ValueExpr
            @Suppress("UNCHECKED_CAST")
            return engine.equality(ctx, expr, other.expr as Expr_t) and engine.equality(ctx, expr, other.axiom as Expr_t)
        }

        fun name() = engine.name(ctx, expr)
        fun getSort() = engine.getSort(ctx, expr)

        fun asAxiom() = spliceAxioms(ctx, expr, axiom)
        open fun withAxiom(ax: ValueExpr) = ValueExpr(ctx, expr, ax.asAxiom())

        open fun simplify() =
                ValueExpr(ctx, engine.simplify(ctx, expr), engine.simplify(ctx, expr))
    }

    inner class Bool : ValueExpr {
        constructor(ctx: Context_t, expr: Expr_t) : super(ctx, expr) {
            assert(engine.isBool(ctx, expr)) { log.error("Bool created from non-bool expr") }
        }

        constructor(ctx: Context_t, expr: Expr_t, axiom: Expr_t) : super(ctx, expr, axiom) {
            assert(engine.isBool(ctx, expr)) { log.error("Bool created from non-bool expr") }
        }

        constructor(other: ValueExpr) : super(other) {
            assert(engine.isBool(ctx, expr)) { log.error("Bool created from non-bool expr") }
        }

        override fun withAxiom(ax: ValueExpr) =
                Bool(ctx, expr, spliceAxioms(ctx, axiom, ax.expr, ax.axiom))

        fun getBitsize() = 1

        override fun simplify() = Bool(super.simplify())


        infix fun eq(other: Bool) =
                Bool(ctx, engine.binary(ctx, SMTEngine.Opcode.EQ, expr, other.expr), spliceAxioms(ctx, axiom, other.axiom))

        infix fun neq(other: Bool) =
                Bool(ctx, engine.binary(ctx, SMTEngine.Opcode.NEQ, expr, other.expr), spliceAxioms(ctx, axiom, other.axiom))

        operator fun not() = Bool(ctx, engine.negate(ctx, expr), axiom)
        infix fun and(other: Bool) =
                Bool(ctx, engine.binary(ctx, SMTEngine.Opcode.AND, expr, other.expr), spliceAxioms(ctx, axiom, other.axiom))

        infix fun or(other: Bool) =
                Bool(ctx, engine.binary(ctx, SMTEngine.Opcode.OR, expr, other.expr), spliceAxioms(ctx, axiom, other.axiom))

        infix fun xor(other: Bool) =
                Bool(ctx, engine.binary(ctx, SMTEngine.Opcode.XOR, expr, other.expr), spliceAxioms(ctx, axiom, other.axiom))

        infix fun implies(other: Bool) =
                Bool(ctx, engine.binary(ctx, SMTEngine.Opcode.IMPLIES, expr, other.expr), spliceAxioms(ctx, axiom, other.axiom))

        infix fun iff(other: Bool) =
                Bool(ctx, engine.binary(ctx, SMTEngine.Opcode.IFF, expr, other.expr), spliceAxioms(ctx, axiom, other.axiom))
    }

    inner class BitVector : ValueExpr {
        constructor(ctx: Context_t, expr: Expr_t) : super(ctx, expr) {
            assert(engine.isBV(ctx, expr)) { log.error("BV created from non-bv expr") }
        }

        constructor(ctx: Context_t, expr: Expr_t, axiom: Expr_t) : super(ctx, expr, axiom) {
            assert(engine.isBV(ctx, expr)) { log.error("BV created from non-bv expr") }
        }

        constructor(other: ValueExpr) : super(other) {
            assert(engine.isBV(ctx, expr)) { log.error("BV created from non-bv expr") }
        }

        override fun withAxiom(ax: ValueExpr) =
                BitVector(ctx, expr, spliceAxioms(ctx, axiom, ax.expr, ax.axiom))

        fun getBitsize() = engine.bvBitsize(ctx, getSort())

        fun binary(opcode: SMTEngine.Opcode, other: BitVector): ValueExpr {
            val maxsize = max(getBitsize(), other.getBitsize())
            val lhv = engine.sext(ctx, maxsize, expr)
            val rhv = engine.sext(ctx, maxsize, expr)

            val nexpr = engine.binary(ctx, opcode, lhv, rhv)
            val nax = spliceAxioms(ctx, lhv, rhv)
            return ValueExpr(ctx, nexpr, nax)
        }

        infix fun eq(other: BitVector) = binary(SMTEngine.Opcode.EQ, other)

        infix fun neq(other: BitVector) = binary(SMTEngine.Opcode.NEQ, other)
        infix fun add(other: BitVector) = binary(SMTEngine.Opcode.ADD, other)
        infix fun sub(other: BitVector) = binary(SMTEngine.Opcode.SUB, other)
        infix fun mul(other: BitVector) = binary(SMTEngine.Opcode.MUL, other)
        infix fun divide(other: BitVector) = binary(SMTEngine.Opcode.DIV, other)
        infix fun mod(other: BitVector) = binary(SMTEngine.Opcode.MOD, other)
        infix fun gt(other: BitVector) = binary(SMTEngine.Opcode.GT, other)
        infix fun ge(other: BitVector) = binary(SMTEngine.Opcode.GE, other)
        infix fun lt(other: BitVector) = binary(SMTEngine.Opcode.LT, other)
        infix fun le(other: BitVector) = binary(SMTEngine.Opcode.LE, other)
        infix fun shl(other: BitVector) = binary(SMTEngine.Opcode.SHL, other)
        infix fun shr(other: BitVector) = binary(SMTEngine.Opcode.SHR, other)
        infix fun ashr(other: BitVector) = binary(SMTEngine.Opcode.ASHR, other)
        infix fun and(other: BitVector) = binary(SMTEngine.Opcode.AND, other)
        infix fun or(other: BitVector) = binary(SMTEngine.Opcode.OR, other)
        infix fun xor(other: BitVector) = binary(SMTEngine.Opcode.XOR, other)

        operator fun plus(other: BitVector) = add(other)
        operator fun minus(other: BitVector) = sub(other)
        operator fun times(other: BitVector) = mul(other)
        operator fun div(other: BitVector) = divide(other)
        operator fun rem(other: BitVector) = mod(other)
    }

    fun ValueExpr.toBool(): Bool {
        val newExpr = when {
            engine.isBool(ctx, expr) -> expr
            engine.isBV(ctx, expr) -> engine.binary(ctx, SMTEngine.Opcode.NEQ, expr, engine.makeNumericConst(ctx, getSort(), 0))
            else -> unreachable { log.debug("Unexpected SMT expr type in cast") }
        }
        return Bool(ctx, newExpr, axiom)
    }

    fun ValueExpr.toBV(): BitVector {
        val newExpr = when {
            engine.isBool(ctx, expr) -> TODO()
            engine.isBV(ctx, expr) -> expr
            else -> unreachable { log.debug("Unexpected SMT expr type in cast") }
        }
        return BitVector(ctx, newExpr, axiom)
    }

    fun makeBool(ctx: Context_t, value: Boolean) =
            Bool(ctx, engine.makeBoolConst(ctx, value))

    fun makeBoolVar(ctx: Context_t, name: String) =
            Bool(ctx, engine.makeVar(ctx, engine.getBoolSort(ctx), name))

    fun makeBV(ctx: Context_t, value: Short) =
            BitVector(ctx, engine.makeNumericConst(ctx, value))

    fun makeBV(ctx: Context_t, value: Int) =
            BitVector(ctx, engine.makeNumericConst(ctx, value))

    fun makeBV(ctx: Context_t, value: Long) =
            BitVector(ctx, engine.makeNumericConst(ctx, value))

    fun makeBVVar(ctx: Context_t, name: String, bitsize: Int) =
            BitVector(ctx, engine.makeVar(ctx, engine.getBVSort(ctx, bitsize), name))

    fun makeBVVar(ctx: Context_t, name: String, sort: Sort_t) =
            BitVector(ctx, engine.makeVar(ctx, sort, name))

    fun makeIntVar(ctx: Context_t, name: String) =
            BitVector(ctx, engine.makeVar(ctx, engine.getBVSort(ctx, SMTEngine.intWidth), name))

    fun makeShortVar(ctx: Context_t, name: String) =
            BitVector(ctx, engine.makeVar(ctx, engine.getBVSort(ctx, SMTEngine.shortWidth), name))

    fun makeLongVar(ctx: Context_t, name: String) =
            BitVector(ctx, engine.makeVar(ctx, engine.getBVSort(ctx, SMTEngine.longWidth), name))
}