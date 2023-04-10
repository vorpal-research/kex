@file:Suppress("unused")

package org.vorpal.research.kex.smt.ksmt

import org.ksmt.KAst
import org.ksmt.KContext
import org.ksmt.decl.KDecl
import org.ksmt.decl.KFuncDecl
import org.ksmt.expr.KExpr
import org.ksmt.expr.KFpRoundingMode
import org.ksmt.sort.*
import org.vorpal.research.kex.smt.SMTEngine
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import java.math.BigInteger

@Suppress("UNCHECKED_CAST")
object KSMTEngine : SMTEngine<KContext, KAst, KSort, KFuncDecl<*>, KAst>() {

    fun KAst.asExpr(ctx: KContext): KExpr<*> = when (this) {
        is KDecl<*> -> ctx.mkConstApp(this)
        is KExpr<*> -> this
        else -> unreachable("Unknown expr type")
    }

    override fun makeBound(ctx: KContext, size: Int, sort: KSort): KDecl<*> {
        return ctx.mkConstDecl("bound$size", sort)
    }

    override fun makePattern(ctx: KContext, expr: KAst): KAst {
        return expr
    }

    override fun getSort(ctx: KContext, expr: KAst): KSort {
        return expr.asExpr(ctx).sort
    }

    override fun getBoolSort(ctx: KContext): KSort {
        return ctx.boolSort
    }

    override fun getBVSort(ctx: KContext, size: Int): KSort {
        return ctx.mkBvSort(size.toUInt())
    }

    override fun getFloatSort(ctx: KContext): KSort {
        return ctx.mkFp32Sort()
    }

    override fun getDoubleSort(ctx: KContext): KSort {
        return ctx.mkFp64Sort()
    }

    override fun getArraySort(ctx: KContext, domain: KSort, range: KSort): KSort {
        return ctx.mkArraySort(domain, range)
    }

    override fun getStringSort(ctx: KContext): KSort {
        TODO("Not yet implemented")
    }

    override fun isBoolSort(ctx: KContext, sort: KSort): Boolean {
        return sort is KBoolSort
    }

    override fun isBVSort(ctx: KContext, sort: KSort): Boolean {
        return sort is KBvSort
    }

    override fun isFloatSort(ctx: KContext, sort: KSort): Boolean {
        return sort is KFp32Sort
    }

    override fun isDoubleSort(ctx: KContext, sort: KSort): Boolean {
        return sort is KFp64Sort
    }

    override fun isArraySort(ctx: KContext, sort: KSort): Boolean {
        return sort is KArraySort<*, *>
    }

    override fun isStringSort(ctx: KContext, sort: KSort): Boolean {
        TODO("Not yet implemented")
    }

    override fun bvBitSize(ctx: KContext, sort: KSort): Int {
        return (sort as KBvSort).sizeBits.toInt()
    }

    override fun floatEBitSize(ctx: KContext, sort: KSort): Int {
        return (sort as KFpSort).exponentBits.toInt()
    }

    override fun floatSBitSize(ctx: KContext, sort: KSort): Int {
        return (sort as KFpSort).significandBits.toInt()
    }

    override fun bool2bv(ctx: KContext, expr: KAst, sort: KSort): KExpr<*> {
        return ite(ctx, expr, makeNumericConst(ctx, sort, 1), makeNumericConst(ctx, sort, 0))
    }

    override fun bv2bool(ctx: KContext, expr: KAst): KExpr<*> {
        return binary(ctx, Opcode.NEQ, expr, makeNumericConst(ctx, getSort(ctx, expr), 0))
    }

    override fun bv2bv(ctx: KContext, expr: KAst, sort: KSort): KExpr<*> {
        val curSize = (getSort(ctx, expr) as KBvSort).sizeBits.toInt()
        val castSize = (sort as KBvSort).sizeBits.toInt()
        return when {
            curSize == castSize -> expr.asExpr(ctx)
            curSize < castSize -> sext(ctx, castSize, expr)
            else -> extract(ctx, expr, high = castSize - 1, low = 0)
        }
    }

    override fun bv2float(ctx: KContext, expr: KAst, sort: KSort): KExpr<*> {
        return ctx.mkBvToFpExpr(
            sort as KFpSort,
            ctx.mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardZero),
            expr.asExpr(ctx) as KExpr<KBvSort>,
            true
        )
    }

    override fun float2bv(ctx: KContext, expr: KAst, sort: KSort): KExpr<*> {
        return ctx.mkFpToBvExpr(
            ctx.mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardZero),
            expr.asExpr(ctx) as KExpr<KFpSort>,
            (sort as KBvSort).sizeBits.toInt(),
            true
        )
    }

    override fun bvIEEE2float(ctx: KContext, expr: KAst, sort: KSort): KExpr<*> {
        val fpSort = sort as KFpSort
        val bv = expr.asExpr(ctx) as KExpr<KBvSort>
        val exponentBits = fpSort.exponentBits.toInt()
        val size = bv.sort.sizeBits.toInt()

        @Suppress("UNCHECKED_CAST")
        val sign = extract(ctx, bv, size - 1, size - 1) as KExpr<KBv1Sort>
        val exponent = extract(ctx, bv, size - 2, size - exponentBits - 1) as KExpr<out KBvSort>
        val significand = extract(ctx, bv, size - exponentBits - 2, 0) as KExpr<out KBvSort>

        return ctx.mkFpFromBvExpr<KFpSort>(sign, exponent, significand)
    }

    override fun float2IEEEbv(ctx: KContext, expr: KAst, sort: KSort): KExpr<*> {
        return unreachable { log.error("Should not use float2ieee bv") }
//        return ctx.mkFpToIEEEBvExpr(expr.asExpr(ctx) as KExpr<KFpSort>)
    }

    override fun float2float(ctx: KContext, expr: KAst, sort: KSort): KExpr<*> {
        return ctx.mkFpToFpExpr(
            sort as KFpSort,
            ctx.mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardZero),
            expr.asExpr(ctx) as KExpr<out KFpSort>
        )
    }

    override fun char2string(ctx: KContext, expr: KAst): KAst {
        TODO("Not yet implemented")
    }

    override fun bv2string(ctx: KContext, expr: KAst): KAst {
        TODO("Not yet implemented")
    }

    override fun float2string(ctx: KContext, expr: KAst): KAst {
        TODO("Not yet implemented")
    }

    override fun double2string(ctx: KContext, expr: KAst): KAst {
        TODO("Not yet implemented")
    }

    override fun string2bv(ctx: KContext, expr: KAst, sort: KSort): KAst {
        TODO("Not yet implemented")
    }

    override fun string2float(ctx: KContext, expr: KAst): KAst {
        TODO("Not yet implemented")
    }

    override fun string2double(ctx: KContext, expr: KAst): KAst {
        TODO("Not yet implemented")
    }

    override fun hash(ctx: KContext, expr: KAst): Int {
        return expr.hashCode()
    }

    override fun name(ctx: KContext, expr: KAst): String {
        return expr.toString()
    }

    override fun toString(ctx: KContext, expr: KAst): String {
        return expr.toString()
    }

    override fun simplify(ctx: KContext, expr: KAst): KAst {
        return expr
    }

    override fun equality(ctx: KContext, lhv: KAst, rhv: KAst): Boolean {
        return lhv == rhv
    }

    override fun makeVar(ctx: KContext, sort: KSort, name: String, fresh: Boolean): KDecl<*> = when {
        fresh -> ctx.mkFreshConstDecl(name, sort)
        else -> ctx.mkConstDecl(name, sort)
    }

    override fun makeBooleanConst(ctx: KContext, value: Boolean): KExpr<KBoolSort> {
        return when {
            value -> ctx.mkTrue()
            else -> ctx.mkFalse()
        }
    }

    override fun makeIntConst(ctx: KContext, value: Short): KExpr<KBvSort> {
        return ctx.mkBv(value, WORD.toUInt())
    }

    override fun makeIntConst(ctx: KContext, value: Int): KExpr<KBvSort> {
        return ctx.mkBv(value, WORD.toUInt())
    }

    override fun makeLongConst(ctx: KContext, value: Long): KExpr<KBvSort> {
        return ctx.mkBv(value, DWORD.toUInt())
    }

    override fun makeNumericConst(ctx: KContext, sort: KSort, value: Long): KExpr<KBvSort> {
        return ctx.mkBv(value, (sort as KBvSort).sizeBits)
    }

    override fun makeFloatConst(ctx: KContext, value: Float): KExpr<KFpSort> {
        return ctx.mkFp(value, getFloatSort(ctx) as KFpSort)
    }

    override fun makeDoubleConst(ctx: KContext, value: Double): KExpr<KFpSort> {
        return ctx.mkFp(value, getDoubleSort(ctx) as KFpSort)
    }

    override fun makeConstArray(ctx: KContext, sort: KSort, expr: KAst): KExpr<*> {
        val e = expr.asExpr(ctx)
        return ctx.mkArrayConst(ctx.mkArraySort(sort, e.sort), e as KExpr<KSort>)
    }

    override fun makeFunction(ctx: KContext, name: String, retSort: KSort, args: List<KSort>): KFuncDecl<*> {
        return ctx.mkFuncDecl(name, retSort, args)
    }

    override fun makeStringConst(ctx: KContext, value: String): KExpr<*> {
        TODO("Not yet implemented")
    }

    override fun makeBVConst(ctx: KContext, value: String, radix: Int, width: Int): KExpr<KBvSort> {
        var bitStr = BigInteger(value, radix).toString(2)
        bitStr = "0".repeat(width - bitStr.length) + bitStr
        return ctx.mkBv(bitStr, width.toUInt())
    }

    override fun apply(ctx: KContext, f: KFuncDecl<*>, args: List<KAst>): KExpr<*> {
        return f.apply(args.map { it.asExpr(ctx) })
    }

    override fun negate(ctx: KContext, expr: KAst): KExpr<*> {
        val ex = expr.asExpr(ctx)
        return when (ex.sort) {
            is KBoolSort -> ctx.mkNot(ex as KExpr<KBoolSort>)
            is KBvSort -> ctx.mkBvNegationExpr(ex as KExpr<KBvSort>)
            is KFpSort -> ctx.mkFpNegationExpr(ex as KExpr<KFpSort>)
            else -> unreachable { log.error("Unimplemented operation negate") }
        }
    }

    override fun binary(ctx: KContext, opcode: Opcode, lhv: KAst, rhv: KAst): KExpr<*> {
        val lhvExpr = lhv.asExpr(ctx)
        val rhvExpr = rhv.asExpr(ctx)
        return when (opcode) {
            Opcode.EQ -> eq(ctx, lhvExpr, rhvExpr)
            Opcode.NEQ -> neq(ctx, lhvExpr, rhvExpr)
            Opcode.ADD -> when {
                lhvExpr.sort is KBvSort && rhvExpr.sort is KBvSort -> addBv(
                    ctx,
                    lhvExpr as KExpr<KBvSort>,
                    rhvExpr as KExpr<KBvSort>
                )

                lhvExpr.sort is KFpSort && rhvExpr.sort is KFpSort -> addFp(
                    ctx,
                    lhvExpr as KExpr<KFpSort>,
                    rhvExpr as KExpr<KFpSort>
                )

                else -> unreachable { log.error("Unexpected and arguments: $lhvExpr and $rhv") }
            }

            Opcode.SUB -> when {
                lhvExpr.sort is KBvSort && rhvExpr.sort is KBvSort -> subBv(
                    ctx,
                    lhvExpr as KExpr<KBvSort>,
                    rhvExpr as KExpr<KBvSort>
                )

                lhvExpr.sort is KFpSort && rhvExpr.sort is KFpSort -> subFp(
                    ctx,
                    lhvExpr as KExpr<KFpSort>,
                    rhvExpr as KExpr<KFpSort>
                )

                else -> unreachable { log.error("Unexpected and arguments: $lhvExpr and $rhvExpr") }
            }

            Opcode.MUL -> when {
                lhvExpr.sort is KBvSort && rhvExpr.sort is KBvSort -> mulBv(
                    ctx,
                    lhvExpr as KExpr<KBvSort>,
                    rhvExpr as KExpr<KBvSort>
                )

                lhvExpr.sort is KFpSort && rhvExpr.sort is KFpSort -> mulFp(
                    ctx,
                    lhvExpr as KExpr<KFpSort>,
                    rhvExpr as KExpr<KFpSort>
                )

                else -> unreachable { log.error("Unexpected and arguments: $lhvExpr and $rhv") }
            }

            Opcode.DIVIDE -> when {
                lhvExpr.sort is KBvSort && rhvExpr.sort is KBvSort -> sdivBv(
                    ctx,
                    lhvExpr as KExpr<KBvSort>,
                    rhvExpr as KExpr<KBvSort>
                )

                lhvExpr.sort is KFpSort && rhvExpr.sort is KFpSort -> sdivFp(
                    ctx,
                    lhvExpr as KExpr<KFpSort>,
                    rhvExpr as KExpr<KFpSort>
                )

                else -> unreachable { log.error("Unexpected and arguments: $lhvExpr and $rhv") }
            }

            Opcode.MOD -> when {
                lhvExpr.sort is KBvSort && rhvExpr.sort is KBvSort -> smod(
                    ctx,
                    lhvExpr as KExpr<KBvSort>,
                    rhvExpr as KExpr<KBvSort>
                )

                lhvExpr.sort is KFpSort && rhvExpr.sort is KFpSort -> fmod(
                    ctx,
                    lhvExpr as KExpr<KFpSort>,
                    rhvExpr as KExpr<KFpSort>
                )

                else -> unreachable { log.error("Unexpected mod arguments: $lhvExpr and $rhv") }
            }

            Opcode.GT -> when {
                lhvExpr.sort is KBvSort && rhvExpr.sort is KBvSort -> gtBv(
                    ctx,
                    lhvExpr as KExpr<KBvSort>,
                    rhvExpr as KExpr<KBvSort>
                )

                lhvExpr.sort is KFpSort && rhvExpr.sort is KFpSort -> gtFp(
                    ctx,
                    lhvExpr as KExpr<KFpSort>,
                    rhvExpr as KExpr<KFpSort>
                )

                else -> unreachable { log.error("Unexpected and arguments: $lhvExpr and $rhv") }
            }

            Opcode.GE -> when {
                lhvExpr.sort is KBvSort && rhvExpr.sort is KBvSort -> geBv(
                    ctx,
                    lhvExpr as KExpr<KBvSort>,
                    rhvExpr as KExpr<KBvSort>
                )

                lhvExpr.sort is KFpSort && rhvExpr.sort is KFpSort -> geFp(
                    ctx,
                    lhvExpr as KExpr<KFpSort>,
                    rhvExpr as KExpr<KFpSort>
                )

                else -> unreachable { log.error("Unexpected and arguments: $lhvExpr and $rhv") }
            }

            Opcode.LT -> when {
                lhvExpr.sort is KBvSort && rhvExpr.sort is KBvSort -> ltBv(
                    ctx,
                    lhvExpr as KExpr<KBvSort>,
                    rhvExpr as KExpr<KBvSort>
                )

                lhvExpr.sort is KFpSort && rhvExpr.sort is KFpSort -> ltFp(
                    ctx,
                    lhvExpr as KExpr<KFpSort>,
                    rhvExpr as KExpr<KFpSort>
                )

                else -> unreachable { log.error("Unexpected and arguments: $lhvExpr and $rhv") }
            }

            Opcode.LE -> when {
                lhvExpr.sort is KBvSort && rhvExpr.sort is KBvSort -> leBv(
                    ctx,
                    lhvExpr as KExpr<KBvSort>,
                    rhvExpr as KExpr<KBvSort>
                )

                lhvExpr.sort is KFpSort && rhvExpr.sort is KFpSort -> leFp(
                    ctx,
                    lhvExpr as KExpr<KFpSort>,
                    rhvExpr as KExpr<KFpSort>
                )

                else -> unreachable { log.error("Unexpected and arguments: $lhvExpr and $rhv") }
            }

            Opcode.SHL -> shl(ctx, lhvExpr as KExpr<KBvSort>, rhvExpr as KExpr<KBvSort>)
            Opcode.SHR -> lshr(ctx, lhvExpr as KExpr<KBvSort>, rhvExpr as KExpr<KBvSort>)
            Opcode.ASHR -> ashr(ctx, lhvExpr as KExpr<KBvSort>, rhvExpr as KExpr<KBvSort>)
            Opcode.AND -> when {
                lhvExpr.sort is KBoolSort && rhvExpr.sort is KBoolSort -> and(
                    ctx,
                    lhvExpr as KExpr<KBoolSort>,
                    rhvExpr as KExpr<KBoolSort>
                )

                lhvExpr.sort is KBvSort && rhvExpr.sort is KBvSort -> andBv(
                    ctx,
                    lhvExpr as KExpr<KBvSort>,
                    rhvExpr as KExpr<KBvSort>
                )

                else -> unreachable { log.error("Unexpected and arguments: $lhvExpr and $rhv") }
            }

            Opcode.OR -> when {
                lhvExpr.sort is KBoolSort && rhvExpr.sort is KBoolSort -> or(
                    ctx,
                    lhvExpr as KExpr<KBoolSort>,
                    rhvExpr as KExpr<KBoolSort>
                )

                lhvExpr.sort is KBvSort && rhvExpr.sort is KBvSort -> orBv(
                    ctx,
                    lhvExpr as KExpr<KBvSort>,
                    rhvExpr as KExpr<KBvSort>
                )

                else -> unreachable { log.error("Unexpected or arguments: $lhvExpr or $rhv") }
            }

            Opcode.XOR -> when {
                lhvExpr.sort is KBoolSort && rhvExpr.sort is KBoolSort -> xor(
                    ctx,
                    lhvExpr as KExpr<KBoolSort>,
                    rhvExpr as KExpr<KBoolSort>
                )

                lhvExpr.sort is KBvSort && rhvExpr.sort is KBvSort -> xorBv(
                    ctx,
                    lhvExpr as KExpr<KBvSort>,
                    rhvExpr as KExpr<KBvSort>
                )

                else -> unreachable { log.error("Unexpected xor arguments: $lhvExpr xor $rhv") }
            }

            Opcode.IMPLIES -> implies(ctx, lhvExpr as KExpr<KBoolSort>, rhvExpr as KExpr<KBoolSort>)
            Opcode.IFF -> iff(ctx, lhvExpr as KExpr<KBoolSort>, rhvExpr as KExpr<KBoolSort>)
            Opcode.CONCAT -> concat(ctx, lhvExpr as KExpr<KBvSort>, rhvExpr as KExpr<KBvSort>)
        }
    }

    private fun eq(ctx: KContext, lhv: KExpr<*>, rhv: KExpr<*>) = ctx.mkEq(lhv as KExpr<KSort>, rhv as KExpr<KSort>)
    private fun neq(ctx: KContext, lhv: KExpr<*>, rhv: KExpr<*>) = ctx.mkNot(eq(ctx, lhv, rhv))

    private fun addBv(ctx: KContext, lhv: KExpr<KBvSort>, rhv: KExpr<KBvSort>) = ctx.mkBvAddExpr(lhv, rhv)
    private fun addFp(ctx: KContext, lhv: KExpr<KFpSort>, rhv: KExpr<KFpSort>) = ctx.mkFpAddExpr(
        ctx.mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardZero), lhv, rhv
    )

    private fun subBv(ctx: KContext, lhv: KExpr<KBvSort>, rhv: KExpr<KBvSort>) = ctx.mkBvSubExpr(lhv, rhv)
    private fun subFp(ctx: KContext, lhv: KExpr<KFpSort>, rhv: KExpr<KFpSort>) = ctx.mkFpSubExpr(
        ctx.mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardZero), lhv, rhv
    )

    private fun mulBv(ctx: KContext, lhv: KExpr<KBvSort>, rhv: KExpr<KBvSort>) = ctx.mkBvMulExpr(lhv, rhv)
    private fun mulFp(ctx: KContext, lhv: KExpr<KFpSort>, rhv: KExpr<KFpSort>) = ctx.mkFpMulExpr(
        ctx.mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardZero), lhv, rhv
    )

    private fun sdivBv(ctx: KContext, lhv: KExpr<KBvSort>, rhv: KExpr<KBvSort>) = ctx.mkBvSignedDivExpr(lhv, rhv)
    private fun sdivFp(ctx: KContext, lhv: KExpr<KFpSort>, rhv: KExpr<KFpSort>) = ctx.mkFpDivExpr(
        ctx.mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardZero), lhv, rhv
    )

    private fun udiv(ctx: KContext, lhv: KExpr<KBvSort>, rhv: KExpr<KBvSort>) = ctx.mkBvUnsignedDivExpr(lhv, rhv)
    private fun smod(ctx: KContext, lhv: KExpr<KBvSort>, rhv: KExpr<KBvSort>) = ctx.mkBvSignedModExpr(lhv, rhv)
    private fun umod(ctx: KContext, lhv: KExpr<KBvSort>, rhv: KExpr<KBvSort>) = ctx.mkBvUnsignedRemExpr(lhv, rhv)
    private fun fmod(ctx: KContext, lhv: KExpr<KFpSort>, rhv: KExpr<KFpSort>) = ctx.mkFpRemExpr(lhv, rhv)

    private fun gtBv(ctx: KContext, lhv: KExpr<KBvSort>, rhv: KExpr<KBvSort>) = ctx.mkBvSignedGreaterExpr(lhv, rhv)
    private fun gtFp(ctx: KContext, lhv: KExpr<KFpSort>, rhv: KExpr<KFpSort>) = ctx.mkFpGreaterExpr(lhv, rhv)

    private fun geBv(ctx: KContext, lhv: KExpr<KBvSort>, rhv: KExpr<KBvSort>) =
        ctx.mkBvSignedGreaterOrEqualExpr(lhv, rhv)

    private fun geFp(ctx: KContext, lhv: KExpr<KFpSort>, rhv: KExpr<KFpSort>) = ctx.mkFpGreaterOrEqualExpr(lhv, rhv)

    private fun ltBv(ctx: KContext, lhv: KExpr<KBvSort>, rhv: KExpr<KBvSort>) = ctx.mkBvSignedLessExpr(lhv, rhv)
    private fun ltFp(ctx: KContext, lhv: KExpr<KFpSort>, rhv: KExpr<KFpSort>) = ctx.mkFpLessExpr(lhv, rhv)

    private fun leBv(ctx: KContext, lhv: KExpr<KBvSort>, rhv: KExpr<KBvSort>) = ctx.mkBvSignedLessOrEqualExpr(lhv, rhv)
    private fun leFp(ctx: KContext, lhv: KExpr<KFpSort>, rhv: KExpr<KFpSort>) = ctx.mkFpLessOrEqualExpr(lhv, rhv)

    private fun shl(ctx: KContext, lhv: KExpr<KBvSort>, rhv: KExpr<KBvSort>) = ctx.mkBvShiftLeftExpr(lhv, rhv)
    private fun lshr(ctx: KContext, lhv: KExpr<KBvSort>, rhv: KExpr<KBvSort>) = ctx.mkBvLogicalShiftRightExpr(lhv, rhv)
    private fun ashr(ctx: KContext, lhv: KExpr<KBvSort>, rhv: KExpr<KBvSort>) = ctx.mkBvArithShiftRightExpr(lhv, rhv)

    private fun and(ctx: KContext, lhv: KExpr<KBoolSort>, rhv: KExpr<KBoolSort>) = ctx.mkAnd(lhv, rhv)
    private fun or(ctx: KContext, lhv: KExpr<KBoolSort>, rhv: KExpr<KBoolSort>) = ctx.mkOr(lhv, rhv)
    private fun xor(ctx: KContext, lhv: KExpr<KBoolSort>, rhv: KExpr<KBoolSort>) = ctx.mkXor(lhv, rhv)
    private fun andBv(ctx: KContext, lhv: KExpr<KBvSort>, rhv: KExpr<KBvSort>) = ctx.mkBvAndExpr(lhv, rhv)
    private fun orBv(ctx: KContext, lhv: KExpr<KBvSort>, rhv: KExpr<KBvSort>) = ctx.mkBvOrExpr(lhv, rhv)
    private fun xorBv(ctx: KContext, lhv: KExpr<KBvSort>, rhv: KExpr<KBvSort>) = ctx.mkBvXorExpr(lhv, rhv)
    private fun implies(ctx: KContext, lhv: KExpr<KBoolSort>, rhv: KExpr<KBoolSort>) = ctx.mkImplies(lhv, rhv)
    private fun iff(ctx: KContext, lhv: KExpr<KBoolSort>, rhv: KExpr<KBoolSort>) = ctx.mkEq(lhv, rhv)
    private fun concat(ctx: KContext, lhv: KExpr<KBvSort>, rhv: KExpr<KBvSort>) = ctx.mkBvConcatExpr(lhv, rhv)

    override fun conjunction(ctx: KContext, vararg exprs: KAst): KExpr<*> {
        val boolExpressions = exprs.map { it.asExpr(ctx) as KExpr<KBoolSort> }
        return ctx.mkAnd(boolExpressions)
    }

    override fun conjunction(ctx: KContext, exprs: Collection<KAst>): KExpr<*> {
        val boolExpressions = exprs.map { it.asExpr(ctx) as KExpr<KBoolSort> }
        return when (boolExpressions.size) {
            1 -> boolExpressions.single()
            else -> ctx.mkAnd(boolExpressions)
        }
    }

    override fun zext(ctx: KContext, n: Int, expr: KAst): KExpr<*> {
        val exprBitSize = bvBitSize(ctx, getSort(ctx, expr))
        val bvExpr = expr.asExpr(ctx) as KExpr<KBvSort>
        return when {
            exprBitSize < n -> ctx.mkBvZeroExtensionExpr(n - exprBitSize, bvExpr)
            else -> bvExpr
        }
    }

    override fun sext(ctx: KContext, n: Int, expr: KAst): KExpr<*> {
        val exprBitSize = bvBitSize(ctx, getSort(ctx, expr))
        val bvExpr = expr.asExpr(ctx) as KExpr<KBvSort>
        return when {
            exprBitSize < n -> ctx.mkBvSignExtensionExpr(n - exprBitSize, bvExpr)
            else -> bvExpr
        }
    }

    override fun load(ctx: KContext, array: KAst, index: KAst): KExpr<*> {
        return ctx.mkArraySelect(
            array.asExpr(ctx) as KExpr<KArraySort<KSort, KSort>>,
            index.asExpr(ctx) as KExpr<KSort>
        )
    }

    override fun store(ctx: KContext, array: KAst, index: KAst, value: KAst): KExpr<*> {
        return ctx.mkArrayStore(
            array.asExpr(ctx) as KExpr<KArraySort<KSort, KSort>>,
            index.asExpr(ctx) as KExpr<KSort>,
            value.asExpr(ctx) as KExpr<KSort>
        )
    }

    override fun ite(ctx: KContext, cond: KAst, lhv: KAst, rhv: KAst): KExpr<*> {
        return ctx.mkIte(
            cond.asExpr(ctx) as KExpr<KBoolSort>,
            lhv.asExpr(ctx) as KExpr<KSort>,
            rhv.asExpr(ctx) as KExpr<KSort>
        )
    }

    override fun extract(ctx: KContext, bv: KAst, high: Int, low: Int): KExpr<*> {
        return ctx.mkBvExtractExpr(high, low, bv.asExpr(ctx) as KExpr<KBvSort>)
    }

    override fun exists(ctx: KContext, sorts: List<KSort>, body: (List<KAst>) -> KAst): KExpr<*> {
        val bounds = sorts.withIndex().map { (index, sort) -> makeBound(ctx, index, sort) }
        val realBody = body(bounds).asExpr(ctx) as KExpr<KBoolSort>
        return ctx.mkExistentialQuantifier(
            realBody,
            bounds
        )
    }

    override fun forAll(ctx: KContext, sorts: List<KSort>, body: (List<KAst>) -> KAst): KExpr<*> {
        val bounds = sorts.withIndex().map { (index, sort) -> makeBound(ctx, index, sort) }
        val realBody = body(bounds).asExpr(ctx) as KExpr<KBoolSort>
        return ctx.mkUniversalQuantifier(
            realBody,
            bounds
        )
    }

    override fun lambda(
        ctx: KContext,
        elementSort: KSort,
        sorts: List<KSort>,
        body: (List<KAst>) -> KAst
    ): KExpr<*> {
        val bounds = sorts.withIndex().map { (index, sort) -> makeBound(ctx, index, sort) }
        val realBody = body(bounds).asExpr(ctx) as KExpr<KBoolSort>
        return ctx.mkArrayLambda(
            bounds.first(),
            realBody
        )
    }

    override fun contains(ctx: KContext, seq: KAst, value: KAst): KExpr<*> {
        TODO("Not yet implemented")
    }

    override fun nths(ctx: KContext, seq: KAst, index: KAst): KExpr<*> {
        TODO("Not yet implemented")
    }

    override fun length(ctx: KContext, seq: KAst): KExpr<*> {
        TODO("Not yet implemented")
    }

    override fun prefixOf(ctx: KContext, seq: KAst, prefix: KAst): KExpr<*> {
        TODO("Not yet implemented")
    }

    override fun suffixOf(ctx: KContext, seq: KAst, suffix: KAst): KExpr<*> {
        TODO("Not yet implemented")
    }

    override fun at(ctx: KContext, seq: KAst, index: KAst): KExpr<*> {
        TODO("Not yet implemented")
    }

    override fun extract(ctx: KContext, seq: KAst, from: KAst, to: KAst): KExpr<*> {
        TODO("Not yet implemented")
    }

    override fun indexOf(ctx: KContext, seq: KAst, subSeq: KAst, offset: KAst): KExpr<*> {
        TODO("Not yet implemented")
    }

    override fun concat(ctx: KContext, lhv: KAst, rhv: KAst): KExpr<*> {
        TODO("Not yet implemented")
    }

    override fun exists(
        ctx: KContext,
        sorts: List<KSort>,
        body: (List<KAst>) -> KAst,
        patternGenerator: (List<KAst>) -> List<KAst>
    ): KExpr<*> {
        TODO("Not yet implemented")
    }

    override fun forAll(
        ctx: KContext,
        sorts: List<KSort>,
        body: (List<KAst>) -> KAst,
        patternGenerator: (List<KAst>) -> List<KAst>
    ): KExpr<*> {
        TODO("Not yet implemented")
    }
}
