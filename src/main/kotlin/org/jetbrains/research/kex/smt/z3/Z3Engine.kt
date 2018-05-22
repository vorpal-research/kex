package org.jetbrains.research.kex.smt.z3

import com.microsoft.z3.*
import org.jetbrains.research.kex.smt.SMTEngine
import org.jetbrains.research.kex.util.unreachable

object Z3Engine : SMTEngine<Context, Expr, Sort, FuncDecl>() {
    override fun getBoolSort(ctx: Context): Sort = ctx.boolSort
    override fun getBVSort(ctx: Context, size: Int): Sort = ctx.mkBitVecSort(size)
    override fun getArraySort(ctx: Context, domain: Sort, range: Sort): Sort = ctx.mkArraySort(domain, range)

    override fun isBool(ctx: Context, expr: Expr) = expr.isBool
    override fun isBV(ctx: Context, expr: Expr) = expr.isBV
    override fun isArray(ctx: Context, expr: Expr) = expr.isArray

    override fun hash(ctx: Context, expr: Expr) = expr.hashCode()
    override fun name(ctx: Context, expr: Expr) = expr.toString()
    override fun toString(ctx: Context, expr: Expr) = expr.toString()
    override fun simplify(ctx: Context, expr: Expr): Expr = expr.simplify()
    override fun equality(ctx: Context, lhv: Expr, rhv: Expr) = lhv == rhv

    override fun makeVar(ctx: Context, sort: Sort, name: String) = ctx.mkFreshConst(name, sort)
    override fun makeBoolConst(ctx: Context, value: Boolean) = ctx.mkBool(value)

    override fun makeNumericConst(ctx: Context, sort: Sort, value: Short) = ctx.mkNumeral(value.toInt(), sort)
    override fun makeNumericConst(ctx: Context, sort: Sort, value: Int) = ctx.mkNumeral(value, sort)
    override fun makeNumericConst(ctx: Context, sort: Sort, value: Long) = ctx.mkNumeral(value, sort)

    override fun makeArrayConst(ctx: Context, sort: Sort, expr: Expr) = TODO()

    override fun makeFunction(ctx: Context, name: String, retSort: Sort, args: List<Sort>) =
            ctx.mkFuncDecl(name, args.toTypedArray(), retSort)

    override fun apply(ctx: Context, f: FuncDecl, args: List<Expr>) = f.apply(*args.toTypedArray())

    override fun negate(ctx: Context, expr: Expr) = when (expr) {
        is BoolExpr -> ctx.mkNot(expr)
        is BitVecExpr -> ctx.mkBVNeg(expr)
        else -> unreachable { log.error("Unimplemented operation negate") }
    }

    private fun exprAsBV(expr: Expr) = expr as? BitVecExpr ?: unreachable { log.error("Unexpected type of expr $expr") }

    override fun binary(ctx: Context, opcode: Opcode, lhv: Expr, rhv: Expr) = when (opcode) {
        Opcode.EQ -> eq(ctx, lhv, rhv)
        Opcode.NEQ -> neq(ctx, lhv, rhv)
        Opcode.ADD -> add(ctx, exprAsBV(lhv), exprAsBV(rhv))
        Opcode.SUB -> sub(ctx, exprAsBV(lhv), exprAsBV(rhv))
        Opcode.MUL -> mul(ctx, exprAsBV(lhv), exprAsBV(rhv))
        Opcode.DIV -> sdiv(ctx, exprAsBV(lhv), exprAsBV(rhv))
        Opcode.MOD -> smod(ctx, exprAsBV(lhv), exprAsBV(rhv))
        Opcode.GT -> gt(ctx, exprAsBV(lhv), exprAsBV(rhv))
        Opcode.GE -> ge(ctx, exprAsBV(lhv), exprAsBV(rhv))
        Opcode.LT -> lt(ctx, exprAsBV(lhv), exprAsBV(rhv))
        Opcode.LE -> le(ctx, exprAsBV(lhv), exprAsBV(rhv))
        Opcode.SHL -> shl(ctx, exprAsBV(lhv), exprAsBV(rhv))
        Opcode.SHR -> lshr(ctx, exprAsBV(lhv), exprAsBV(rhv))
        Opcode.ASHR -> ashr(ctx, exprAsBV(lhv), exprAsBV(rhv))
        Opcode.AND -> when {
            (lhv is BoolExpr) and (rhv is BoolExpr) -> and(ctx, lhv as BoolExpr, rhv as BoolExpr)
            (lhv is BitVecExpr) and (rhv is BitVecExpr) -> and(ctx, lhv as BitVecExpr, rhv as BitVecExpr)
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.OR -> when {
            (lhv is BoolExpr) and (rhv is BoolExpr) -> or(ctx, lhv as BoolExpr, rhv as BoolExpr)
            (lhv is BitVecExpr) and (rhv is BitVecExpr) -> or(ctx, lhv as BitVecExpr, rhv as BitVecExpr)
            else -> unreachable { log.error("Unexpected or arguments: $lhv or $rhv") }
        }
        Opcode.XOR -> when {
            (lhv is BoolExpr) and (rhv is BoolExpr) -> xor(ctx, lhv as BoolExpr, rhv as BoolExpr)
            (lhv is BitVecExpr) and (rhv is BitVecExpr) -> xor(ctx, lhv as BitVecExpr, rhv as BitVecExpr)
            else -> unreachable { log.error("Unexpected xor arguments: $lhv xor $rhv") }
        }
    }

    private fun eq(ctx: Context, lhv: Expr, rhv: Expr) = ctx.mkEq(lhv, rhv)
    private fun neq(ctx: Context, lhv: Expr, rhv: Expr) = ctx.mkNot(eq(ctx, lhv, rhv))
    private fun add(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVAdd(lhv, rhv)
    private fun sub(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSub(lhv, rhv)
    private fun mul(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVMul(lhv, rhv)
    private fun sdiv(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSDiv(lhv, rhv)
    private fun udiv(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVUDiv(lhv, rhv)
    private fun smod(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSMod(lhv, rhv)
    private fun umod(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVURem(lhv, rhv)
    private fun gt(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSGT(lhv, rhv)
    private fun ge(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSGE(lhv, rhv)
    private fun lt(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSLT(lhv, rhv)
    private fun le(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSLE(lhv, rhv)
    private fun shl(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVSHL(lhv, rhv)
    private fun lshr(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVLSHR(lhv, rhv)
    private fun ashr(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVASHR(lhv, rhv)
    private fun and(ctx: Context, lhv: BoolExpr, rhv: BoolExpr) = ctx.mkAnd(lhv, rhv)
    private fun or(ctx: Context, lhv: BoolExpr, rhv: BoolExpr) = ctx.mkOr(lhv, rhv)
    private fun xor(ctx: Context, lhv: BoolExpr, rhv: BoolExpr) = ctx.mkXor(lhv, rhv)
    private fun and(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVAND(lhv, rhv)
    private fun or(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVOR(lhv, rhv)
    private fun xor(ctx: Context, lhv: BitVecExpr, rhv: BitVecExpr) = ctx.mkBVXOR(lhv, rhv)
}