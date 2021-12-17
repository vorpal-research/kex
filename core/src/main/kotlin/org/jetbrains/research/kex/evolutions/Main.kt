package org.jetbrains.research.kex.evolutions

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.KfgConfig
import org.jetbrains.research.kfg.analysis.LoopAnalysis
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.container.JarContainer
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kfg.visitor.executePipeline
import kotlin.io.path.Path

class MethodPrinter(override val cm: ClassManager) : MethodVisitor {
    /**
     * should override this method and cleanup all the temporary info between visitor invocations
     */
    override fun cleanup() {}

    override fun visit(method: Method) {
        println("$method")
        super.visit(method)
    }

    override fun visitBasicBlock(bb: BasicBlock) {
        println("${bb.name}:")
        super.visitBasicBlock(bb)
    }

    override fun visitInstruction(inst: Instruction) {
        super.visitInstruction(inst)
        println("  ${inst.print()}")
    }
}

fun main() {

    val jar = JarContainer("src/main/resources/tests.jar", "shlTwice")

    val classManager = ClassManager(KfgConfig(Flags.readAll, true, verifyIR = false))
    classManager.initialize(jar)
    executePipeline(classManager, jar.pkg) {
        +LoopAnalysis(classManager)
        +LoopSimplifier(classManager)
        +MethodPrinter(classManager)
        +LoopOptimizer(classManager)
        +MethodPrinter(classManager)
    }
    jar.unpack(classManager, Path("unpack"), failOnError = true)
    jar.update(classManager, Path("rebuild"))
}
