package org.jetbrains.research.kex.smt

import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kex.util.castTo
import org.jetbrains.research.kex.util.uncheckedCastTo
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.util.defaultHashCode
import kotlin.math.max
import kotlin.math.min

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

    open inner class BitVector : ValueExpr {
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
        fun extract(high: Int, low: Int) =
                BitVector(ctx, engine.extract(ctx, expr, high, low), axiom)

        open fun binary(opcode: SMTEngine.Opcode, other: BitVector): ValueExpr {
            val maxsize = max(getBitsize(), other.getBitsize())
            val lhv = engine.sext(ctx, maxsize, expr)
            val rhv = engine.sext(ctx, maxsize, other.expr)

            val nexpr = engine.binary(ctx, opcode, lhv, rhv)
            val nax = spliceAxioms(ctx, axiom, other.axiom)
            return ValueExpr(ctx, nexpr, nax)
        }

        infix fun eq(other: BitVector) = binary(SMTEngine.Opcode.EQ, other)

        infix fun neq(other: BitVector) = binary(SMTEngine.Opcode.NEQ, other)
        infix fun add(other: BitVector) = binary(SMTEngine.Opcode.ADD, other)
        infix fun add(other: Int) =
                binary(SMTEngine.Opcode.ADD, BitVector(ctx, engine.makeNumericConst(ctx, engine.getBVSort(ctx, getBitsize()), other.toLong())))

        infix fun sub(other: BitVector) = binary(SMTEngine.Opcode.SUB, other)
        infix fun sub(other: Int) =
                binary(SMTEngine.Opcode.SUB, BitVector(ctx, engine.makeNumericConst(ctx, engine.getBVSort(ctx, getBitsize()), other.toLong())))

        infix fun mul(other: BitVector) = binary(SMTEngine.Opcode.MUL, other)
        infix fun mul(other: Int) =
                binary(SMTEngine.Opcode.MUL, BitVector(ctx, engine.makeNumericConst(ctx, engine.getBVSort(ctx, getBitsize()), other.toLong())))

        infix fun divide(other: BitVector) = binary(SMTEngine.Opcode.DIV, other)
        infix fun divide(other: Int) =
                binary(SMTEngine.Opcode.DIV, BitVector(ctx, engine.makeNumericConst(ctx, engine.getBVSort(ctx, getBitsize()), other.toLong())))

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
        operator fun plus(other: Int) = add(other)
        operator fun minus(other: BitVector) = sub(other)
        operator fun minus(other: Int) = sub(other)
        operator fun times(other: BitVector) = mul(other)
        operator fun times(other: Int) = mul(other)
        operator fun div(other: BitVector) = divide(other)
        operator fun div(other: Int) = divide(other)
        operator fun rem(other: BitVector) = mod(other)
    }

    inner class BV64 : BitVector {
        constructor(ctx: Context_t, expr: Expr_t) : super(ctx, expr) {
            assert(engine.isBV(ctx, expr)) { log.error("BV created from non-bv expr") }
            assert(engine.bvBitsize(ctx, getSort()) == 64) { log.error("BV64 created from non-bv64 expr") }
        }

        constructor(ctx: Context_t, expr: Expr_t, axiom: Expr_t) : super(ctx, expr, axiom) {
            assert(engine.isBV(ctx, expr)) { log.error("BV created from non-bv expr") }
            assert(engine.bvBitsize(ctx, getSort()) == 64) { log.error("BV64 created from non-bv64 expr") }
        }

        constructor(other: ValueExpr) : super(other) {
            assert(engine.isBV(ctx, expr)) { log.error("BV created from non-bv expr") }
            assert(engine.bvBitsize(ctx, getSort()) == 64) { log.error("BV64 created from non-bv64 expr") }
        }

        override fun binary(opcode: SMTEngine.Opcode, other: BitVector): ValueExpr {
            return if (other is BV64) {
                ValueExpr(ctx,
                        engine.binary(ctx, opcode, expr, other.expr),
                        spliceAxioms(ctx, axiom, other.axiom))
            } else super.binary(opcode, other)
        }
    }

    inner class BV32 : BitVector {
        constructor(ctx: Context_t, expr: Expr_t) : super(ctx, expr) {
            assert(engine.isBV(ctx, expr)) { log.error("BV created from non-bv expr") }
            assert(engine.bvBitsize(ctx, getSort()) == 32) { log.error("BV32 created from non-bv32 expr") }
        }

        constructor(ctx: Context_t, expr: Expr_t, axiom: Expr_t) : super(ctx, expr, axiom) {
            assert(engine.isBV(ctx, expr)) { log.error("BV created from non-bv expr") }
            assert(engine.bvBitsize(ctx, getSort()) == 32) { log.error("BV32 created from non-bv32 expr") }
        }

        constructor(other: ValueExpr) : super(other) {
            assert(engine.isBV(ctx, expr)) { log.error("BV created from non-bv expr") }
            assert(engine.bvBitsize(ctx, getSort()) == 32) { log.error("BV32 created from non-bv32 expr") }
        }

        override fun binary(opcode: SMTEngine.Opcode, other: BitVector): ValueExpr {
            return if (other is BV32) {
                ValueExpr(ctx,
                        engine.binary(ctx, opcode, expr, other.expr),
                        spliceAxioms(ctx, axiom, other.axiom))
            } else super.binary(opcode, other)
        }
    }

    inner class Real : ValueExpr {
        constructor(ctx: Context_t, expr: Expr_t) : super(ctx, expr) {
            assert(engine.isFP(ctx, expr)) { log.error("FP created from non-fp expr") }
        }

        constructor(ctx: Context_t, expr: Expr_t, axiom: Expr_t) : super(ctx, expr, axiom) {
            assert(engine.isFP(ctx, expr)) { log.error("FP created from non-fp expr") }
        }

        constructor(other: ValueExpr) : super(other) {
            assert(engine.isFP(ctx, expr)) { log.error("FP created from non-fp expr") }
        }

        override fun withAxiom(ax: ValueExpr) =
                Real(ctx, expr, spliceAxioms(ctx, axiom, ax.expr, ax.axiom))

        fun getEBitsize() = engine.fpEBitsize(ctx, getSort())
        fun getSBitsize() = engine.fpSBitsize(ctx, getSort())

        fun binary(opcode: SMTEngine.Opcode, other: Real): ValueExpr {
            val nexpr = engine.binary(ctx, opcode, expr, other.expr)
            val nax = spliceAxioms(ctx, axiom, other.axiom)
            return ValueExpr(ctx, nexpr, nax)
        }

        infix fun eq(other: Real) = binary(SMTEngine.Opcode.EQ, other)

        infix fun neq(other: Real) = binary(SMTEngine.Opcode.NEQ, other)
        infix fun add(other: Real) = binary(SMTEngine.Opcode.ADD, other)
        infix fun sub(other: Real) = binary(SMTEngine.Opcode.SUB, other)
        infix fun mul(other: Real) = binary(SMTEngine.Opcode.MUL, other)
        infix fun divide(other: Real) = binary(SMTEngine.Opcode.DIV, other)
        infix fun gt(other: Real) = binary(SMTEngine.Opcode.GT, other)
        infix fun ge(other: Real) = binary(SMTEngine.Opcode.GE, other)
        infix fun lt(other: Real) = binary(SMTEngine.Opcode.LT, other)
        infix fun le(other: Real) = binary(SMTEngine.Opcode.LE, other)

        operator fun plus(other: Real) = add(other)
        operator fun minus(other: Real) = sub(other)
        operator fun times(other: Real) = mul(other)
        operator fun div(other: Real) = divide(other)
    }

    inner class SMTArray<Element : ValueExpr, Index : ValueExpr> : ValueExpr {
        constructor(ctx: Context_t, expr: Expr_t) : super(ctx, expr) {
            assert(engine.isArray(ctx, expr)) { log.error("Array created from non-array expr") }
        }

        constructor(ctx: Context_t, expr: Expr_t, axiom: Expr_t) : super(ctx, expr, axiom) {
            assert(engine.isArray(ctx, expr)) { log.error("Array created from non-array expr") }
        }

        constructor(other: ValueExpr) : super(other) {
            assert(engine.isArray(ctx, expr)) { log.error("Array created from non-array expr") }
        }

        fun load(index: Index) =
                ValueExpr(ctx, engine.load(ctx, expr, index.expr))

        fun store(index: Index, value: Element): SMTArray<Element, Index> =
                SMTArray(ctx, engine.store(ctx, expr, index.expr, value.expr))

        fun store(cases: List<Pair<Index, Element>>): SMTArray<Element, Index> {
            val base: Expr_t = cases.fold(expr) { expr, pair -> engine.store(ctx, expr, pair.first.expr, pair.second.expr) }
            return SMTArray(ctx, base)
        }
    }

    fun <Element : ValueExpr, Index : ValueExpr> merge(default: SMTArray<Element, Index>,
                                                       cases: List<Pair<Bool, SMTArray<Element, Index>>>): SMTArray<Element, Index> {
        return cases.fold(default) { acc, pair ->
            val ctx = pair.first.ctx
            SMTArray(ctx, engine.ite(ctx, pair.first.expr, acc.expr, pair.second.expr), spliceAxioms(ctx, acc.axiom, pair.second.axiom))
        }
    }

    inner class Memory<in Index : BitVector, Byte : BitVector>(val byteSize: Int, val inner: SMTArray<Byte, Index>) {
        val ctx = inner.ctx

        fun load(index: Index, elementSize: Int): BitVector {
            val bytes = (0..((elementSize - 1) / byteSize)).map {
                inner.load((index + it).uncheckedCastTo())
            }
            var expr = bytes.first().expr
            var axiom = bytes.first().axiom
            bytes.drop(1).forEach {
                expr = engine.binary(ctx, SMTEngine.Opcode.CONCAT, expr, it.expr)
                axiom = spliceAxioms(ctx, axiom, it.axiom)
            }
            return BitVector(ctx, expr, axiom)
        }

        fun store(index: Index, element: BitVector): Memory<Index, Byte> {
            val elementSize = element.getBitsize()
            var start = 0
            val cases = mutableListOf<Pair<Index, Byte>>()
            while (start < elementSize) {
                val hi = min(start + byteSize - 1, elementSize - 1)
                cases.add((index + start).uncheckedCastTo<Index>() to element.extract(hi, start).uncheckedCastTo())
                start += byteSize
            }
            return Memory(byteSize, inner.store(cases))
        }

        fun store(index: Index, element: ValueExpr) = store(index, element.castTo())
    }

    fun <Index : BitVector, Byte : BitVector> merge(default: Memory<Index, Byte>,
                                                    cases: List<Pair<Bool, Memory<Index, Byte>>>): Memory<Index, Byte> {
        val inners = cases.map { it.first to it.second.inner }
        return Memory(default.byteSize, merge(default.inner, inners))
    }

    fun ValueExpr.convert(sort: Sort_t) = when {
        engine.isBoolSort(ctx, sort) -> toBool()
        engine.isBVSort(ctx, sort) -> toBV(sort)
        engine.isFPSort(ctx, sort) -> toFloat(sort)
        else -> unreachable { log.error("Trying to convert value to unknown sort") }
    }

    fun ValueExpr.toBool(): Bool {
        val newExpr = when {
            engine.isBool(ctx, expr) -> expr
            engine.isBV(ctx, expr) -> engine.bv2bool(ctx, expr)
            else -> unreachable { log.debug("Unexpected SMT expr type in cast") }
        }
        return Bool(ctx, newExpr, axiom)
    }

    fun ValueExpr.toBV(sort: Sort_t): BitVector {
        val newExpr = when {
            engine.isBool(ctx, expr) -> engine.bool2bv(ctx, expr, sort)
            engine.isBV(ctx, expr) -> engine.bv2bv(ctx, expr, sort)
            else -> unreachable { log.debug("Unexpected SMT expr type in cast") }
        }
        return BitVector(ctx, newExpr, axiom)
    }

    fun ValueExpr.toFloat(sort: Sort_t): BitVector {
        val newExpr = when {
            engine.isBV(ctx, expr) -> engine.bv2float(ctx, expr, sort)
            engine.isFP(ctx, expr) -> engine.float2float(ctx, expr, sort)
            else -> unreachable { log.debug("Unexpected SMT expr type in cast") }
        }
        return BitVector(ctx, newExpr, axiom)
    }
}