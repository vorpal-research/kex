package org.jetbrains.research.kex.smt.z3

import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import com.microsoft.z3.*
import org.jetbrains.research.kex.smt.SMTEngine

object Z3Engine : SMTEngine<Context, Expr, Sort, FuncDecl, Pattern>() {
    private var trueExpr: Expr? = null
    private var falseExpr: Expr? = null
    private val bvSortCache = mutableMapOf<Int, Sort>()
    private val bv32Sort get() = bvSortCache[32]
    private val bv64Sort get() = bvSortCache[64]
    private var array32to32Sort: Sort? = null
    private var array32to64Sort: Sort? = null
    private var array64to64Sort: Sort? = null

    override fun initialize() {
        trueExpr = null
        falseExpr = null
        array32to32Sort = null
        array32to64Sort = null
        array64to64Sort = null
        bvSortCache.clear()
    }

    override fun makeBound(ctx: Context, size: Int, sort: Sort): Expr = ctx.mkBound(size, sort)
    override fun makePattern(ctx: Context, expr: Expr): Pattern = ctx.mkPattern(expr)

    override fun getSort(ctx: Context, expr: Expr): Sort = expr.sort
    override fun getBoolSort(ctx: Context): Sort = ctx.boolSort
    override fun getBVSort(ctx: Context, size: Int): Sort = bvSortCache.getOrPut(size) { ctx.mkBitVecSort(size) }
    override fun getFloatSort(ctx: Context): Sort = ctx.mkFPSort32()
    override fun getDoubleSort(ctx: Context): Sort = ctx.mkFPSort64()
    override fun getArraySort(ctx: Context, domain: Sort, range: Sort): Sort = when {
        domain === bv32Sort && range === bv32Sort -> {
            if (array32to32Sort == null) {
                array32to32Sort = ctx.mkArraySort(bv32Sort, bv32Sort)
            }
            array32to32Sort!!
        }
        domain === bv32Sort && range === bv64Sort -> {
            if (array32to64Sort == null) {
                array32to64Sort = ctx.mkArraySort(bv32Sort, bv64Sort)
            }
            array32to64Sort!!
        }
        domain === bv64Sort && range === bv64Sort -> {
            if (array64to64Sort == null) {
                array64to64Sort = ctx.mkArraySort(bv64Sort, bv64Sort)
            }
            array64to64Sort!!
        }
        else -> ctx.mkArraySort(domain, range)
    }

    override fun isBoolSort(ctx: Context, sort: Sort): Boolean = sort is BoolSort
    override fun isBVSort(ctx: Context, sort: Sort): Boolean = sort is BitVecSort
    override fun isArraySort(ctx: Context, sort: Sort): Boolean = sort is ArraySort
    override fun isFloatSort(ctx: Context, sort: Sort): Boolean = sort is FPSort && sort == ctx.mkFPSort32()
    override fun isDoubleSort(ctx: Context, sort: Sort): Boolean = sort is FPSort && sort == ctx.mkFPSort64()

    override fun bvBitsize(ctx: Context, sort: Sort): Int = (sort as BitVecSort).size
    override fun floatEBitsize(ctx: Context, sort: Sort): Int = (sort as FPSort).eBits
    override fun floatSBitsize(ctx: Context, sort: Sort): Int = (sort as FPSort).sBits

    override fun bool2bv(ctx: Context, expr: Expr, sort: Sort): Expr =
            ite(ctx, expr, makeNumericConst(ctx, sort, 1), makeNumericConst(ctx, sort, 0))

    override fun bv2bool(ctx: Context, expr: Expr): Expr =
            binary(ctx, Opcode.NEQ, expr, makeNumericConst(ctx, getSort(ctx, expr), 0))

    override fun bv2bv(ctx: Context, expr: Expr, sort: Sort): Expr {
        val curSize = (getSort(ctx, expr) as BitVecSort).size
        val castSize = (sort as BitVecSort).size
        return when {
            curSize == castSize -> expr
            curSize < castSize -> sext(ctx, castSize, expr)
            else -> extract(ctx, expr, high = castSize - 1, low = 0)
        }
    }

    override fun bv2float(ctx: Context, expr: Expr, sort: Sort): Expr =
            ctx.mkFPToFP(ctx.mkFPRTZ(), expr as BitVecExpr, sort as FPSort, true)

    override fun float2bv(ctx: Context, expr: Expr, sort: Sort): Expr =
            ctx.mkFPToBV(ctx.mkFPRTZ(), expr as FPExpr, (sort as BitVecSort).size, true)

    override fun IEEEbv2float(ctx: Context, expr: Expr, sort: Sort): Expr =
            ctx.mkFPToFP(expr as BitVecExpr, sort as FPSort)

    override fun float2IEEEbv(ctx: Context, expr: Expr, sort: Sort): Expr =
            ctx.mkFPToIEEEBV(expr as FPExpr)

    override fun float2float(ctx: Context, expr: Expr, sort: Sort): Expr =
            ctx.mkFPToFP(ctx.mkFPRTZ(), expr as FPExpr, sort as FPSort)

    override fun hash(ctx: Context, expr: Expr) = expr.hashCode()
    override fun name(ctx: Context, expr: Expr) = expr.toString()
    override fun toString(ctx: Context, expr: Expr) = expr.toString()
    override fun simplify(ctx: Context, expr: Expr): Expr = expr.simplify()
    override fun equality(ctx: Context, lhv: Expr, rhv: Expr) = lhv == rhv

    override fun makeVar(ctx: Context, sort: Sort, name: String, fresh: Boolean): Expr = when {
        fresh -> ctx.mkFreshConst(name, sort)
        else -> ctx.mkConst(name, sort)
    }

    fun makeTrue(ctx: Context) = trueExpr ?: run {
        trueExpr = ctx.mkTrue()
        trueExpr!!
    }

    fun makeFalse(ctx: Context) = falseExpr ?: run {
        falseExpr = ctx.mkFalse()
        falseExpr!!
    }

    override fun makeBooleanConst(ctx: Context, value: Boolean): Expr = when {
        value -> makeTrue(ctx)
        else -> makeFalse(ctx)
    }

    override fun makeIntConst(ctx: Context, value: Short): Expr = ctx.mkNumeral(value.toInt(), getBVSort(ctx, WORD))
    override fun makeIntConst(ctx: Context, value: Int): Expr = ctx.mkNumeral(value, getBVSort(ctx, WORD))
    override fun makeLongConst(ctx: Context, value: Long): Expr = ctx.mkNumeral(value, getBVSort(ctx, DWORD))
    override fun makeNumericConst(ctx: Context, sort: Sort, value: Long): Expr = ctx.mkNumeral(value, sort)
    override fun makeFloatConst(ctx: Context, value: Float): Expr = ctx.mkFPNumeral(value, getFloatSort(ctx) as FPSort)
    override fun makeDoubleConst(ctx: Context, value: Double): Expr = ctx.mkFPNumeral(value, getDoubleSort(ctx) as FPSort)

    override fun makeConstArray(ctx: Context, sort: Sort, expr: Expr): Expr = ctx.mkConstArray(sort, expr)

    override fun makeFunction(ctx: Context, name: String, retSort: Sort, args: List<Sort>): FuncDecl =
            ctx.mkFuncDecl(name, args.toTypedArray(), retSort)

    override fun apply(ctx: Context, f: FuncDecl, args: List<Expr>): Expr = f.apply(*args.toTypedArray())

    override fun negate(ctx: Context, expr: Expr): Expr = when (expr) {
        is BoolExpr -> ctx.mkNot(expr)
        is BitVecExpr -> ctx.mkBVNeg(expr)
        is FPExpr -> ctx.mkFPNeg(expr)
        else -> unreachable { log.error("Unimplemented operation negate") }
    }

    override fun binary(ctx: Context, opcode: Opcode, lhv: Expr, rhv: Expr): Expr = when (opcode) {
        Opcode.EQ -> eq(ctx, lhv, rhv)
        Opcode.NEQ -> neq(ctx, lhv, rhv)
        Opcode.ADD -> when {
            lhv is BitVecExpr && rhv is BitVecExpr -> add(ctx, lhv, rhv)
            lhv is FPExpr && rhv is FPExpr -> add(ctx, lhv, rhv)
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.SUB -> when {
            lhv is BitVecExpr && rhv is BitVecExpr -> sub(ctx, lhv, rhv)
            lhv is FPExpr && rhv is FPExpr -> sub(ctx, lhv, rhv)
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.MUL -> when {
            lhv is BitVecExpr && rhv is BitVecExpr -> mul(ctx, lhv, rhv)
            lhv is FPExpr && rhv is FPExpr -> mul(ctx, lhv, rhv)
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.DIVIDE -> when {
            lhv is BitVecExpr && rhv is BitVecExpr -> sdiv(ctx, lhv, rhv)
            lhv is FPExpr && rhv is FPExpr -> sdiv(ctx, lhv, rhv)
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.MOD -> when {
            lhv is BitVecExpr && rhv is BitVecExpr -> smod(ctx, lhv, rhv)
            lhv is FPExpr && rhv is FPExpr -> fmod(ctx, lhv, rhv)
            else -> unreachable { log.error("Unexpected mod arguments: $lhv and $rhv") }
        }
        Opcode.GT -> when {
            lhv is BitVecExpr && rhv is BitVecExpr -> gt(ctx, lhv, rhv)
            lhv is FPExpr && rhv is FPExpr -> gt(ctx, lhv, rhv)
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.GE -> when {
            lhv is BitVecExpr && rhv is BitVecExpr -> ge(ctx, lhv, rhv)
            lhv is FPExpr && rhv is FPExpr -> ge(ctx, lhv, rhv)
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.LT -> when {
            lhv is BitVecExpr && rhv is BitVecExpr -> lt(ctx, lhv, rhv)
            lhv is FPExpr && rhv is FPExpr -> lt(ctx, lhv, rhv)
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.LE -> when {
            lhv is BitVecExpr && rhv is BitVecExpr -> le(ctx, lhv, rhv)
            lhv is FPExpr && rhv is FPExpr -> le(ctx, lhv, rhv)
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.SHL -> shl(ctx, lhv as BitVecExpr, rhv as BitVecExpr)
        Opcode.SHR -> lshr(ctx, lhv as BitVecExpr, rhv as BitVecExpr)
        Opcode.ASHR -> ashr(ctx, lhv as BitVecExpr, rhv as BitVecExpr)
        Opcode.AND -> when {
            lhv is BoolExpr && rhv is BoolExpr -> and(ctx, lhv, rhv)
            lhv is BitVecExpr && rhv is BitVecExpr -> and(ctx, lhv, rhv)
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.OR -> when {
            lhv is BoolExpr && rhv is BoolExpr -> or(ctx, lhv, rhv)
            lhv is BitVecExpr && rhv is BitVecExpr -> or(ctx, lhv, rhv)
            else -> unreachable { log.error("Unexpected or arguments: $lhv or $rhv") }
        }
        Opcode.XOR -> when {
            lhv is BoolExpr && rhv is BoolExpr -> xor(ctx, lhv, rhv)
            lhv is BitVecExpr && rhv is BitVecExpr -> xor(ctx, lhv, rhv)
            else -> unreachable { log.error("Unexpected xor arguments: $lhv xor $rhv") }
        }
        Opcode.IMPLIES -> implies(ctx, lhv as BoolExpr, rhv as BoolExpr)
        Opcode.IFF -> iff(ctx, lhv as BoolExpr, rhv as BoolExpr)
        Opcode.CONCAT -> concat(ctx, lhv as BitVecExpr, rhv as BitVecExpr)
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
    private fun fmod(ctx: Context, lhv: FPExpr, rhv: FPExpr) = ctx.mkFPRem(lhv, rhv)

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
        val boolExprs = exprs.map { it as BoolExpr }.toTypedArray()
        return ctx.mkAnd(*boolExprs)
    }

    override fun conjunction(ctx: Context, exprs: Collection<Expr>): Expr {
        val boolExprs = exprs.map { it as BoolExpr }.toTypedArray()
        return ctx.mkAnd(*boolExprs)
    }

    override fun sext(ctx: Context, n: Int, expr: Expr): Expr {
        val exprBitsize = bvBitsize(ctx, getSort(ctx, expr))
        return if (exprBitsize < n) ctx.mkSignExt(n - exprBitsize, expr as BitVecExpr) else expr
    }

    override fun zext(ctx: Context, n: Int, expr: Expr): Expr {
        val exprBitsize = bvBitsize(ctx, getSort(ctx, expr))
        return if (exprBitsize < n) ctx.mkZeroExt(n - exprBitsize, expr as BitVecExpr) else expr
    }

    override fun load(ctx: Context, array: Expr, index: Expr): Expr = ctx.mkSelect(array as ArrayExpr, index)
    override fun store(ctx: Context, array: Expr, index: Expr, value: Expr): Expr = ctx.mkStore(array as ArrayExpr, index, value)

    override fun ite(ctx: Context, cond: Expr, lhv: Expr, rhv: Expr): Expr = ctx.mkITE(cond as BoolExpr, lhv, rhv)

    override fun extract(ctx: Context, bv: Expr, high: Int, low: Int): Expr = ctx.mkExtract(high, low, bv as BitVecExpr)

    override fun forAll(ctx: Context, sorts: List<Sort>, body: (List<Expr>) -> Expr): Expr {
        val numArgs = sorts.lastIndex

        val bounds = sorts.asSequence().withIndex().map { (index, sort) -> makeBound(ctx, index, sort) }.toList()
        val realBody = body(bounds)
        val names = (0..numArgs).map { "forall_bound_${numArgs - it - 1}" }.map { ctx.mkSymbol(it) }.toTypedArray()
        val sortsRaw = sorts.toTypedArray()
        return ctx.mkForall(sortsRaw, names, realBody, 0, arrayOf(), arrayOf(), null, null)
    }

    override fun forAll(ctx: Context, sorts: List<Sort>, body: (List<Expr>) -> Expr, patternGenerator: (List<Expr>) -> List<Pattern>): Expr {
        val numArgs = sorts.lastIndex

        val bounds = sorts.asSequence().withIndex().map { (index, sort) -> makeBound(ctx, index, sort) }.toList()
        val realBody = body(bounds)
        val names = (0..numArgs).map { "forall_bound_${numArgs - it - 1}" }.map { ctx.mkSymbol(it) }.toTypedArray()
        val sortsRaw = sorts.toTypedArray()

        val patterns = patternGenerator(bounds).toTypedArray()
        return ctx.mkForall(sortsRaw, names, realBody, 0, patterns, arrayOf(), null, null)
    }
}