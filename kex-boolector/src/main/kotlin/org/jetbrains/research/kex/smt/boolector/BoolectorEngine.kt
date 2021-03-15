package org.jetbrains.research.kex.smt.boolector

import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.boolector.*
import org.jetbrains.research.kex.smt.SMTEngine
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object BoolectorEngine : SMTEngine<Btor, BoolectorNode, BoolectorSort, BoolectorFunction, FunctionDecl.FunctionParam>() {
    private var trueExpr: BoolectorNode? = null
    private var falseExpr: BoolectorNode? = null
    private val bvSortCache = mutableMapOf<Int, BoolectorSort>()

    override fun initialize() {
        trueExpr = null
        falseExpr = null
        bvSortCache.clear()
    }

    override fun makeBound(ctx: Btor, size: Int, sort: BoolectorSort): BoolectorNode = BitvecNode.constBitvec(ctx, "1")

    override fun makePattern(ctx: Btor, expr: BoolectorNode): FunctionDecl.FunctionParam =
            FunctionDecl.FunctionParam.param(expr.sort, "d")

    override fun getSort(ctx: Btor, expr: BoolectorNode): BoolectorSort = expr.sort

    override fun getBoolSort(ctx: Btor): BoolectorSort = BoolSort.boolSort(ctx)

    override fun getBVSort(ctx: Btor, size: Int): BoolectorSort = bvSortCache.getOrPut(size) { BitvecSort.bitvecSort(ctx, size) }

    override fun getFloatSort(ctx: Btor): BoolectorSort = getBVSort(ctx, 32)

    override fun getDoubleSort(ctx: Btor): BoolectorSort = getBVSort(ctx, 64)

    override fun getArraySort(ctx: Btor, domain: BoolectorSort, range: BoolectorSort): BoolectorSort =
            ArraySort.arraySort(domain, range)

    override fun isBoolSort(ctx: Btor, sort: BoolectorSort): Boolean = sort.isBoolSort

    override fun isBVSort(ctx: Btor, sort: BoolectorSort): Boolean = sort.isBitvecSort

    override fun isFloatSort(ctx: Btor, sort: BoolectorSort): Boolean =
            sort.isBitvecSort && sort.toBitvecSort().width == 32

    override fun isDoubleSort(ctx: Btor, sort: BoolectorSort): Boolean =
            sort.isBitvecSort && sort.toBitvecSort().width == 64

    override fun isArraySort(ctx: Btor, sort: BoolectorSort): Boolean = sort.isArraySort

    override fun bvBitsize(ctx: Btor, sort: BoolectorSort): Int = sort.toBitvecSort().width

    override fun floatEBitsize(ctx: Btor, sort: BoolectorSort): Int = 10

    override fun floatSBitsize(ctx: Btor, sort: BoolectorSort): Int = 10

    override fun bool2bv(ctx: Btor, expr: BoolectorNode, sort: BoolectorSort): BoolectorNode =
            bv2bv(ctx, expr, sort)

    override fun bv2bool(ctx: Btor, expr: BoolectorNode): BoolectorNode =
            binary(ctx, Opcode.NEQ, expr, BitvecNode.constLong(0L, expr.sort.toBitvecSort()))


    override fun bv2bv(ctx: Btor, expr: BoolectorNode, sort: BoolectorSort): BoolectorNode =
            bv2bv(ctx, expr, sort.toBitvecSort().width)

    override fun bv2float(ctx: Btor, expr: BoolectorNode, sort: BoolectorSort): BoolectorNode = bv2bv(ctx, expr, sort)

    override fun float2bv(ctx: Btor, expr: BoolectorNode, sort: BoolectorSort): BoolectorNode = bv2bv(ctx, expr, sort)

    override fun IEEEbv2float(ctx: Btor, expr: BoolectorNode, sort: BoolectorSort): BoolectorNode = bv2bv(ctx, expr, sort)

    override fun float2IEEEbv(ctx: Btor, expr: BoolectorNode, sort: BoolectorSort): BoolectorNode = bv2bv(ctx, expr, sort)

    override fun float2float(ctx: Btor, expr: BoolectorNode, sort: BoolectorSort): BoolectorNode = bv2bv(ctx, expr, sort)

    override fun hash(ctx: Btor, expr: BoolectorNode): Int = expr.id

    override fun name(ctx: Btor, expr: BoolectorNode): String = expr.symbol

    override fun toString(ctx: Btor, expr: BoolectorNode) = expr.dumpSmt2() ?: "null"

    override fun simplify(ctx: Btor, expr: BoolectorNode): BoolectorNode = expr

    override fun equality(ctx: Btor, lhv: BoolectorNode, rhv: BoolectorNode): Boolean = lhv == rhv

    override fun makeVar(ctx: Btor, sort: BoolectorSort, name: String, fresh: Boolean): BoolectorNode = when {
        sort.isArraySort -> ArrayNode.arrayNode(sort.toArraySort(), name)
        else -> BitvecNode.`var`(sort.toBitvecSort(), name, fresh)
    }

    fun makeTrue(ctx: Btor) = trueExpr ?: run {
        trueExpr = BoolNode.constBool(ctx, true)
        trueExpr!!
    }

    fun makeFalse(ctx: Btor) = falseExpr ?: run {
        falseExpr = BoolNode.constBool(ctx, false)
        falseExpr!!
    }

    override fun makeBooleanConst(ctx: Btor, value: Boolean): BoolectorNode = when {
        value -> makeTrue(ctx)
        else -> makeFalse(ctx)
    }

    override fun makeIntConst(ctx: Btor, value: Short): BoolectorNode =
            BitvecNode.constInt(value.toInt(), BitvecSort.bitvecSort(ctx, WORD))

    override fun makeIntConst(ctx: Btor, value: Int): BoolectorNode =
            BitvecNode.constInt(value, BitvecSort.bitvecSort(ctx, WORD))

    override fun makeLongConst(ctx: Btor, value: Long): BoolectorNode =
            BitvecNode.constLong(value, BitvecSort.bitvecSort(ctx, DWORD))

    override fun makeNumericConst(ctx: Btor, sort: BoolectorSort, value: Long): BoolectorNode =
            BitvecNode.constLong(value, BitvecSort.bitvecSort(ctx, DWORD))

    override fun makeFloatConst(ctx: Btor, value: Float): BoolectorNode {
        val intValue = value.roundToInt()
        return makeIntConst(ctx, intValue)
    }

    override fun makeDoubleConst(ctx: Btor, value: Double): BoolectorNode {
        val longValue = value.roundToLong()
        return makeLongConst(ctx, longValue)
    }

    override fun makeConstArray(ctx: Btor, sort: BoolectorSort, expr: BoolectorNode): BoolectorNode =
            ArrayNode.constArrayNode(sort.toBitvecSort(), expr.toBitvecNode())

    override fun makeFunction(ctx: Btor, name: String, retSort: BoolectorSort, args: List<BoolectorSort>): BoolectorFunction {
        val sort = FunctionSort.functionSort(args.toTypedArray(), retSort)
        return UninterpretedFunction.func(name, sort)
    }

    override fun apply(ctx: Btor, f: BoolectorFunction, args: List<BoolectorNode>): BoolectorNode = f.apply(args)

    override fun negate(ctx: Btor, expr: BoolectorNode): BoolectorNode {
        return when {
            expr.isBoolNode -> expr.toBoolNode().not()
            expr.isBitvecNode -> expr.toBitvecNode().neg()
            else -> unreachable { log.error("Unimplemented operation negate") }
        }
    }

    override fun binary(ctx: Btor, opcode: Opcode, lhv: BoolectorNode, rhv: BoolectorNode): BoolectorNode = when (opcode) {
        Opcode.EQ -> when {
            lhv.isArrayNode && rhv.isArrayNode -> eq(lhv, rhv)
            lhv.width >= rhv.width -> eq(lhv, rhv.toBitvecNode(lhv.width))
            else -> eq(lhv.toBitvecNode(rhv.width), rhv)
        }

        Opcode.NEQ -> when {
            lhv.isArrayNode && rhv.isArrayNode -> neq(lhv, rhv)
            lhv.width >= rhv.width -> neq(lhv, rhv.toBitvecNode(lhv.width))
            else -> neq(lhv.toBitvecNode(rhv.width), rhv)
        }

        Opcode.ADD -> if (lhv.isBitvecNode && rhv.isBitvecNode) {
            if (lhv.width >= rhv.width) add((lhv.toBitvecNode()), rhv.toBitvecNode(lhv.width))
            else add(lhv.toBitvecNode(rhv.width), (rhv.toBitvecNode()))
        } else unreachable { log.error("Unexpected add arguments: $lhv and $rhv") }

        Opcode.SUB -> if (lhv.isBitvecNode && rhv.isBitvecNode) {
            if (lhv.width >= rhv.width) sub((lhv.toBitvecNode()), rhv.toBitvecNode(lhv.width))
            else sub(lhv.toBitvecNode(rhv.width), (rhv.toBitvecNode()))
        } else unreachable { log.error("Unexpected sub arguments: $lhv and $rhv") }

        Opcode.MUL -> if (lhv.isBitvecNode && rhv.isBitvecNode) {
            if (lhv.width >= rhv.width) mul((lhv.toBitvecNode()), rhv.toBitvecNode(lhv.width))
            else mul(lhv.toBitvecNode(rhv.width), (rhv.toBitvecNode()))
        } else unreachable { log.error("Unexpected mul arguments: $lhv and $rhv") }

        Opcode.DIVIDE -> if (lhv.isBitvecNode && rhv.isBitvecNode) {
            if (lhv.width >= rhv.width) sdiv((lhv.toBitvecNode()), rhv.toBitvecNode(lhv.width))
            else sdiv(lhv.toBitvecNode(rhv.width), (rhv.toBitvecNode()))
        } else unreachable { log.error("Unexpected divide arguments: $lhv and $rhv") }

        Opcode.MOD -> if (lhv.isBitvecNode && rhv.isBitvecNode) {
            if (lhv.width >= rhv.width) smod((lhv.toBitvecNode()), rhv.toBitvecNode(lhv.width))
            else smod(lhv.toBitvecNode(rhv.width), (rhv.toBitvecNode()))
        } else unreachable { log.error("Unexpected mod arguments: $lhv and $rhv") }

        Opcode.GT -> if (lhv.isBitvecNode && rhv.isBitvecNode) {
            if (lhv.width >= rhv.width) gt((lhv.toBitvecNode()), rhv.toBitvecNode(lhv.width))
            else gt(lhv.toBitvecNode(rhv.width), (rhv.toBitvecNode()))
        } else unreachable { log.error("Unexpected gt arguments: $lhv and $rhv") }

        Opcode.GE -> if (lhv.isBitvecNode && rhv.isBitvecNode) {
            if (lhv.width >= rhv.width) ge((lhv.toBitvecNode()), rhv.toBitvecNode(lhv.width))
            else ge(lhv.toBitvecNode(rhv.width), (rhv.toBitvecNode()))
        } else unreachable { log.error("Unexpected ge arguments: $lhv and $rhv") }

        Opcode.LT -> if (lhv.isBitvecNode && rhv.isBitvecNode) {
            if (lhv.width >= rhv.width) lt((lhv.toBitvecNode()), rhv.toBitvecNode(lhv.width))
            else lt(lhv.toBitvecNode(rhv.width), (rhv.toBitvecNode()))
        } else unreachable { log.error("Unexpected lt arguments: $lhv and $rhv") }

        Opcode.LE -> if (lhv.isBitvecNode && rhv.isBitvecNode) {
            if (lhv.width >= rhv.width) le((lhv.toBitvecNode()), rhv.toBitvecNode(lhv.width))
            else le(lhv.toBitvecNode(rhv.width), (rhv.toBitvecNode()))
        } else unreachable { log.error("Unexpected le arguments: $lhv and $rhv") }

        Opcode.SHL -> if (lhv.isBitvecNode && rhv.isBitvecNode) {
            if (lhv.width >= rhv.width) shl((lhv.toBitvecNode()), rhv.toBitvecNode(lhv.width))
            else shl(lhv.toBitvecNode(rhv.width), (rhv.toBitvecNode()))
        } else unreachable { log.error("Unexpected shl arguments: $lhv and $rhv") }

        Opcode.SHR -> if (lhv.isBitvecNode && rhv.isBitvecNode) {
            if (lhv.width >= rhv.width) lshr((lhv.toBitvecNode()), rhv.toBitvecNode(lhv.width))
            else lshr(lhv.toBitvecNode(rhv.width), (rhv.toBitvecNode()))
        } else unreachable { log.error("Unexpected shr arguments: $lhv and $rhv") }

        Opcode.ASHR -> if (lhv.isBitvecNode && rhv.isBitvecNode) {
            if (lhv.width >= rhv.width) ashr(lhv.toBitvecNode(), rhv.toBitvecNode(lhv.width))
            else ashr(lhv.toBitvecNode(rhv.width), rhv.toBitvecNode())
        } else unreachable { log.error("Unexpected ashr arguments: $lhv and $rhv") }

        Opcode.AND -> when {
            (lhv.isBoolNode && rhv.isBoolNode) -> and(lhv.toBoolNode(), rhv.toBoolNode())
            !(lhv.isArrayNode && rhv.isArrayNode) -> {
                if (lhv.width >= rhv.width) and(lhv.toBitvecNode(), rhv.toBitvecNode(lhv.width))
                else
                    and(lhv.toBitvecNode(rhv.width), rhv.toBitvecNode())
            }
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.OR -> when {
            (lhv.isBoolNode && rhv.isBoolNode) -> or(lhv.toBoolNode(), rhv.toBoolNode())
            !(lhv.isArrayNode && rhv.isArrayNode) -> {
                if (lhv.width >= rhv.width) or(lhv.toBitvecNode(), rhv.toBitvecNode(lhv.width))
                else or(lhv.toBitvecNode(rhv.width), rhv.toBitvecNode())
            }
            else -> unreachable { log.error("Unexpected or arguments: $lhv or $rhv") }
        }
        Opcode.XOR -> when {
            (lhv.isBoolNode && rhv.isBoolNode) -> xor(lhv.toBoolNode(), rhv.toBoolNode())
            !(lhv.isArrayNode && rhv.isArrayNode) -> {
                if (lhv.width >= rhv.width) xor(lhv.toBitvecNode(), rhv.toBitvecNode(lhv.width))
                else xor(lhv.toBitvecNode(rhv.width), rhv.toBitvecNode())
            }
            else -> unreachable { log.error("Unexpected xor arguments: $lhv xor $rhv") }
        }
        Opcode.IMPLIES -> implies(lhv.toBoolNode(), rhv.toBoolNode())
        Opcode.IFF -> iff(lhv.toBoolNode(), rhv.toBoolNode())
        Opcode.CONCAT -> concat(lhv.toBitvecNode(), rhv.toBitvecNode())
    }

    private fun concat(lhv: BitvecNode, rhv: BitvecNode): BoolectorNode = (lhv.toBitvecNode()).concat((rhv.toBitvecNode()))

    private fun iff(lhv: BoolNode, rhv: BoolNode): BoolectorNode = lhv.iff(rhv)

    private fun implies(lhv: BoolNode, rhv: BoolNode): BoolectorNode = lhv.implies(rhv)

    private fun xor(lhv: BitvecNode, rhv: BitvecNode): BoolectorNode = lhv.xor(rhv)

    private fun xor(lhv: BoolNode, rhv: BoolNode): BoolectorNode = lhv.xor(rhv)

    private fun or(lhv: BitvecNode, rhv: BitvecNode): BoolectorNode = lhv.or(rhv)

    private fun or(lhv: BoolNode, rhv: BoolNode): BoolectorNode = lhv.or(rhv)

    private fun and(lhv: BitvecNode, rhv: BitvecNode): BoolectorNode = lhv.and(rhv)

    private fun and(lhv: BoolNode, rhv: BoolNode): BoolectorNode = lhv.and(rhv)

    private fun ashr(lhv: BitvecNode, rhv: BitvecNode): BoolectorNode = lhv.sra(rhv)

    private fun lshr(lhv: BitvecNode, rhv: BitvecNode): BoolectorNode = lhv.srl(rhv)

    private fun shl(lhv: BitvecNode, rhv: BitvecNode): BoolectorNode = lhv.sll(rhv)

    private fun le(lhv: BitvecNode, rhv: BitvecNode): BoolectorNode = lhv.slte(rhv)

    private fun lt(lhv: BitvecNode, rhv: BitvecNode): BoolectorNode = lhv.slt(rhv)

    private fun ge(lhv: BitvecNode, rhv: BitvecNode): BoolectorNode = lhv.sgte(rhv)

    private fun gt(lhv: BitvecNode, rhv: BitvecNode): BoolectorNode = lhv.sgt(rhv)

    private fun smod(lhv: BitvecNode, rhv: BitvecNode): BoolectorNode = lhv.smod(rhv)

    private fun sdiv(lhv: BitvecNode, rhv: BitvecNode): BoolectorNode = lhv.sdiv(rhv)

    private fun mul(lhv: BitvecNode, rhv: BitvecNode): BoolectorNode = lhv.mul(rhv)

    private fun sub(lhv: BitvecNode, rhv: BitvecNode): BoolectorNode = lhv.sub(rhv)

    private fun add(lhv: BitvecNode, rhv: BitvecNode): BoolectorNode = lhv.add(rhv)

    private fun neq(lhv: BoolectorNode, rhv: BoolectorNode): BoolectorNode = lhv.eq(rhv).not()

    private fun eq(lhv: BoolectorNode, rhv: BoolectorNode): BoolectorNode = lhv.eq(rhv)

    private fun bv2bv(ctx: Btor, expr: BoolectorNode, castSize: Int): BitvecNode {
        val curSize = expr.sort.toBitvecSort().width
        return when {
            curSize == castSize -> expr.toBitvecNode()
            curSize < castSize -> sext(ctx, castSize, expr).toBitvecNode()
            else -> extract(ctx, expr, high = castSize - 1, low = 0).toBitvecNode()
        }
    }

    override fun conjunction(ctx: Btor, vararg exprs: BoolectorNode): BoolectorNode {
        var ans = BoolNode.constBool(ctx, true)
        for (i in exprs) ans = ans.and(i.toBoolNode())
        return ans
    }

    override fun conjunction(ctx: Btor, exprs: Collection<BoolectorNode>): BoolectorNode {
        var ans = BoolNode.constBool(ctx, true)
        for (i in exprs) ans = ans.and(i.toBoolNode())
        return ans
    }

    override fun zext(ctx: Btor, n: Int, expr: BoolectorNode): BoolectorNode = expr.toBitvecNode().uext(n)

    override fun sext(ctx: Btor, n: Int, expr: BoolectorNode): BoolectorNode {
        // This is generally fucked up
        // sext on bitvector 1 should not count the first bit as sign
        val bv = expr.toBitvecNode()
        return when (bv.width) {
            1 -> zext(ctx, n, expr)
            else -> expr.toBitvecNode().sext(n)
        }
    }

    override fun load(ctx: Btor, array: BoolectorNode, index: BoolectorNode): BoolectorNode =
            array.toArrayNode().read(bv2bv(ctx, index, array.toArrayNode().indexWidth))

    override fun store(ctx: Btor, array: BoolectorNode, index: BoolectorNode, value: BoolectorNode): BoolectorNode =
            array.toArrayNode().write(bv2bv(ctx, index, array.toArrayNode().indexWidth), value.toBitvecNode())

    override fun ite(ctx: Btor, cond: BoolectorNode, lhv: BoolectorNode, rhv: BoolectorNode): BoolectorNode =
            lhv.ite(cond.toBoolNode(), rhv)

    override fun extract(ctx: Btor, bv: BoolectorNode, high: Int, low: Int): BoolectorNode =
            bv.toBitvecNode().slice(high, low)

    override fun forAll(ctx: Btor, sorts: List<BoolectorSort>, body: (List<BoolectorNode>) -> BoolectorNode): BoolectorNode =
            BitvecNode.constBitvec(ctx, "1")

    override fun forAll(ctx: Btor,
                        sorts: List<BoolectorSort>,
                        body: (List<BoolectorNode>) -> BoolectorNode,
                        patternGenerator: (List<BoolectorNode>) -> List<FunctionDecl.FunctionParam>): BoolectorNode =
            BitvecNode.constBitvec(ctx, "1")
}
