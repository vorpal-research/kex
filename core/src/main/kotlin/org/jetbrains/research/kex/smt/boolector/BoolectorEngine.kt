package org.jetbrains.research.kex.smt.boolector


import org.jetbrains.research.boolector.*
import org.jetbrains.research.boolector.Function
import org.jetbrains.research.kex.smt.SMTEngine
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object BoolectorEngine : SMTEngine<Btor, BoolectorNode, BoolectorSort, Function, BoolectorFun.FuncParam>() {
    override fun makeBound(ctx: Btor, size: Int, sort: BoolectorSort): BoolectorNode = BitvecNode.constBitvec("1")

    override fun makePattern(ctx: Btor, expr: BoolectorNode): BoolectorFun.FuncParam = BoolectorFun.FuncParam.param(expr.sort, "d")

    override fun getSort(ctx: Btor, expr: BoolectorNode): BoolectorSort = expr.sort

    override fun getBoolSort(ctx: Btor): BoolectorSort = BoolSort.boolSort()

    override fun getBVSort(ctx: Btor, size: Int): BoolectorSort = BitvecSort.bitvecSort(size)

    override fun getFloatSort(ctx: Btor): BoolectorSort = BitvecSort.bitvecSort(32)

    override fun getDoubleSort(ctx: Btor): BoolectorSort = BitvecSort.bitvecSort(64)
    //??????????
    override fun getArraySort(ctx: Btor, domain: BoolectorSort, range: BoolectorSort): BoolectorSort =
            ArraySort.arraySort(domain, range)

    override fun isBoolSort(ctx: Btor, sort: BoolectorSort): Boolean = sort.isBoolSort

    override fun isBVSort(ctx: Btor, sort: BoolectorSort): Boolean = sort.isBitvecSort

    override fun isFloatSort(ctx: Btor, sort: BoolectorSort): Boolean = sort.isBitvecSort && sort.toBitvecSort().width == 32

    override fun isDoubleSort(ctx: Btor, sort: BoolectorSort): Boolean = sort.isBitvecSort && sort.toBitvecSort().width == 64

    override fun isArraySort(ctx: Btor, sort: BoolectorSort): Boolean = sort.isArraySort

    override fun bvBitsize(ctx: Btor, sort: BoolectorSort): Int = sort.toBitvecSort().width

    override fun floatEBitsize(ctx: Btor, sort: BoolectorSort): Int = 10

    override fun floatSBitsize(ctx: Btor, sort: BoolectorSort): Int = 10

    override fun bool2bv(ctx: Btor, expr: BoolectorNode, sort: BoolectorSort): BoolectorNode =
            bv2bv(ctx, expr, sort)

    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!?
    override fun bv2bool(ctx: Btor, expr: BoolectorNode): BoolectorNode =
            binary(ctx, Opcode.NEQ, expr, BitvecNode.constLong(0L, expr.sort.toBitvecSort()))


    override fun bv2bv(ctx: Btor, expr: BoolectorNode, sort: BoolectorSort): BoolectorNode =
            bv2bv(ctx, expr, sort.toBitvecSort().width)

    override fun bv2float(ctx: Btor, expr: BoolectorNode, sort: BoolectorSort): BoolectorNode = bv2bv(ctx, expr, sort)

    override fun float2bv(ctx: Btor, expr: BoolectorNode, sort: BoolectorSort): BoolectorNode = bv2bv(ctx, expr, sort)

    override fun float2float(ctx: Btor, expr: BoolectorNode, sort: BoolectorSort): BoolectorNode = bv2bv(ctx, expr, sort)

    //$
    override fun hash(ctx: Btor, expr: BoolectorNode): Int = expr.id

    override fun name(ctx: Btor, expr: BoolectorNode): String = expr.symbol

    override fun toString(ctx: Btor, expr: BoolectorNode) = expr.symbol

    //$
    override fun simplify(ctx: Btor, expr: BoolectorNode): BoolectorNode = expr

    //$$$$
    override fun equality(ctx: Btor, lhv: BoolectorNode, rhv: BoolectorNode): Boolean = lhv == rhv

    //$
    override fun makeVar(ctx: Btor, sort: BoolectorSort, name: String, fresh: Boolean): BoolectorNode = when {
        sort.isArraySort -> ArrayNode.arrayNode(sort.toArraySort(), name)
        else -> BitvecNode.`var`(sort.toBitvecSort(), name, fresh)
    }

    override fun makeBooleanConst(ctx: Btor, value: Boolean): BoolectorNode = BoolNode.constBool(value)

    override fun makeIntConst(ctx: Btor, value: Short): BoolectorNode = BitvecNode.constInt(value.toInt(), BitvecSort.bitvecSort(WORD))

    override fun makeIntConst(ctx: Btor, value: Int): BoolectorNode = BitvecNode.constInt(value, BitvecSort.bitvecSort(WORD))

    //$
    override fun makeLongConst(ctx: Btor, value: Long): BoolectorNode = BitvecNode.constLong(value, BitvecSort.bitvecSort(DWORD))

    override fun makeNumericConst(ctx: Btor, sort: BoolectorSort, value: Long): BoolectorNode = BitvecNode.constLong(value, BitvecSort.bitvecSort(DWORD))

    override fun makeFloatConst(ctx: Btor, value: Float): BoolectorNode {
        val intValue = value.roundToInt()
        return makeIntConst(ctx, intValue)
    }

    override fun makeDoubleConst(ctx: Btor, value: Double): BoolectorNode {
        val longValue = value.roundToLong()
        return makeLongConst(ctx, longValue)
    }

    //$$$
    override fun makeConstArray(ctx: Btor, sort: BoolectorSort, expr: BoolectorNode): BoolectorNode =
            ArrayNode.constArrayNode(sort.toBitvecSort(), expr.toBitvecNode())

    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    override fun makeFunction(ctx: Btor, name: String, retSort: BoolectorSort, args: List<BoolectorSort>): Function {
        val param = args.map { BoolectorFun.FuncParam.param(it, null) }
        return Function.func(BitvecNode.zero(retSort.toBitvecSort()), param)
    }

    //$$$
    override fun apply(ctx: Btor, f: Function, args: List<BoolectorNode>): BoolectorNode = f.apply(args)

    //$$
    override fun negate(ctx: Btor, expr: BoolectorNode): BoolectorNode {
        return when {
            expr.isBoolNode -> expr.toBoolNode().not()
            expr.isBitvecNode -> expr.toBitvecNode().neg()
            else -> unreachable { log.error("Unimplemented operation negate") }
        }
    }

    override fun binary(ctx: Btor, opcode: Opcode, lhv: BoolectorNode, rhv: BoolectorNode): BoolectorNode = when (opcode) {
        Opcode.EQ -> eq(ctx, lhv, rhv)
        Opcode.NEQ -> neq(lhv, rhv)
        Opcode.ADD -> if (lhv.isBitvecNode && rhv.isBitvecNode) add(lhv.toBitvecNode(), rhv.toBitvecNode())
        else unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }

        Opcode.SUB -> if (lhv.isBitvecNode && rhv.isBitvecNode) sub(lhv.toBitvecNode(), rhv.toBitvecNode())
        else unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }

        Opcode.MUL -> if (lhv.isBitvecNode && rhv.isBitvecNode) mul(lhv.toBitvecNode(), rhv.toBitvecNode())
        else unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }

        Opcode.DIVIDE -> if (lhv.isBitvecNode && rhv.isBitvecNode) sdiv(lhv.toBitvecNode(), rhv.toBitvecNode())
        else unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }

        Opcode.MOD -> if (lhv.isBitvecNode && rhv.isBitvecNode) smod(lhv.toBitvecNode(), rhv.toBitvecNode())
        else unreachable { log.error("Unexpected mod arguments: $lhv and $rhv") }

        Opcode.GT -> if (lhv.isBitvecNode && rhv.isBitvecNode) gt(lhv.toBitvecNode(), rhv.toBitvecNode())
        else unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }

        Opcode.GE -> if (lhv.isBitvecNode && rhv.isBitvecNode) ge(lhv.toBitvecNode(), rhv.toBitvecNode())
        else unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }

        Opcode.LT -> if (lhv.isBitvecNode && rhv.isBitvecNode) lt(lhv.toBitvecNode(), rhv.toBitvecNode())
        else unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }

        Opcode.LE -> if (lhv.isBitvecNode && rhv.isBitvecNode) le(lhv.toBitvecNode(), rhv.toBitvecNode())
        else unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }

        Opcode.SHL -> shl(lhv.toBitvecNode(), rhv.toBitvecNode())
        Opcode.SHR -> lshr(lhv.toBitvecNode(), rhv.toBitvecNode())
        Opcode.ASHR -> ashr(lhv.toBitvecNode(), rhv.toBitvecNode())
        Opcode.AND -> when {
            lhv.isBoolNode && rhv.isBoolNode -> and(lhv.toBoolNode(), rhv.toBoolNode())
            lhv.isBitvecNode && rhv.isBitvecNode -> and(lhv.toBitvecNode(), rhv.toBitvecNode())
            else -> unreachable { log.error("Unexpected and arguments: $lhv and $rhv") }
        }
        Opcode.OR -> when {
            lhv.isBoolNode && rhv.isBoolNode -> or(lhv.toBoolNode(), rhv.toBoolNode())
            lhv.isBitvecNode && rhv.isBitvecNode -> or(lhv.toBitvecNode(), rhv.toBitvecNode())
            else -> unreachable { log.error("Unexpected or arguments: $lhv or $rhv") }
        }
        Opcode.XOR -> when {
            lhv.isBoolNode && rhv.isBoolNode -> xor(lhv.toBoolNode(), rhv.toBoolNode())
            lhv.isBitvecNode && rhv.isBitvecNode -> xor(lhv.toBitvecNode(), rhv.toBitvecNode())
            lhv.isBoolNode && rhv.isBitvecNode -> xor(bool2bv(ctx, lhv, rhv.sort.toBitvecSort()), rhv.toBitvecNode())
            lhv.isBitvecNode && rhv.isBoolNode -> xor(lhv.toBitvecNode(), bool2bv(ctx, rhv, lhv.sort.toBitvecSort()))
            else -> unreachable { log.error("Unexpected xor arguments: $lhv xor $rhv") }
        }
        Opcode.IMPLIES -> implies(lhv.toBoolNode(), rhv.toBoolNode())
        Opcode.IFF -> iff(lhv.toBoolNode(), rhv.toBoolNode())
        Opcode.CONCAT -> concat(lhv.toBitvecNode(), rhv.toBitvecNode())

    }

    private fun concat(lhv: BitvecNode, rhv: BitvecNode): BoolectorNode = lhv.toBitvecNode().concat(rhv.toBitvecNode())

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

    private fun bv2bv(ctx: Btor, expr: BoolectorNode, castSize: Int): BitvecNode {
        val curSize = expr.sort.toBitvecSort().width
        return when {
            curSize == castSize -> expr.toBitvecNode()
            curSize < castSize -> sext(ctx, castSize, expr).toBitvecNode()
            else -> extract(ctx, expr, high = castSize - 1, low = 0).toBitvecNode()
        }
    }

    private fun bool2bv(ctx: Btor, expr: BoolectorNode, sort: BitvecSort): BitvecNode {
        val castSize = sort.width
        return bv2bv(ctx, expr, castSize)
    }

    private fun eq(ctx: Btor, lhv: BoolectorNode, rhv: BoolectorNode): BoolectorNode {
        val lhvSort = lhv.sort.toBitvecSort()
        val lhvWidth = lhvSort.width
        val rhvSort = rhv.sort.toBitvecSort()
        val rhvWidth = rhvSort.width
        return when {
            lhvWidth > rhvWidth -> {
                lhv.eq(sext(ctx, lhvWidth - rhvWidth, rhv))
            }
            lhvWidth < rhvWidth -> {
                rhv.eq(sext(ctx, rhvWidth - lhvWidth, lhv))
            }
            else -> lhv.eq(rhv)
        }
    }


    override fun conjunction(ctx: Btor, vararg exprs: BoolectorNode): BoolectorNode {
        var ans = BoolNode.constBool(true)
        for (i in exprs) ans = ans.and(i.toBoolNode())
        return ans
    }

    override fun conjunction(ctx: Btor, exprs: Collection<BoolectorNode>): BoolectorNode {
        var ans = BoolNode.constBool(true)
        for (i in exprs) ans = ans.and(i.toBoolNode())
        return ans
    }

    override fun zext(ctx: Btor, n: Int, expr: BoolectorNode): BoolectorNode = expr.toBitvecNode().uext(n  - expr.width)

    override fun sext(ctx: Btor, n: Int, expr: BoolectorNode): BoolectorNode {
        // This is generally fucked up
        // sext on bitvector 1 should not count the first bit as sign
        val bv = expr.toBitvecNode()
        return when (bv.width) {
            1 -> zext(ctx, n, expr)
            else -> expr.toBitvecNode().sext(n - expr.width)
        }
    }

    override fun load(ctx: Btor, array: BoolectorNode, index: BoolectorNode): BoolectorNode =
            array.toArrayNode().read(bv2bv(ctx, index, array.toArrayNode().indexWidth))

    override fun store(ctx: Btor, array: BoolectorNode, index: BoolectorNode, value: BoolectorNode): BoolectorNode =
            array.toArrayNode().write(bv2bv(ctx, index, array.toArrayNode().indexWidth), value.toBitvecNode())

    override fun ite(ctx: Btor, cond: BoolectorNode, lhv: BoolectorNode, rhv: BoolectorNode): BoolectorNode =
            lhv.ite(cond.toBoolNode(), rhv)

    override fun extract(ctx: Btor, bv: BoolectorNode, high: Int, low: Int): BoolectorNode = bv.toBitvecNode().slice(high, low)

    override fun forAll(ctx: Btor, sorts: List<BoolectorSort>, body: (List<BoolectorNode>) -> BoolectorNode): BoolectorNode =
            BitvecNode.constBitvec("1")

    override fun forAll(ctx: Btor, sorts: List<BoolectorSort>, body: (List<BoolectorNode>) -> BoolectorNode, patternGenerator: (List<BoolectorNode>) -> List<BoolectorFun.FuncParam>): BoolectorNode =
            BitvecNode.constBitvec("1")
}
