package org.jetbrains.research.kex.smt.z3

import com.microsoft.z3.*
import org.jetbrains.research.kex.smt.SMTEngine
import org.jetbrains.research.kex.util.castTo
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable

object Z3Engine : SMTEngine<Context, Expr, Sort, FuncDecl, Pattern>() {
    override fun makeBound(ctx: Context, size: Int, sort: Sort): Expr = ctx.mkBound(size, sort)
    override fun makePattern(ctx: Context, expr: Expr): Pattern = ctx.mkPattern(expr)

    override fun getSort(ctx: Context, expr: Expr): Sort = expr.sort
    override fun getBoolSort(ctx: Context): Sort = ctx.boolSort
    override fun getBVSort(ctx: Context, size: Int): Sort = ctx.mkBitVecSort(size)
    override fun getFloatSort(ctx: Context): Sort = ctx.mkFPSortSingle()
    override fun getDoubleSort(ctx: Context): Sort = ctx.mkFPSortDouble()
    override fun getArraySort(ctx: Context, domain: Sort, range: Sort): Sort = ctx.mkArraySort(domain, range)

    override fun isBoolSort(ctx: Context, sort: Sort): Boolean = sort is BoolSort
    override fun isBVSort(ctx: Context, sort: Sort): Boolean = sort is BitVecSort
    override fun isArraySort(ctx: Context, sort: Sort): Boolean = sort is ArraySort
    override fun isFloatSort(ctx: Context, sort: Sort): Boolean = sort is FPSort && sort == ctx.mkFPSortSingle()
    override fun isDoubleSort(ctx: Context, sort: Sort): Boolean = sort is FPSort && sort == ctx.mkFPSortDouble()

    override fun bvBitsize(ctx: Context, sort: Sort): Int = sort.castTo<BitVecSort>().size
    override fun floatEBitsize(ctx: Context, sort: Sort): Int = sort.castTo<FPSort>().eBits
    override fun floatSBitsize(ctx: Context, sort: Sort): Int = sort.castTo<FPSort>().sBits

    override fun bool2bv(ctx: Context, expr: Expr, sort: Sort): Expr =
            ite(ctx, expr, makeNumericConst(ctx, sort, 1), makeNumericConst(ctx, sort, 0))

    override fun bv2bool(ctx: Context, expr: Expr): Expr =
            binary(ctx, SMTEngine.Opcode.NEQ, expr, makeNumericConst(ctx, getSort(ctx, expr), 0))

    override fun bv2bv(ctx: Context, expr: Expr, sort: Sort): Expr {
        val curSize = getSort(ctx, expr).castTo<BitVecSort>().size
        val castSize = sort.castTo<BitVecSort>().size
        return when {
            curSize == castSize -> expr
            curSize < castSize -> sext(ctx, castSize, expr)
            else -> extract(ctx, expr, high = castSize - 1, low = 0)
        }
    }

    override fun bv2float(ctx: Context, expr: Expr, sort: Sort): Expr =
            ctx.mkFPToFP(ctx.mkFPRTZ(), expr.castTo(), sort.castTo(), true)

    override fun float2bv(ctx: Context, expr: Expr, sort: Sort): Expr =
            ctx.mkFPToBV(ctx.mkFPRTZ(), expr.castTo(), sort.castTo<BitVecSort>().size, true)

    override fun float2float(ctx: Context, expr: Expr, sort: Sort): Expr =
            ctx.mkFPToFP(sort.castTo(), ctx.mkFPRTZ(), expr.castTo())

    override fun hash(ctx: Context, expr: Expr) = expr.hashCode()
    override fun name(ctx: Context, expr: Expr) = expr.toString()
    override fun toString(ctx: Context, expr: Expr) = expr.toString()
    override fun simplify(ctx: Context, expr: Expr): Expr = expr.simplify()
    override fun equality(ctx: Context, lhv: Expr, rhv: Expr) = lhv == rhv

    override fun makeVar(ctx: Context, sort: Sort, name: String, fresh: Boolean): Expr = when {
        fresh -> ctx.mkFreshConst(name, sort)
        else -> ctx.mkConst(name, sort)
    }

    override fun makeBooleanConst(ctx: Context, value: Boolean): Expr = ctx.mkBool(value)

    override fun makeIntConst(ctx: Context, value: Short): Expr = ctx.mkNumeral(value.toInt(), getBVSort(ctx, WORD))
    override fun makeIntConst(ctx: Context, value: Int): Expr = ctx.mkNumeral(value, getBVSort(ctx, WORD))
    override fun makeLongConst(ctx: Context, value: Long): Expr = ctx.mkNumeral(value, getBVSort(ctx, DWORD))
    override fun makeNumericConst(ctx: Context, sort: Sort, value: Long): Expr = ctx.mkNumeral(value, sort)
    override fun makeFloatConst(ctx: Context, value: Float): Expr = ctx.mkFPNumeral(value, getFloatSort(ctx).castTo())
    override fun makeDoubleConst(ctx: Context, value: Double): Expr = ctx.mkFPNumeral(value, getDoubleSort(ctx).castTo())

    override fun makeConstArray(ctx: Context, sort: Sort, expr: Expr): Expr = ctx.mkConstArray(sort, expr)

    override fun makeFunction(ctx: Context, name: String, retSort: Sort, args: List<Sort>): FuncDecl =
            ctx.mkFuncDecl(name, args.toTypedArray(), retSort)

    override fun apply(ctx: Context, f: FuncDecl, args: List<Expr>): Expr = f.apply(*args.toTypedArray())

    override fun negate(ctx: Context, expr: Expr): Expr = when (expr) {
        is BoolExpr -> ctx.mkNot(expr)
        is BitVecExpr -> ctx.mkBVNeg(expr)
        else -> unreachable { log.error("Unimplemented operation negate") }
    }

    override fun binary(ctx: Context, opcode: Opcode, lhv: Expr, rhv: Expr): Expr = when (opcode) {
        Opcode.EQ -> eq(ctx, lhv, rhv)
        Opcode.NEQ -> neq(ctx, lhv, rhv)
        Opcode.ADD -> when {
            lhv is BitVecExpr && rhv is BitVecExpr -> add(ctx, lhv.castTo<BitVecExpr>(), rhv.castTo())
            lhv is FPExpr && rhv is FPExpr -> add(ctx, lhv.castTo<FPExpr>(), rhv.castTo())
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.SUB -> when {
            lhv is BitVecExpr && rhv is BitVecExpr -> sub(ctx, lhv.castTo<BitVecExpr>(), rhv.castTo())
            lhv is FPExpr && rhv is FPExpr -> sub(ctx, lhv.castTo<FPExpr>(), rhv.castTo())
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.MUL -> when {
            lhv is BitVecExpr && rhv is BitVecExpr -> mul(ctx, lhv.castTo<BitVecExpr>(), rhv.castTo())
            lhv is FPExpr && rhv is FPExpr -> mul(ctx, lhv.castTo<FPExpr>(), rhv.castTo())
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.DIVIDE -> when {
            lhv is BitVecExpr && rhv is BitVecExpr -> sdiv(ctx, lhv.castTo<BitVecExpr>(), rhv.castTo())
            lhv is FPExpr && rhv is FPExpr -> sdiv(ctx, lhv.castTo<FPExpr>(), rhv.castTo())
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.MOD -> smod(ctx, lhv.castTo(), rhv.castTo())
        Opcode.GT -> when {
            lhv is BitVecExpr && rhv is BitVecExpr -> gt(ctx, lhv.castTo<BitVecExpr>(), rhv.castTo())
            lhv is FPExpr && rhv is FPExpr -> gt(ctx, lhv.castTo<FPExpr>(), rhv.castTo())
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.GE -> when {
            lhv is BitVecExpr && rhv is BitVecExpr -> ge(ctx, lhv.castTo<BitVecExpr>(), rhv.castTo())
            lhv is FPExpr && rhv is FPExpr -> ge(ctx, lhv.castTo<FPExpr>(), rhv.castTo())
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.LT -> when {
            lhv is BitVecExpr && rhv is BitVecExpr -> lt(ctx, lhv.castTo<BitVecExpr>(), rhv.castTo())
            lhv is FPExpr && rhv is FPExpr -> lt(ctx, lhv.castTo<FPExpr>(), rhv.castTo())
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.LE -> when {
            lhv is BitVecExpr && rhv is BitVecExpr -> le(ctx, lhv.castTo<BitVecExpr>(), rhv.castTo())
            lhv is FPExpr && rhv is FPExpr -> le(ctx, lhv.castTo<FPExpr>(), rhv.castTo())
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.SHL -> shl(ctx, lhv.castTo(), rhv.castTo())
        Opcode.SHR -> lshr(ctx, lhv.castTo(), rhv.castTo())
        Opcode.ASHR -> ashr(ctx, lhv.castTo(), rhv.castTo())
        Opcode.AND -> when {
            lhv is BoolExpr && rhv is BoolExpr -> and(ctx, lhv.castTo<BoolExpr>(), rhv.castTo())
            lhv is BitVecExpr && rhv is BitVecExpr -> and(ctx, lhv.castTo<BitVecExpr>(), rhv.castTo())
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.OR -> when {
            lhv is BoolExpr && rhv is BoolExpr -> or(ctx, lhv.castTo<BoolExpr>(), rhv.castTo())
            lhv is BitVecExpr && rhv is BitVecExpr -> or(ctx, lhv.castTo<BitVecExpr>(), rhv.castTo())
            else -> unreachable { log.error("Unexpected or arguments: $lhv or $rhv") }
        }
        Opcode.XOR -> when {
            lhv is BoolExpr && rhv is BoolExpr -> xor(ctx, lhv.castTo<BoolExpr>(), rhv.castTo())
            lhv is BitVecExpr && rhv is BitVecExpr -> xor(ctx, lhv.castTo<BitVecExpr>(), rhv.castTo())
            else -> unreachable { log.error("Unexpected xor arguments: $lhv xor $rhv") }
        }
        Opcode.IMPLIES -> implies(ctx, lhv.castTo(), rhv.castTo())
        Opcode.IFF -> iff(ctx, lhv.castTo(), rhv.castTo())
        Opcode.CONCAT -> concat(ctx, lhv.castTo(), rhv.castTo())
    }

    private fun eq(ctx: Context, lhv: Expr, rhv: Expr) = ctx.mkEq(lhv, rhv)
    private fun neq(ctx: Context, lhv: Expr, rhv: Expr) = ctx.mkNot(eq(ctx, lhv, rhv))

    private fun add(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVAdd(lhv, rhv)
    private fun add(ctx: Context, lhv: FPExpr, rhv: FPExpr) = ctx.mkFPAdd(ctx.mkFPRNA(), lhv, rhv)

    private fun sub(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSub(lhv, rhv)
    private fun sub(ctx: Context, lhv: FPExpr, rhv: FPExpr) = ctx.mkFPSub(ctx.mkFPRNA(), lhv, rhv)

    private fun mul(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVMul(lhv, rhv)
    private fun mul(ctx: Context, lhv: FPExpr, rhv: FPExpr) = ctx.mkFPMul(ctx.mkFPRNA(), lhv, rhv)

    private fun sdiv(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSDiv(lhv, rhv)
    private fun sdiv(ctx: Context, lhv: FPExpr, rhv: FPExpr) = ctx.mkFPDiv(ctx.mkFPRNA(), lhv, rhv)

    private fun udiv(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVUDiv(lhv, rhv)
    private fun smod(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSMod(lhv, rhv)
    private fun umod(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVURem(lhv, rhv)

    private fun gt(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSGT(lhv, rhv)
    private fun gt(ctx: Context, lhv: FPExpr, rhv: FPExpr) = ctx.mkFPGt(lhv, rhv)

    private fun ge(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSGE(lhv, rhv)
    private fun ge(ctx: Context, lhv: FPExpr, rhv: FPExpr) = ctx.mkFPGEq(lhv, rhv)

    private fun lt(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSLT(lhv, rhv)
    private fun lt(ctx: Context, lhv: FPExpr, rhv: FPExpr) = ctx.mkFPLt(lhv, rhv)

    private fun le(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSLE(lhv, rhv)
    private fun le(ctx: Context, lhv: FPExpr, rhv: FPExpr) = ctx.mkFPLEq(lhv, rhv)

    private fun shl(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSHL(lhv, rhv)
    private fun lshr(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVLSHR(lhv, rhv)
    private fun ashr(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVASHR(lhv, rhv)

    private fun and(ctx: Context, lhv: BoolExpr, rhv: BoolExpr) = ctx.mkAnd(lhv, rhv)
    private fun or(ctx: Context, lhv: BoolExpr, rhv: BoolExpr) = ctx.mkOr(lhv, rhv)
    private fun xor(ctx: Context, lhv: BoolExpr, rhv: BoolExpr) = ctx.mkXor(lhv, rhv)
    private fun and(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVAND(lhv, rhv)
    private fun or(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVOR(lhv, rhv)
    private fun xor(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVXOR(lhv, rhv)
    private fun implies(ctx: Context, lhv: BoolExpr, rhv: BoolExpr) = ctx.mkImplies(lhv, rhv)
    private fun iff(ctx: Context, lhv: BoolExpr, rhv: BoolExpr) = ctx.mkIff(lhv, rhv)
    private fun concat(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkConcat(lhv, rhv)

    override fun conjunction(ctx: Context, vararg exprs: Expr): Expr {
        val boolExprs = exprs.map { it.castTo<BoolExpr>() }.toTypedArray()
        return ctx.mkAnd(*boolExprs)
    }

    override fun conjunction(ctx: Context, exprs: Collection<Expr>): Expr {
        val boolExprs = exprs.map { it.castTo<BoolExpr>() }.toTypedArray()
        return ctx.mkAnd(*boolExprs)
    }

    override fun sext(ctx: Context, n: Int, expr: Expr): Expr {
        val exprBitsize = bvBitsize(ctx, getSort(ctx, expr))
        return if (exprBitsize < n) ctx.mkSignExt(n - exprBitsize, expr.castTo()) else expr
    }

    override fun zext(ctx: Context, n: Int, expr: Expr): Expr {
        val exprBitsize = bvBitsize(ctx, getSort(ctx, expr))
        return if (exprBitsize < n) ctx.mkZeroExt(n - exprBitsize, expr.castTo()) else expr
    }

    override fun load(ctx: Context, array: Expr, index: Expr): Expr = ctx.mkSelect(array.castTo(), index)
    override fun store(ctx: Context, array: Expr, index: Expr, value: Expr): Expr = ctx.mkStore(array.castTo(), index, value)

    override fun ite(ctx: Context, cond: Expr, lhv: Expr, rhv: Expr): Expr = ctx.mkITE(cond.castTo(), lhv, rhv)

    override fun extract(ctx: Context, bv: Expr, high: Int, low: Int): Expr = ctx.mkExtract(high, low, bv.castTo())

    override fun forAll(ctx: Context, sorts: List<Sort>, body: (List<Expr>) -> Expr): Expr {
        val numArgs = sorts.lastIndex

        val bounds = sorts.withIndex().map { (index, sort) -> makeBound(ctx, index, sort) }
        val realBody = body(bounds)
        val names = (0..numArgs).map { "forall_bound_${numArgs - it - 1}" }.map { ctx.mkSymbol(it) }.toTypedArray()
        val sortsRaw = sorts.toTypedArray()
        return ctx.mkForall(sortsRaw, names, realBody, 0, arrayOf(), arrayOf(), null, null)
    }

    override fun forAll(ctx: Context, sorts: List<Sort>, body: (List<Expr>) -> Expr, patternGenerator: (List<Expr>) -> List<Pattern>): Expr {
        val numArgs = sorts.lastIndex

        val bounds = sorts.withIndex().map { (index, sort) -> makeBound(ctx, index, sort) }
        val realBody = body(bounds)
        val names = (0..numArgs).map { "forall_bound_${numArgs - it - 1}" }.map { ctx.mkSymbol(it) }.toTypedArray()
        val sortsRaw = sorts.toTypedArray()

        val patterns = patternGenerator(bounds).toTypedArray()
        return ctx.mkForall(sortsRaw, names, realBody, 0, patterns, arrayOf(), null, null)
    }
}