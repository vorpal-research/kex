package org.vorpal.research.kex.evolutions

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.KfgConfig
import org.vorpal.research.kfg.analysis.LoopSimplifier
import org.vorpal.research.kfg.container.JarContainer
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.util.Flags
import org.vorpal.research.kfg.visitor.LoopAnalysis
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kfg.visitor.executePipeline
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
