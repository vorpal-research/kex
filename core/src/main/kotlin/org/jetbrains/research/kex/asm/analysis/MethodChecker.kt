package org.jetbrains.research.kex.asm.analysis

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.asm.transform.TraceInstrumenter
import org.jetbrains.research.kex.driver.RandomDriver
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.smt.model.ModelRecoverer
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.runner.SimpleRunner
import org.jetbrains.research.kex.util.debug
import org.jetbrains.research.kex.util.getClass
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.tryOrNull
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.util.JarUtils
import org.jetbrains.research.kfg.visitor.MethodVisitor
import java.io.File
import java.net.URLClassLoader

class MethodChecker(
        override val cm: ClassManager,
        private val loader: ClassLoader,
        private val originalCM: ClassManager,
        private val target: File,
        private val psa: PredicateStateAnalysis) : MethodVisitor {
    private val tm = TraceManager
    private var state: State? = null

    private data class State(
            val `class`: Class,
            val method: Method,
            val loader: ClassLoader,
            val traces: List<Instruction>
    )

    private fun prepareMethodInfo(method: Method) {
        val originalClass = originalCM.getByName(method.`class`.fullname)
        val originalMethod = originalClass.getMethod(method.name, method.desc)
        val classFileName = "${target.canonicalPath}/${originalClass.fullname}.class"

        if (!method.isAbstract && !method.isConstructor) {
            val traceInstructions = TraceInstrumenter(originalCM).invoke(originalMethod)
            JarUtils.writeClass(cm, loader, originalClass, classFileName)
            val directory = URLClassLoader(arrayOf(target.toURI().toURL()))

            state = State(originalClass, originalMethod, directory, traceInstructions)
        }
    }

    override fun cleanup() {
        val `class` = state?.`class` ?: return
        val classFileName = "${target.canonicalPath}/${`class`.fullname}.class"

        state?.traces?.forEach { it.parent?.remove(it) }

        JarUtils.writeClass(cm, loader, `class`, classFileName)
        state = null
    }

    override fun visit(method: Method) {
        super.visit(method)
        prepareMethodInfo(method)

        if (method.isAbstract || method.isConstructor) return

        log.debug(method)
        log.debug(method.print())
        log.debug()

//        val returnBlock = method.firstOrNull { it.any { inst -> inst is ReturnInst } } ?: return
//        coverBlock(method, returnBlock)
        // check body blocks backwards, to reduce number of runs

        val blockMappings = LoopDeroller.blockMapping.getOrPut(method, ::mutableMapOf)
        for (block in method.bodyBlocks.reversed()) {
            val originalBlock = blockMappings[block] ?: continue
            if (tm.isCovered(method, originalBlock)) continue

            coverBlock(method, block)

            log.debug("Block ${block.name} is covered = ${tm.isCovered(method, originalBlock)}")
            log.debug()
        }
        cleanup()
    }

    private fun coverBlock(method: Method, block: BasicBlock) {
        val checker = Checker(method, state!!.loader, psa)

        log.debug("Checking reachability of ${block.terminator.print()}")
        val result = checker.checkReachable(block.terminator)
        log.debug(result)

        when (result) {
            is Result.SatResult -> {
                log.debug(result.model)
                val model = ModelRecoverer(method, result.model, state!!.loader).apply()
                log.debug("Recovered: ${tryOrNull { model.toString() }}")

                tryOrNull {
                    val instance = when (model.instance) {
                        null ->
                            if (method.isStatic) null
                            else RandomDriver.generate(getClass(types.getRefType(method.`class`), state!!.loader))
                        else -> model.instance
                    }

                    val trace = SimpleRunner(method, state!!.loader).invoke(instance, model.arguments.toTypedArray())
                    tm.addTrace(method, trace)
                }
            }
            is Result.UnsatResult -> log.debug("Instruction ${block.terminator.print()} is unreachable")
            is Result.UnknownResult -> Unit
        }
    }
}