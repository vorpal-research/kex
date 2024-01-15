package org.vorpal.research.kex.smt.z3

import com.microsoft.z3.*
import org.vorpal.research.kex.smt.SMTEngine
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.collection.mapToArray
import org.vorpal.research.kthelper.logging.log
import java.math.BigInteger

@Suppress("UNCHECKED_CAST")
object Z3Engine : SMTEngine<ExtendedContext, Expr<*>, Sort, FuncDecl<*>, Pattern>() {
    override fun makeBound(ctx: ExtendedContext, size: Int, sort: Sort): Expr<*> = ctx.mkBound(size, sort)
    override fun makePattern(ctx: ExtendedContext, expr: Expr<*>): Pattern = ctx.mkPattern(expr)

    override fun getSort(ctx: ExtendedContext, expr: Expr<*>): Sort = expr.sort
    override fun getBoolSort(ctx: ExtendedContext): Sort = ctx.boolSort
    override fun getBVSort(ctx: ExtendedContext, size: Int): Sort = ctx.mkBitVecSort(size)
    override fun getFloatSort(ctx: ExtendedContext): Sort = ctx.mkFPSort32()
    override fun getDoubleSort(ctx: ExtendedContext): Sort = ctx.mkFPSort64()
    override fun getArraySort(ctx: ExtendedContext, domain: Sort, range: Sort): Sort = ctx.mkArraySort(domain, range)

    override fun isBoolSort(ctx: ExtendedContext, sort: Sort): Boolean = sort is BoolSort
    override fun isBVSort(ctx: ExtendedContext, sort: Sort): Boolean = sort is BitVecSort
    override fun isArraySort(ctx: ExtendedContext, sort: Sort): Boolean = sort is ArraySort<*, *>
    override fun isFloatSort(ctx: ExtendedContext, sort: Sort): Boolean = sort is FPSort && sort == ctx.mkFPSort32()
    override fun isDoubleSort(ctx: ExtendedContext, sort: Sort): Boolean = sort is FPSort && sort == ctx.mkFPSort64()

    override fun bvBitSize(ctx: ExtendedContext, sort: Sort): Int = (sort as BitVecSort).size
    override fun floatEBitSize(ctx: ExtendedContext, sort: Sort): Int = (sort as FPSort).eBits
    override fun floatSBitSize(ctx: ExtendedContext, sort: Sort): Int = (sort as FPSort).sBits

    override fun bool2bv(ctx: ExtendedContext, expr: Expr<*>, sort: Sort): Expr<*> =
        ite(ctx, expr, makeNumericConst(ctx, sort, 1), makeNumericConst(ctx, sort, 0))

    override fun bv2bool(ctx: ExtendedContext, expr: Expr<*>): Expr<*> =
        binary(ctx, Opcode.NEQ, expr, makeNumericConst(ctx, getSort(ctx, expr), 0))

    override fun bv2bv(ctx: ExtendedContext, expr: Expr<*>, sort: Sort): Expr<*> {
        val curSize = (getSort(ctx, expr) as BitVecSort).size
        val castSize = (sort as BitVecSort).size
        return when {
            curSize == castSize -> expr
            curSize < castSize -> sext(ctx, castSize, expr)
            else -> extract(ctx, expr, high = castSize - 1, low = 0)
        }
    }

    override fun bv2float(ctx: ExtendedContext, expr: Expr<*>, sort: Sort): Expr<*> =
        ctx.mkFPToFP(ctx.mkFPRTZ(), expr as BitVecExpr, sort as FPSort, true)

    override fun float2bv(ctx: ExtendedContext, expr: Expr<*>, sort: Sort): Expr<*> =
        ctx.mkFPToBV(ctx.mkFPRTZ(), expr as FPExpr, (sort as BitVecSort).size, true)

    override fun bvIEEE2float(ctx: ExtendedContext, expr: Expr<*>, sort: Sort): Expr<*> =
        ctx.mkFPToFP(expr as BitVecExpr, sort as FPSort)

    override fun float2IEEEbv(ctx: ExtendedContext, expr: Expr<*>, sort: Sort): Expr<*> =
        ctx.mkFPToIEEEBV(expr as FPExpr)

    override fun float2float(ctx: ExtendedContext, expr: Expr<*>, sort: Sort): Expr<*> =
        ctx.mkFPToFP(ctx.mkFPRTZ(), expr as FPExpr, sort as FPSort)

    override fun hash(ctx: ExtendedContext, expr: Expr<*>) = expr.hashCode()
    override fun name(ctx: ExtendedContext, expr: Expr<*>) = expr.toString()
    override fun toString(ctx: ExtendedContext, expr: Expr<*>) = expr.toString()
    override fun simplify(ctx: ExtendedContext, expr: Expr<*>): Expr<*> = expr.simplify()
    override fun equality(ctx: ExtendedContext, lhv: Expr<*>, rhv: Expr<*>) = lhv == rhv

    override fun makeVar(ctx: ExtendedContext, sort: Sort, name: String, fresh: Boolean): Expr<*> = when {
        fresh -> ctx.mkFreshConst(name, sort)
        else -> ctx.mkConst(name, sort)
    }

    fun makeTrue(ctx: ExtendedContext) = ctx.mkTrue()
    fun makeFalse(ctx: ExtendedContext) = ctx.mkFalse()

    override fun makeBooleanConst(ctx: ExtendedContext, value: Boolean): Expr<*> = when {
        value -> makeTrue(ctx)
        else -> makeFalse(ctx)
    }

    override fun makeIntConst(ctx: ExtendedContext, value: Short): Expr<*> =
        ctx.mkNumeral(value.toInt(), getBVSort(ctx, WORD))

    override fun makeIntConst(ctx: ExtendedContext, value: Int): Expr<*> = ctx.mkNumeral(value, getBVSort(ctx, WORD))
    override fun makeLongConst(ctx: ExtendedContext, value: Long): Expr<*> = ctx.mkNumeral(value, getBVSort(ctx, DWORD))
    override fun makeNumericConst(ctx: ExtendedContext, sort: Sort, value: Long): Expr<*> = ctx.mkNumeral(value, sort)
    override fun makeFloatConst(ctx: ExtendedContext, value: Float): Expr<*> =
        ctx.mkFPNumeral(value, getFloatSort(ctx) as FPSort)

    override fun makeDoubleConst(ctx: ExtendedContext, value: Double): Expr<*> =
        ctx.mkFPNumeral(value, getDoubleSort(ctx) as FPSort)

    override fun makeConstArray(ctx: ExtendedContext, sort: Sort, expr: Expr<*>): Expr<*> = ctx.mkConstArray(sort, expr)

    override fun makeFunction(ctx: ExtendedContext, name: String, retSort: Sort, args: List<Sort>): FuncDecl<*> =
        ctx.mkFuncDecl(name, args.toTypedArray(), retSort)

    override fun makeBVConst(ctx: ExtendedContext, value: String, radix: Int, width: Int): Expr<*> {
        val bitStr = BigInteger(value, radix).toString(10)
        return ctx.mkNumeral(bitStr, getBVSort(ctx, width))
    }

    override fun apply(ctx: ExtendedContext, f: FuncDecl<*>, args: List<Expr<*>>): Expr<*> =
        f.apply(*args.toTypedArray())

    override fun negate(ctx: ExtendedContext, expr: Expr<*>): Expr<*> = when (expr) {
        is BoolExpr -> ctx.mkNot(expr)
        is BitVecExpr -> ctx.mkBVNeg(expr)
        is FPExpr -> ctx.mkFPNeg(expr)
        else -> unreachable { log.error("Unimplemented operation negate") }
    }

    override fun binary(ctx: ExtendedContext, opcode: Opcode, lhv: Expr<*>, rhv: Expr<*>): Expr<*> = when (opcode) {
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

    private fun eq(ctx: ExtendedContext, lhv: Expr<*>, rhv: Expr<*>) = ctx.mkEq(lhv, rhv)
    private fun neq(ctx: ExtendedContext, lhv: Expr<*>, rhv: Expr<*>) = ctx.mkNot(eq(ctx, lhv, rhv))

    private fun add(ctx: ExtendedContext, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVAdd(lhv, rhv)
    private fun add(ctx: ExtendedContext, lhv: FPExpr, rhv: FPExpr) = ctx.mkFPAdd(ctx.mkFPRNA(), lhv, rhv)

    private fun sub(ctx: ExtendedContext, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSub(lhv, rhv)
    private fun sub(ctx: ExtendedContext, lhv: FPExpr, rhv: FPExpr) = ctx.mkFPSub(ctx.mkFPRNA(), lhv, rhv)

    private fun mul(ctx: ExtendedContext, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVMul(lhv, rhv)
    private fun mul(ctx: ExtendedContext, lhv: FPExpr, rhv: FPExpr) = ctx.mkFPMul(ctx.mkFPRNA(), lhv, rhv)

    private fun sdiv(ctx: ExtendedContext, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSDiv(lhv, rhv)
    private fun sdiv(ctx: ExtendedContext, lhv: FPExpr, rhv: FPExpr) = ctx.mkFPDiv(ctx.mkFPRNA(), lhv, rhv)

    private fun udiv(ctx: ExtendedContext, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVUDiv(lhv, rhv)
    private fun smod(ctx: ExtendedContext, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSMod(lhv, rhv)
    private fun umod(ctx: ExtendedContext, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVURem(lhv, rhv)
    private fun fmod(ctx: ExtendedContext, lhv: FPExpr, rhv: FPExpr) = ctx.mkFPRem(lhv, rhv)

    private fun gt(ctx: ExtendedContext, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSGT(lhv, rhv)
    private fun gt(ctx: ExtendedContext, lhv: FPExpr, rhv: FPExpr) = ctx.mkFPGt(lhv, rhv)

    private fun ge(ctx: ExtendedContext, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSGE(lhv, rhv)
    private fun ge(ctx: ExtendedContext, lhv: FPExpr, rhv: FPExpr) = ctx.mkFPGEq(lhv, rhv)

    private fun lt(ctx: ExtendedContext, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSLT(lhv, rhv)
    private fun lt(ctx: ExtendedContext, lhv: FPExpr, rhv: FPExpr) = ctx.mkFPLt(lhv, rhv)

    private fun le(ctx: ExtendedContext, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSLE(lhv, rhv)
    private fun le(ctx: ExtendedContext, lhv: FPExpr, rhv: FPExpr) = ctx.mkFPLEq(lhv, rhv)

    private fun shl(ctx: ExtendedContext, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSHL(lhv, rhv)
    private fun lshr(ctx: ExtendedContext, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVLSHR(lhv, rhv)
    private fun ashr(ctx: ExtendedContext, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVASHR(lhv, rhv)

    private fun and(ctx: ExtendedContext, lhv: BoolExpr, rhv: BoolExpr) = ctx.mkAnd(lhv, rhv)
    private fun or(ctx: ExtendedContext, lhv: BoolExpr, rhv: BoolExpr) = ctx.mkOr(lhv, rhv)
    private fun xor(ctx: ExtendedContext, lhv: BoolExpr, rhv: BoolExpr) = ctx.mkXor(lhv, rhv)
    private fun and(ctx: ExtendedContext, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVAND(lhv, rhv)
    private fun or(ctx: ExtendedContext, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVOR(lhv, rhv)
    private fun xor(ctx: ExtendedContext, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVXOR(lhv, rhv)
    private fun implies(ctx: ExtendedContext, lhv: BoolExpr, rhv: BoolExpr) = ctx.mkImplies(lhv, rhv)
    private fun iff(ctx: ExtendedContext, lhv: BoolExpr, rhv: BoolExpr) = ctx.mkIff(lhv, rhv)
    private fun concat(ctx: ExtendedContext, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkConcat(lhv, rhv)

    override fun conjunction(ctx: ExtendedContext, vararg exprs: Expr<*>): Expr<*> {
        return ctx.mkAnd(*exprs.mapToArray { it as BoolExpr })
    }

    override fun conjunction(ctx: ExtendedContext, exprs: Collection<Expr<*>>): Expr<*> {
        return ctx.mkAnd(*exprs.mapToArray { it as BoolExpr })
    }

    override fun disjunction(ctx: ExtendedContext, vararg exprs: Expr<*>): Expr<*> {
        return ctx.mkOr(*exprs.mapToArray { it as BoolExpr })
    }

    override fun disjunction(ctx: ExtendedContext, exprs: Collection<Expr<*>>): Expr<*> {
        return ctx.mkOr(*exprs.mapToArray { it as BoolExpr })
    }

    override fun sext(ctx: ExtendedContext, n: Int, expr: Expr<*>): Expr<*> {
        val exprBitSize = bvBitSize(ctx, getSort(ctx, expr))
        return if (exprBitSize < n) ctx.mkSignExt(n - exprBitSize, expr as BitVecExpr) else expr
    }

    override fun zext(ctx: ExtendedContext, n: Int, expr: Expr<*>): Expr<*> {
        val exprBitSize = bvBitSize(ctx, getSort(ctx, expr))
        return if (exprBitSize < n) ctx.mkZeroExt(n - exprBitSize, expr as BitVecExpr) else expr
    }

    fun <T : Sort, R : Sort> ld(ctx: ExtendedContext, array: Expr<*>, index: Expr<T>): Expr<*> =
        ctx.mkSelect(array as ArrayExpr<T, R>, index)

    fun <T : Sort, R : Sort> st(ctx: ExtendedContext, array: Expr<*>, index: Expr<T>, value: Expr<R>): Expr<*> =
        ctx.mkStore(array as ArrayExpr<T, R>, index, value)

    override fun load(ctx: ExtendedContext, array: Expr<*>, index: Expr<*>): Expr<*> =
        ld<Sort, Sort>(ctx, array, index as Expr<Sort>)

    override fun store(ctx: ExtendedContext, array: Expr<*>, index: Expr<*>, value: Expr<*>): Expr<*> =
        st<Sort, Sort>(ctx, array, index as Expr<Sort>, value as Expr<Sort>)

    override fun ite(ctx: ExtendedContext, cond: Expr<*>, lhv: Expr<*>, rhv: Expr<*>): Expr<*> =
        ctx.mkITE(cond as BoolExpr, lhv, rhv)

    override fun extract(ctx: ExtendedContext, bv: Expr<*>, high: Int, low: Int): Expr<*> =
        ctx.mkExtract(high, low, bv as BitVecExpr)

    override fun forAll(ctx: ExtendedContext, sorts: List<Sort>, body: (List<Expr<*>>) -> Expr<*>): Expr<*> {
        val numArgs = sorts.lastIndex
        val bounds = sorts.mapIndexed { index, sort -> makeBound(ctx, index, sort) }
        val realBody = body(bounds)
        val names = (0..numArgs).map { "forall_bound_${numArgs - it}" }.mapToArray { ctx.mkSymbol(it) }
        val sortsRaw = sorts.toTypedArray()
        return ctx.mkForall(sortsRaw, names, realBody as BoolExpr, 0, arrayOf(), arrayOf(), null, null)
    }

    override fun forAll(
        ctx: ExtendedContext,
        sorts: List<Sort>,
        body: (List<Expr<*>>) -> Expr<*>,
        patternGenerator: (List<Expr<*>>) -> List<Pattern>
    ): Expr<*> {
        val numArgs = sorts.lastIndex
        val bounds = sorts.mapIndexed { index, sort -> makeBound(ctx, index, sort) }
        val realBody = body(bounds)
        val names = (0..numArgs).map { "forall_bound_${numArgs - it - 1}" }.mapToArray { ctx.mkSymbol(it) }
        val sortsRaw = sorts.toTypedArray()

        val patterns = patternGenerator(bounds).toTypedArray()
        return ctx.mkForall(sortsRaw, names, realBody as BoolExpr, 0, patterns, arrayOf(), null, null)
    }

    override fun exists(ctx: ExtendedContext, sorts: List<Sort>, body: (List<Expr<*>>) -> Expr<*>): Expr<*> {
        val numArgs = sorts.lastIndex
        val bounds = sorts.mapIndexed { index, sort -> makeBound(ctx, index, sort) }
        val realBody = body(bounds)
        val names = (0..numArgs).map { "exists_bound_${numArgs - it}" }.mapToArray { ctx.mkSymbol(it) }
        val sortsRaw = sorts.toTypedArray()
        return ctx.mkExists(sortsRaw, names, realBody as BoolExpr, 0, arrayOf(), arrayOf(), null, null)
    }

    override fun exists(
        ctx: ExtendedContext,
        sorts: List<Sort>,
        body: (List<Expr<*>>) -> Expr<*>,
        patternGenerator: (List<Expr<*>>) -> List<Pattern>
    ): Expr<*> {
        val numArgs = sorts.lastIndex
        val bounds = sorts.mapIndexed { index, sort -> makeBound(ctx, index, sort) }
        val realBody = body(bounds)
        val names = (0..numArgs).map { "forall_bound_${numArgs - it - 1}" }.mapToArray { ctx.mkSymbol(it) }
        val sortsRaw = sorts.toTypedArray()

        val patterns = patternGenerator(bounds).toTypedArray()
        return ctx.mkExists(sortsRaw, names, realBody as BoolExpr, 0, patterns, arrayOf(), null, null)
    }

    override fun lambda(
        ctx: ExtendedContext,
        elementSort: Sort,
        sorts: List<Sort>, body: (List<Expr<*>>) -> Expr<*>
    ): Expr<*> {
        val numArgs = sorts.lastIndex
        val bounds = sorts.mapIndexed { index, sort -> makeBound(ctx, index, sort) }
        val realBody = body(bounds)
        val names = (0..numArgs).map { "lambda_bound_${numArgs - it}" }.mapToArray { ctx.mkSymbol(it) }
        val sortsRaw = sorts.toTypedArray()
        return ctx.mkLambda(sortsRaw, names, realBody)
    }

    override fun getStringSort(ctx: ExtendedContext): Sort = ctx.stringSort

    override fun isStringSort(ctx: ExtendedContext, sort: Sort): Boolean = sort == ctx.stringSort

    override fun bv2string(ctx: ExtendedContext, expr: Expr<*>): Expr<*> =
        ctx.intToString(ctx.mkBV2Int(expr as BitVecExpr, true))

    override fun float2string(ctx: ExtendedContext, expr: Expr<*>): Expr<*> =
        ctx.intToString(ctx.mkBV2Int(ctx.mkFPToBV(ctx.mkFPRTZ(), expr as Expr<FPSort>, WORD, true), true))

    override fun double2string(ctx: ExtendedContext, expr: Expr<*>): Expr<*> =
        ctx.intToString(ctx.mkBV2Int(ctx.mkFPToBV(ctx.mkFPRTZ(), expr as Expr<FPSort>, DWORD, true), true))

    override fun string2bv(ctx: ExtendedContext, expr: Expr<*>, sort: Sort): Expr<*> =
        ctx.mkInt2BV(getSortBitSize(ctx, sort), ctx.stringToInt(expr as Expr<SeqSort<CharSort>>))

    override fun string2float(ctx: ExtendedContext, expr: Expr<*>): Expr<*> =
        bv2float(ctx, string2bv(ctx, expr, getBVSort(ctx, WORD)), getFloatSort(ctx))

    override fun string2double(ctx: ExtendedContext, expr: Expr<*>): Expr<*> =
        bv2float(ctx, string2bv(ctx, expr, getBVSort(ctx, DWORD)), getDoubleSort(ctx))

    override fun makeStringConst(ctx: ExtendedContext, value: String): Expr<*> = ctx.mkString(value)

    override fun contains(ctx: ExtendedContext, seq: Expr<*>, value: Expr<*>): Expr<*> =
        ctx.mkContains(seq as Expr<SeqSort<BitVecSort>>, value as Expr<SeqSort<BitVecSort>>)

    override fun nths(ctx: ExtendedContext, seq: Expr<*>, index: Expr<*>): Expr<*> {
//        val char2Int = ctx.mkFuncDecl("char.to_int", ctx.mkBitVecSort(), ctx.mkIntSort())
//        val nth = ctx.MkNth(seq as Expr<SeqSort<BitVecSort>>, ctx.mkBV2Int(index as Expr<BitVecSort>, true))
//        val nthInt = ctx.mkApp(char2Int, nth)
//        return ctx.mkInt2BV(WORD, nthInt)
        return ctx.mkNth(seq as Expr<SeqSort<BitVecSort>>, ctx.mkBV2Int(index as Expr<BitVecSort>, true))
    }

    override fun length(ctx: ExtendedContext, seq: Expr<*>): Expr<*> =
        ctx.mkInt2BV(WORD, ctx.mkLength(seq as Expr<SeqSort<CharSort>>))

    override fun prefixOf(ctx: ExtendedContext, seq: Expr<*>, prefix: Expr<*>): Expr<*> =
        ctx.mkPrefixOf(prefix as Expr<SeqSort<CharSort>>, seq as Expr<SeqSort<CharSort>>)

    override fun suffixOf(ctx: ExtendedContext, seq: Expr<*>, suffix: Expr<*>): Expr<*> =
        ctx.mkSuffixOf(suffix as Expr<SeqSort<CharSort>>, seq as Expr<SeqSort<CharSort>>)

    override fun at(ctx: ExtendedContext, seq: Expr<*>, index: Expr<*>): Expr<*> =
        ctx.mkAt(seq as Expr<SeqSort<CharSort>>, ctx.mkBV2Int(index as Expr<BitVecSort>, true))

    override fun extract(ctx: ExtendedContext, seq: Expr<*>, from: Expr<*>, to: Expr<*>): Expr<*> =
        ctx.mkExtract(
            seq as Expr<SeqSort<BitVecSort>>,
            ctx.mkBV2Int(from as Expr<BitVecSort>, true),
            ctx.mkBV2Int(to as Expr<BitVecSort>, true)
        )

    override fun indexOf(ctx: ExtendedContext, seq: Expr<*>, subSeq: Expr<*>, offset: Expr<*>): Expr<*> =
        ctx.mkInt2BV(
            WORD, ctx.mkIndexOf(
                seq as Expr<SeqSort<BitVecSort>>,
                subSeq as Expr<SeqSort<BitVecSort>>,
                ctx.mkBV2Int(offset as Expr<BitVecSort>, true)
            )
        )

    override fun concat(ctx: ExtendedContext, lhv: Expr<*>, rhv: Expr<*>): Expr<*> =
        ctx.mkConcat(lhv as Expr<SeqSort<BitVecSort>>, rhv as Expr<SeqSort<BitVecSort>>)

    override fun char2string(ctx: ExtendedContext, expr: Expr<*>): Expr<*> =
        ctx.mkUnit(bv2bv(ctx, expr, getBVSort(ctx, 8)))
}
