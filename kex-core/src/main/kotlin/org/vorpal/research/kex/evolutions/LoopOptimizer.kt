package org.vorpal.research.kex.evolutions


import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.BodyBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.MethodUsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.instruction.BinaryInst
import org.vorpal.research.kfg.ir.value.instruction.BinaryOpcode
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.PhiInst
import org.vorpal.research.kfg.ir.value.usageContext
import org.vorpal.research.kfg.type.IntType
import org.vorpal.research.kfg.visitor.Loop
import org.vorpal.research.kfg.visitor.LoopVisitor
import ru.spbstu.Apply
import ru.spbstu.Const
import ru.spbstu.Product
import ru.spbstu.Shift
import ru.spbstu.ShiftLeft
import ru.spbstu.ShiftRight
import ru.spbstu.Sum
import ru.spbstu.SymDouble
import ru.spbstu.SymRational
import ru.spbstu.Symbolic
import ru.spbstu.Var
import ru.spbstu.gcd

@Suppress("MemberVisibilityCanBePrivate", "unused")
open class LoopOptimizer(cm: ClassManager) : Evolutions(cm), LoopVisitor {
    protected lateinit var ctx: MethodUsageContext
    protected val phiToEvo = mutableMapOf<PhiInst, Symbolic>()
    protected val freshVars = mutableMapOf<Loop, Var>()
    override val preservesLoopInfo get() = false

    override fun cleanup() {}

    protected fun precalculateEvolutions(topLoops : List<Loop>) {
        cleanup()
        walkLoops(topLoops).forEach { loop ->
            loop
                .header
                .instructions
                .filterIsInstance<PhiInst>()
                .forEach {
                    loopPhis[it] = loop
                }
            loop.body.forEach { basicBlock ->
                basicBlock.forEach {
                    inst2loop.getOrPut(it) { loop }
                }
            }
        }
    }

    override fun visit(method: Method) = method.usageContext.use {
    }

    override fun visitLoop(loop: Loop) {
        super.visitLoop(loop)
        if (loop.allEntries.size != 1 || loop !in freshVars.keys) {
            return
        }
        insertInductive(loop)
        rebuild(loop)
        clearUnused(loop)
    }

    protected fun clearUnused(loop: Loop) = with(ctx){
        val unused = mutableListOf<Instruction>()
        for (b in loop.body) {
            for (i in b) {
                if (i is BinaryInst || i is PhiInst)
                    if (i.users.isEmpty()) {
                        unused += i
                    }
            }
            unused.forEach {
                it.clearAllUses()
                b -= it
            }
            unused.clear()
        }
    }

    protected fun insertBefore(block: BasicBlock, e: BasicBlock, loop: Loop) = with(ctx) {
        e.add(instructions.getJump(ctx, block))
        loop.addBlock(e)
        method.body.add(e)
        block.predecessors.forEach { preBlock ->
            e.addPredecessor(preBlock)
            e.addSuccessor(block)

            block.removePredecessor(preBlock)
            block.addPredecessor(e)

            preBlock.removeSuccessor(block)
            preBlock.addSuccessor(e)


            preBlock.remove(preBlock.instructions.last())
            preBlock.add(instructions.getJump(ctx, e))

        }
    }

    protected fun insertAfter(block: BasicBlock, e: BasicBlock, loop: Loop) = with(ctx) {
        val afterBlock = block.successors.first()
        insertBefore(afterBlock, e, loop)
    }

    protected fun insertInductive(loop: Loop) = with(ctx) {
        val one = values.getInt(1)
        val tmpPhi = instructions.getPhi(ctx, IntType, mapOf())

        val newInstruction = instructions.getBinary(ctx, BinaryOpcode.ADD, one, tmpPhi.get())
        val newPhi = instructions.getPhi(
            ctx,
            IntType,
            mapOf(Pair(loop.preheader, one), Pair(loop.latch, newInstruction.get()))
        )
        var2inst[freshVars[loop]!!] = newPhi
        inst2var[newPhi] = freshVars[loop]!!
        tmpPhi.replaceAllUsesWith(newPhi)
        tmpPhi.clearAllUses()
        loop.header.insertBefore(loop.header.first(), newPhi)

        val updater = BodyBlock("loop.updater")
        updater += newInstruction
        insertBefore(loop.latch, updater, loop)
    }

    private fun reconstructPhi(phi: PhiInst, collector: MutableList<Instruction>): Value? {
        val evo = phiToEvo[phi] ?: return null
        return evo.generateCode(collector)
    }

    private fun Symbolic.generateCode(collector: MutableList<Instruction>): Value? {
        return when (this) {
            is Sum -> this.generateCode(collector)
            is Const -> this.generateCode()
            is Var -> this.generateCode()
            is Shift -> this.generateCode(collector)
            is Apply -> null
            is Product -> this.generateCode(collector)
        }
    }

    private fun Sum.generateCode(collector: MutableList<Instruction>): Value? {
        val lcm = lcm((constant as? SymRational)?.den ?: 1, this.parts.values.fold(1L) { acc, v ->
            if (v is SymRational) lcm(acc, v.den) else acc
        })
        val results = mutableListOf<Value>()
        this.parts.forEach {
            val res = it.key.generateCode(collector) ?: return null
            val coefficient = when (val v = it.value) {
                is SymRational -> values.getInt((v * lcm).toInt())
                is SymDouble -> values.getDouble((v * lcm).toDouble())
            }
            val newInstruction =
                instructions.getBinary(ctx, BinaryOpcode.MUL, res, coefficient)
            collector.add(newInstruction)
            results.add(newInstruction.get())
        }
        val res = results.drop(1).fold(results[0]) { acc, v ->
            val newValue = instructions.getBinary(ctx, BinaryOpcode.ADD, acc, v)
            collector.add(newValue)
            newValue
        }
        if (constant.equals(0L)) {
            if (lcm == 1L) {
                return res
            }
            val divLcm = instructions.getBinary(ctx, BinaryOpcode.DIV, res, values.getInt(lcm.toInt()))
            collector.add(divLcm)
            return divLcm
        }
        val constantValue = when (val c = constant) {
            is SymRational -> values.getInt((c * lcm).toInt())
            is SymDouble -> values.getDouble((c * lcm).toDouble())
        }
        val addConst =
            instructions.getBinary(ctx, BinaryOpcode.ADD, res, constantValue)
        collector.add(addConst)
        if (lcm == 1L) {
            return addConst
        }
        val divLcm = instructions.getBinary(ctx, BinaryOpcode.DIV, addConst, values.getInt(lcm.toInt()))
        collector.add(divLcm)
        return divLcm
    }


    private fun Product.generateCode(collector: MutableList<Instruction>): Value? {
        val results = mutableListOf<Value>()
        this.parts.forEach {
            val v = it.value
            if (v !is SymRational || !v.isWhole()) return null
            val base = it.key.generateCode(collector) ?: return null
            var pre = base
            for (i in 1 until v.wholePart) {
                val newInst =
                    instructions.getBinary(ctx, BinaryOpcode.MUL, pre, base)
                collector.add(newInst)
                pre = newInst

            }
            results.add(pre)
        }
        val res = results.drop(1).fold(results[0]) { acc, v ->
            val newValue = instructions.getBinary(ctx, BinaryOpcode.MUL, acc, v)
            collector.add(newValue)
            newValue
        }
        when (val const = constant) {
            is SymRational -> {
                if (const.isWhole() && const.wholePart == 1L) {
                    return res
                }
                val mulConst =
                    instructions.getBinary(ctx, BinaryOpcode.MUL, res, values.getInt(const.num.toInt()))
                collector.add(mulConst)

                if (const.isWhole()) {
                    return mulConst
                }
                val divLcm =
                    instructions.getBinary(ctx, BinaryOpcode.DIV, mulConst, values.getInt(const.den.toInt()))
                collector.add(divLcm)
                return divLcm
            }
            is SymDouble -> {
                val mulConst =
                    instructions.getBinary(ctx, BinaryOpcode.MUL, res, values.getDouble(const.toDouble()))
                collector.add(mulConst)
                return mulConst
            }
        }
    }

    private fun Const.generateCode(): Value? =
        when (val v = this.value) {
            is SymRational -> values.getConstant(v.toLong())
            is SymDouble -> values.getConstant(v.toDouble())
        }

    private fun Var.generateCode(): Value {
        return var2inst[this]!!
    }

    private fun Apply.generateCode(collector: MutableList<Instruction>): Value? {
        return when (this) {
            is ShiftRight -> generateCode(collector)
            is ShiftLeft -> generateCode(collector)
            else -> null
        }
    }

    private fun ShiftLeft.generateCode(collector: MutableList<Instruction>): Value? {
        collector.add(
            instructions.getBinary(
                ctx,
                BinaryOpcode.SHL,
                base.generateCode(collector) ?: return null,
                shift.generateCode(collector) ?: return null
            )
        )
        return collector.last()
    }

    private fun ShiftRight.generateCode(collector: MutableList<Instruction>): Value? {
        collector.add(
            instructions.getBinary(
                ctx,
                BinaryOpcode.SHR,
                base.generateCode(collector) ?: return null,
                shift.generateCode(collector) ?: return null
            )
        )
        return collector.last()
    }

    private fun lcm(a: Long, b: Long): Long = (a / gcd(a, b)) * b

    protected fun rebuild(loop: Loop): Pair<List<Instruction>,List<Pair<PhiInst, Value>>>?   {
        val phis = loop.body.flatMap { it.instructions }.mapNotNull { it as? PhiInst }
            .mapNotNull { if (phiToEvo.containsKey(it)) it else null }
        val newBlock = mutableListOf<Instruction>()
        val phiList = mutableListOf<Pair<PhiInst, Value>>()
        return if (phis.all {
                val res = reconstructPhi(it, newBlock) ?: return@all false
                phiList.add(Pair(it, res))
                true
            }) {
//            phiList.forEach { it.first.replaceAllUsesWith(ctx, it.second) }
            newBlock to phiList
        } else {
            null
        }
    }
}
