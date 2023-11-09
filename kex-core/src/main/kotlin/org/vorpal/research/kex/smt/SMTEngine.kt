@file:Suppress("unused")

package org.vorpal.research.kex.smt

import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

abstract class SMTEngine<in Context_t : Any, Expr_t : Any, Sort_t : Any, Function_t : Any, Pattern_t : Any> {
    companion object {
        const val WORD = KexType.WORD
        const val DWORD = KexType.DWORD
    }

    enum class Opcode {
        EQ,
        NEQ,
        ADD,
        SUB,
        MUL,
        DIVIDE,
        MOD,
        GT,
        GE,
        LT,
        LE,
        SHL,
        SHR,
        ASHR,
        AND,
        OR,
        XOR,
        IMPLIES,
        IFF,
        CONCAT
    }

    abstract fun makeBound(ctx: Context_t, size: Int, sort: Sort_t): Expr_t
    abstract fun makePattern(ctx: Context_t, expr: Expr_t): Pattern_t

    abstract fun getSort(ctx: Context_t, expr: Expr_t): Sort_t
    abstract fun getBoolSort(ctx: Context_t): Sort_t
    abstract fun getBVSort(ctx: Context_t, size: Int): Sort_t
    abstract fun getFloatSort(ctx: Context_t): Sort_t
    abstract fun getDoubleSort(ctx: Context_t): Sort_t
    abstract fun getArraySort(ctx: Context_t, domain: Sort_t, range: Sort_t): Sort_t
    abstract fun getStringSort(ctx: Context_t): Sort_t
    abstract fun isBoolSort(ctx: Context_t, sort: Sort_t): Boolean
    abstract fun isBVSort(ctx: Context_t, sort: Sort_t): Boolean
    abstract fun isFloatSort(ctx: Context_t, sort: Sort_t): Boolean
    abstract fun isDoubleSort(ctx: Context_t, sort: Sort_t): Boolean
    abstract fun isArraySort(ctx: Context_t, sort: Sort_t): Boolean
    abstract fun isStringSort(ctx: Context_t, sort: Sort_t): Boolean

    fun isBool(ctx: Context_t, expr: Expr_t) = isBoolSort(ctx, getSort(ctx, expr))
    fun isBV(ctx: Context_t, expr: Expr_t) = isBVSort(ctx, getSort(ctx, expr))
    fun isFloat(ctx: Context_t, expr: Expr_t) = isFloatSort(ctx, getSort(ctx, expr))
    fun isDouble(ctx: Context_t, expr: Expr_t) = isDoubleSort(ctx, getSort(ctx, expr))
    fun isFP(ctx: Context_t, expr: Expr_t) = isFloat(ctx, expr) || isDouble(ctx, expr)
    fun isArray(ctx: Context_t, expr: Expr_t) = isArraySort(ctx, getSort(ctx, expr))
    fun isString(ctx: Context_t, expr: Expr_t) = isStringSort(ctx, getSort(ctx, expr))

    abstract fun bvBitSize(ctx: Context_t, sort: Sort_t): Int
    abstract fun floatEBitSize(ctx: Context_t, sort: Sort_t): Int
    abstract fun floatSBitSize(ctx: Context_t, sort: Sort_t): Int
    fun getSortBitSize(ctx: Context_t, sort: Sort_t): Int = when {
        isBoolSort(ctx, sort) -> WORD
        isBVSort(ctx, sort) -> bvBitSize(ctx, sort)
        isFloatSort(ctx, sort) -> WORD
        isDoubleSort(ctx, sort) -> DWORD
        else -> unreachable { log.error("Trying to get bit size of unknown sort $sort") }
    }

    fun getExprBitSize(ctx: Context_t, expr: Expr_t) = getSortBitSize(ctx, getSort(ctx, expr))

    abstract fun bool2bv(ctx: Context_t, expr: Expr_t, sort: Sort_t): Expr_t
    abstract fun bv2bool(ctx: Context_t, expr: Expr_t): Expr_t
    abstract fun bv2bv(ctx: Context_t, expr: Expr_t, sort: Sort_t): Expr_t
    abstract fun bv2float(ctx: Context_t, expr: Expr_t, sort: Sort_t): Expr_t
    abstract fun float2bv(ctx: Context_t, expr: Expr_t, sort: Sort_t): Expr_t
    abstract fun bvIEEE2float(ctx: Context_t, expr: Expr_t, sort: Sort_t): Expr_t
    abstract fun float2IEEEbv(ctx: Context_t, expr: Expr_t, sort: Sort_t): Expr_t
    abstract fun float2float(ctx: Context_t, expr: Expr_t, sort: Sort_t): Expr_t
    abstract fun char2string(ctx: Context_t, expr: Expr_t): Expr_t
    abstract fun bv2string(ctx: Context_t, expr: Expr_t): Expr_t
    abstract fun float2string(ctx: Context_t, expr: Expr_t): Expr_t
    abstract fun double2string(ctx: Context_t, expr: Expr_t): Expr_t
    abstract fun string2bv(ctx: Context_t, expr: Expr_t, sort: Sort_t): Expr_t
    abstract fun string2float(ctx: Context_t, expr: Expr_t): Expr_t
    abstract fun string2double(ctx: Context_t, expr: Expr_t): Expr_t

    abstract fun hash(ctx: Context_t, expr: Expr_t): Int
    abstract fun name(ctx: Context_t, expr: Expr_t): String
    abstract fun toString(ctx: Context_t, expr: Expr_t): String

    abstract fun simplify(ctx: Context_t, expr: Expr_t): Expr_t
    abstract fun equality(ctx: Context_t, lhv: Expr_t, rhv: Expr_t): Boolean

    abstract fun makeVar(ctx: Context_t, sort: Sort_t, name: String, fresh: Boolean): Expr_t

    abstract fun makeBooleanConst(ctx: Context_t, value: Boolean): Expr_t
    abstract fun makeIntConst(ctx: Context_t, value: Short): Expr_t
    abstract fun makeIntConst(ctx: Context_t, value: Int): Expr_t
    abstract fun makeLongConst(ctx: Context_t, value: Long): Expr_t
    abstract fun makeNumericConst(ctx: Context_t, sort: Sort_t, value: Long): Expr_t
    abstract fun makeFloatConst(ctx: Context_t, value: Float): Expr_t
    abstract fun makeDoubleConst(ctx: Context_t, value: Double): Expr_t
    abstract fun makeConstArray(ctx: Context_t, sort: Sort_t, expr: Expr_t): Expr_t
    abstract fun makeFunction(ctx: Context_t, name: String, retSort: Sort_t, args: List<Sort_t>): Function_t
    abstract fun makeStringConst(ctx: Context_t, value: String): Expr_t
    abstract fun makeBVConst(ctx: Context_t, value: String, radix: Int, width: Int): Expr_t

    abstract fun apply(ctx: Context_t, f: Function_t, args: List<Expr_t>): Expr_t

    abstract fun negate(ctx: Context_t, expr: Expr_t): Expr_t
    abstract fun binary(ctx: Context_t, opcode: Opcode, lhv: Expr_t, rhv: Expr_t): Expr_t

    abstract fun conjunction(ctx: Context_t, vararg exprs: Expr_t): Expr_t
    abstract fun conjunction(ctx: Context_t, exprs: Collection<Expr_t>): Expr_t

    abstract fun disjunction(ctx: Context_t, vararg exprs: Expr_t): Expr_t
    abstract fun disjunction(ctx: Context_t, exprs: Collection<Expr_t>): Expr_t

    abstract fun zext(ctx: Context_t, n: Int, expr: Expr_t): Expr_t
    abstract fun sext(ctx: Context_t, n: Int, expr: Expr_t): Expr_t

    abstract fun load(ctx: Context_t, array: Expr_t, index: Expr_t): Expr_t
    abstract fun store(ctx: Context_t, array: Expr_t, index: Expr_t, value: Expr_t): Expr_t

    abstract fun ite(ctx: Context_t, cond: Expr_t, lhv: Expr_t, rhv: Expr_t): Expr_t

    abstract fun extract(ctx: Context_t, bv: Expr_t, high: Int, low: Int): Expr_t

    abstract fun exists(ctx: Context_t, sorts: List<Sort_t>, body: (List<Expr_t>) -> Expr_t): Expr_t
    abstract fun exists(ctx: Context_t, sorts: List<Sort_t>,
                        body: (List<Expr_t>) -> Expr_t, patternGenerator: (List<Expr_t>) -> List<Pattern_t>): Expr_t

    abstract fun forAll(ctx: Context_t, sorts: List<Sort_t>, body: (List<Expr_t>) -> Expr_t): Expr_t
    abstract fun forAll(ctx: Context_t, sorts: List<Sort_t>,
                        body: (List<Expr_t>) -> Expr_t, patternGenerator: (List<Expr_t>) -> List<Pattern_t>): Expr_t

    abstract fun lambda(ctx: Context_t, elementSort: Sort_t, sorts: List<Sort_t>, body: (List<Expr_t>) -> Expr_t): Expr_t

    abstract fun contains(ctx: Context_t, seq: Expr_t, value: Expr_t): Expr_t
    abstract fun nths(ctx: Context_t, seq: Expr_t, index: Expr_t): Expr_t
    abstract fun length(ctx: Context_t, seq: Expr_t): Expr_t
    abstract fun prefixOf(ctx: Context_t, seq: Expr_t, prefix: Expr_t): Expr_t
    abstract fun suffixOf(ctx: Context_t, seq: Expr_t, suffix: Expr_t): Expr_t
    abstract fun at(ctx: Context_t, seq: Expr_t, index: Expr_t): Expr_t
    abstract fun extract(ctx: Context_t, seq: Expr_t, from: Expr_t, to: Expr_t): Expr_t
    abstract fun indexOf(ctx: Context_t, seq: Expr_t, subSeq: Expr_t, offset: Expr_t): Expr_t
    abstract fun concat(ctx: Context_t, lhv: Expr_t, rhv: Expr_t): Expr_t
}
