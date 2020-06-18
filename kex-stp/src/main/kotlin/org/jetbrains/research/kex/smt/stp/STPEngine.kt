package org.jetbrains.research.kex.smt.stp

import com.abdullin.kthelper.assert.unreachable
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.smt.SMTEngine

import org.zhekehz.stpjava.*
import java.text.Normalizer
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object STPEngine : SMTEngine<ValidityChecker, Expr, Sort, FunctionExpr, Unit>() {
    private var freshVariablesNumber = 0

    override fun makeBound(ctx: ValidityChecker, size: Int, sort: Sort): Expr =
            BitVectorExpr.fromInt(ctx, 1, 1);

    override fun getSort(ctx: ValidityChecker, expr: Expr): Sort = expr.sort

    override fun getBoolSort(ctx: ValidityChecker): Sort = BoolSort()

    override fun getBVSort(ctx: ValidityChecker, size: Int): Sort = BitVectorSort(size)

    override fun getFloatSort(ctx: ValidityChecker): Sort = BitVectorSort(32)

    override fun getDoubleSort(ctx: ValidityChecker): Sort = BitVectorSort(64)

    override fun getArraySort(ctx: ValidityChecker, domain: Sort, range: Sort): Sort =
            ArraySort(domain.asBitVectorSort().width, range.asBitVectorSort().width)

    override fun isBoolSort(ctx: ValidityChecker, sort: Sort): Boolean = sort.isBool

    override fun isBVSort(ctx: ValidityChecker, sort: Sort): Boolean = sort.isBitVector

    override fun isFloatSort(ctx: ValidityChecker, sort: Sort): Boolean =
            sort.isBitVector && sort.asBitVectorSort().width == 32

    override fun isDoubleSort(ctx: ValidityChecker, sort: Sort): Boolean =
            sort.isBitVector && sort.asBitVectorSort().width == 64

    override fun isArraySort(ctx: ValidityChecker, sort: Sort): Boolean = sort.isArray

    override fun bvBitsize(ctx: ValidityChecker, sort: Sort): Int = sort.asBitVectorSort().width

    override fun floatEBitsize(ctx: ValidityChecker, sort: Sort): Int = 10

    override fun floatSBitsize(ctx: ValidityChecker, sort: Sort): Int = 10

    override fun bv2bool(ctx: ValidityChecker, expr: Expr): Expr = expr.asBool()

    override fun bv2float(ctx: ValidityChecker, expr: Expr, sort: Sort): Expr = bv2bv(ctx, expr, sort)

    override fun float2bv(ctx: ValidityChecker, expr: Expr, sort: Sort): Expr = bv2bv(ctx, expr, sort)

    override fun float2float(ctx: ValidityChecker, expr: Expr, sort: Sort): Expr = bv2bv(ctx, expr, sort)

    override fun hash(ctx: ValidityChecker, expr: Expr): Int = expr.hashCode()

    override fun name(ctx: ValidityChecker, expr: Expr): String = expr.toString()

    override fun toString(ctx: ValidityChecker, expr: Expr) = expr.toString()

    override fun simplify(ctx: ValidityChecker, expr: Expr): Expr = expr.simplify()

    override fun equality(ctx: ValidityChecker, lhv: Expr, rhv: Expr): Boolean = lhv == rhv

    override fun makeVar(ctx: ValidityChecker, sort: Sort, name: String, fresh: Boolean): Expr {
        var name_ = name + if (fresh) "_fresh_var_" + freshVariablesNumber++ else ""
        name_ = Normalizer.normalize(name_, Normalizer.Form.NFD)
                .filter { it.isLetterOrDigit() || it == '_' }  // STP limitation

        return when {
            sort.isArray -> ArrayExpr(ctx, name_, sort.asArraySort().indexWidth, sort.asArraySort().elementWidth)
            sort.isBitVector -> BitVectorExpr(ctx, name_, sort.asBitVectorSort().width)
            sort.isBool -> BoolExpr(ctx, name_)
            else -> unreachable { log.error("Unexpected sort to create a variable") }
        }
    }

    override fun makeBooleanConst(ctx: ValidityChecker, value: Boolean): Expr =
            if (value) BoolExpr.getTrue(ctx) else BoolExpr.getFalse(ctx)

    override fun makeIntConst(ctx: ValidityChecker, value: Short): Expr =
            BitVectorExpr.fromInt(ctx, WORD, value.toInt())

    override fun makeIntConst(ctx: ValidityChecker, value: Int): Expr =
            BitVectorExpr.fromInt(ctx, WORD, value)

    override fun makeLongConst(ctx: ValidityChecker, value: Long): Expr =
            BitVectorExpr.fromLong(ctx, DWORD, value)

    override fun makeNumericConst(ctx: ValidityChecker, sort: Sort, value: Long): Expr =
            BitVectorExpr.fromLong(ctx, DWORD, value)

    override fun makeFloatConst(ctx: ValidityChecker, value: Float): Expr {
        val intValue = value.roundToInt()
        return makeIntConst(ctx, intValue)
    }

    override fun makeDoubleConst(ctx: ValidityChecker, value: Double): Expr {
        val longValue = value.roundToLong()
        return makeLongConst(ctx, longValue)
    }

    override fun makeFunction(ctx: ValidityChecker, name: String, retSort: Sort, args: List<Sort>): FunctionExpr {
        val argsSize = args.map { it.asBitVectorSort().width }
        return FunctionExpr(ctx, name, argsSize.toIntArray(), retSort.asBitVectorSort().width)
    }

    override fun negate(ctx: ValidityChecker, expr: Expr): Expr {
        return when {
            expr.sort.isBool -> expr.asBool().not()
            expr.sort.isBitVector -> expr.asBitVector().minus()
            else -> unreachable { log.error("Unimplemented operation negate") }
        }
    }

    override fun binary(ctx: ValidityChecker, opcode: Opcode, lhv: Expr, rhv: Expr): Expr = when (opcode) {
        Opcode.EQ -> eq(lhv, rhv)

        Opcode.NEQ -> neq(lhv, rhv)

        Opcode.ADD -> when {
            lhv.sort.isBitVector && lhv.sort == rhv.sort -> add(lhv.asBitVector(), rhv.asBitVector())
            else -> unreachable { log.error("Unexpected add arguments: $lhv and $rhv") }
        }

        Opcode.SUB -> when {
            lhv.sort.isBitVector && lhv.sort == rhv.sort -> sub(lhv.asBitVector(), rhv.asBitVector())
            else -> unreachable { log.error("Unexpected sub arguments: $lhv and $rhv") }
        }

        Opcode.MUL -> when {
            lhv.sort.isBitVector && lhv.sort == rhv.sort -> mul(lhv.asBitVector(), rhv.asBitVector())
            else -> unreachable { log.error("Unexpected mul arguments: $lhv and $rhv") }
        }

        Opcode.DIVIDE -> when {
            lhv.sort.isBitVector && lhv.sort == rhv.sort -> sdiv(lhv.asBitVector(), rhv.asBitVector())
            else -> unreachable { log.error("Unexpected divide arguments: $lhv and $rhv") }
        }

        Opcode.MOD -> when {
            lhv.sort.isBitVector && lhv.sort == rhv.sort -> smod(lhv.asBitVector(), rhv.asBitVector())
            else -> unreachable { log.error("Unexpected mod arguments: $lhv and $rhv") }
        }

        Opcode.GT -> when {
            lhv.sort.isBitVector && lhv.sort == rhv.sort -> gt(lhv.asBitVector(), rhv.asBitVector())
            else -> unreachable { log.error("Unexpected gt arguments: $lhv and $rhv") }
        }

        Opcode.GE -> when {
            lhv.sort.isBitVector && lhv.sort == rhv.sort -> ge(lhv.asBitVector(), rhv.asBitVector())
            else -> unreachable { log.error("Unexpected ge arguments: $lhv and $rhv") }
        }

        Opcode.LT -> when {
            lhv.sort.isBitVector && lhv.sort == rhv.sort -> lt(lhv.asBitVector(), rhv.asBitVector())
            else -> unreachable { log.error("Unexpected lt arguments: $lhv and $rhv") }
        }

        Opcode.LE -> when {
            lhv.sort.isBitVector && lhv.sort == rhv.sort -> le(lhv.asBitVector(), rhv.asBitVector())
            else -> unreachable { log.error("Unexpected le arguments: $lhv and $rhv") }
        }

        Opcode.SHL -> when {
            lhv.sort.isBitVector && rhv.sort.isBitVector -> shl(lhv.asBitVector(), rhv.asBitVector())
            else -> unreachable { log.error("Unexpected shl arguments: $lhv and $rhv") }
        }

        Opcode.SHR -> when {
            lhv.sort.isBitVector && rhv.sort.isBitVector -> lshr(lhv.asBitVector(), rhv.asBitVector())
            else -> unreachable { log.error("Unexpected shr arguments: $lhv and $rhv") }
        }

        Opcode.ASHR -> when {
            lhv.sort.isBitVector && rhv.sort.isBitVector -> ashr(lhv.asBitVector(), rhv.asBitVector())
            else -> unreachable { log.error("Unexpected ashr arguments: $lhv and $rhv") }
        }

        Opcode.AND -> when {
            lhv.sort.isBool && rhv.sort.isBool -> and(lhv.asBool(), rhv.asBool())
            lhv.sort.isBitVector && lhv.sort == rhv.sort -> and(lhv.asBitVector(), rhv.asBitVector())
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }

        Opcode.OR -> when {
            lhv.sort.isBool && rhv.sort.isBool -> or(lhv.asBool(), rhv.asBool())
            lhv.sort.isBitVector && lhv.sort == rhv.sort -> or(lhv.asBitVector(), rhv.asBitVector())
            else -> unreachable { log.error("Unexpected or arguments: $lhv and $rhv") }
        }

        Opcode.XOR -> when {
            lhv.sort.isBool && rhv.sort.isBool -> xor(lhv.asBool(), rhv.asBool())
            lhv.sort.isBitVector && lhv.sort == rhv.sort -> xor(lhv.asBitVector(), rhv.asBitVector())
            else -> unreachable { log.error("Unexpected xor arguments: $lhv and $rhv") }
        }

        Opcode.IMPLIES -> implies(lhv.asBool(), rhv.asBool())

        Opcode.IFF -> iff(lhv.asBool(), rhv.asBool())

        Opcode.CONCAT -> concat(lhv.asBitVector(), rhv.asBitVector())
    }

    private fun concat(lhv: BitVectorExpr, rhv: BitVectorExpr): Expr = lhv.concat(rhv)

    private fun iff(lhv: BoolExpr, rhv: BoolExpr): Expr = lhv.iff(rhv)

    private fun implies(lhv: BoolExpr, rhv: BoolExpr): Expr = lhv.implies(rhv)

    private fun xor(lhv: BitVectorExpr, rhv: BitVectorExpr): Expr = lhv.xor(rhv)

    private fun xor(lhv: BoolExpr, rhv: BoolExpr): Expr = lhv.xor(rhv)

    private fun or(lhv: BitVectorExpr, rhv: BitVectorExpr): Expr = lhv.or(rhv)

    private fun or(lhv: BoolExpr, rhv: BoolExpr): Expr = lhv.or(rhv)

    private fun and(lhv: BitVectorExpr, rhv: BitVectorExpr): Expr = lhv.and(rhv)

    private fun and(lhv: BoolExpr, rhv: BoolExpr): Expr = lhv.and(rhv)

    private fun ashr(lhv: BitVectorExpr, rhv: BitVectorExpr): Expr = lhv.signedRightShift(rhv, lhv.width)

    private fun lshr(lhv: BitVectorExpr, rhv: BitVectorExpr): Expr = lhv.rightShift(rhv, lhv.width)

    private fun shl(lhv: BitVectorExpr, rhv: BitVectorExpr): Expr = lhv.leftShift(rhv, lhv.width)

    private fun le(lhv: BitVectorExpr, rhv: BitVectorExpr): Expr = lhv.signedLe(rhv)

    private fun lt(lhv: BitVectorExpr, rhv: BitVectorExpr): Expr = lhv.signedLt(rhv)

    private fun ge(lhv: BitVectorExpr, rhv: BitVectorExpr): Expr = lhv.signedGe(rhv)

    private fun gt(lhv: BitVectorExpr, rhv: BitVectorExpr): Expr = lhv.signedGt(rhv)

    private fun smod(lhv: BitVectorExpr, rhv: BitVectorExpr): Expr = lhv.signedMod(rhv)

    private fun sdiv(lhv: BitVectorExpr, rhv: BitVectorExpr): Expr = lhv.signedDiv(rhv)

    private fun mul(lhv: BitVectorExpr, rhv: BitVectorExpr): Expr = lhv.mult(rhv)

    private fun sub(lhv: BitVectorExpr, rhv: BitVectorExpr): Expr = lhv.minus(rhv)

    private fun add(lhv: BitVectorExpr, rhv: BitVectorExpr): Expr = lhv.plus(rhv)

    private fun neq(lhv: Expr, rhv: Expr): Expr = lhv.equiv(rhv).not()

    private fun eq(lhv: Expr, rhv: Expr): Expr = lhv.equiv(rhv)

    private fun bv2bv(ctx: ValidityChecker, expr: Expr, castSize: Int): BitVectorExpr {
        val curSize = expr.sort.asBitVectorSort().width
        return when {
            curSize == castSize -> expr
            curSize < castSize -> sext(ctx, castSize, expr)
            else -> extract(ctx, expr, high = castSize - 1, low = 0)
        }.asBitVector()
    }

    override fun conjunction(ctx: ValidityChecker, vararg exprs: Expr): Expr {
        return BoolExpr.andAll(ctx, exprs.map { it.asBool() }.toTypedArray())
    }

    override fun conjunction(ctx: ValidityChecker, exprs: Collection<Expr>): Expr {
        return BoolExpr.andAll(ctx, exprs.map { it.asBool() }.toTypedArray())
    }

    override fun zext(ctx: ValidityChecker, n: Int, expr: Expr): Expr = expr.asBitVector().zeroExtend(n)

    override fun sext(ctx: ValidityChecker, n: Int, expr: Expr): Expr {
        val bv = expr.asBitVector()
        return when (bv.width) {
            1 -> zext(ctx, n, expr)
            else -> expr.asBitVector().signExtend(n)
        }
    }

    override fun load(ctx: ValidityChecker, array: Expr, index: Expr): Expr =
            array.asArray().read(bv2bv(ctx, index, array.sort.asArraySort().indexWidth))

    override fun store(ctx: ValidityChecker, array: Expr, index: Expr, value: Expr): Expr =
            array.asArray().write(bv2bv(ctx, index, array.sort.asArraySort().indexWidth), value.asBitVector())

    override fun ite(ctx: ValidityChecker, cond: Expr, lhv: Expr, rhv: Expr): Expr =
            cond.asBool().ifThenElse(lhv, rhv)

    override fun extract(ctx: ValidityChecker, bv: Expr, high: Int, low: Int): Expr =
            bv.asBitVector().extract(high, low)

    override fun forAll(ctx: ValidityChecker, sorts: List<Sort>, body: (List<Expr>) -> Expr): Expr =
            makeIntConst(ctx, 1) //BitVectorExpr.constBitvec(ctx, "1")

    override fun forAll(ctx: ValidityChecker,
                        sorts: List<Sort>,
                        body: (List<Expr>) -> Expr,
                        patternGenerator: (List<Expr>) -> List<Unit>): Expr =
            makeIntConst(ctx, 1) //BitVectorExpr.constBitvec(ctx, "1")

    override fun makePattern(ctx: ValidityChecker, expr: Expr): Unit {}

    override fun bool2bv(ctx: ValidityChecker, expr: Expr, sort: Sort): Expr = expr.asBitVector()

    override fun bv2bv(ctx: ValidityChecker, expr: Expr, sort: Sort): Expr =
            bv2bv(ctx, expr, sort.asBitVectorSort().width)

    override fun makeConstArray(ctx: ValidityChecker, sort: Sort, expr: Expr): Expr =
            ConstArrayExpr(ctx, sort.asBitVectorSort().width, expr.asBitVector())

    override fun apply(ctx: ValidityChecker, f: FunctionExpr, args: List<Expr>): Expr =
            f.apply(*args.map { it.asBitVector() }.toTypedArray())
}

