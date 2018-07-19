package org.jetbrains.research.kex

import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.smt.SMTProxySolver
import org.jetbrains.research.kex.state.term.ConstIntTerm
import org.jetbrains.research.kex.state.term.TermFactory
import org.jetbrains.research.kex.state.transformer.ConstantPropagator
import org.jetbrains.research.kex.state.transformer.MemorySpacer
import org.jetbrains.research.kex.state.transformer.StateOptimizer
import org.jetbrains.research.kex.util.debug
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Location
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.TypeFactory
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

fun Method.getBBbyLoc(location: Location) = basicBlocks.find { it.front().location == location }

class BasicTest : KexTest() {
    val `class` = CM.getByName("$packageName/BasicTests")

    @Test
    fun testPlain() {
        val method = `class`.getMethod("testPlain", "(II)I")

        val psa = getPSA(method)
        val state = psa.getInstructionState(method.last().last())
        val optimized = StateOptimizer().transform(state)

        val propagated = ConstantPropagator().transform(optimized)
        val memspaced = MemorySpacer(propagated).transform(propagated)

        log.run {
            debug(method)
            debug(memspaced)
        }

        val result = SMTProxySolver().isReachable(memspaced)
        assertTrue(result is Result.SatResult)

        val model = (result as Result.SatResult).model
        log.debug(model)

        val arg0 = model.assignments[TermFactory.getArgument(TypeFactory.getIntType(), 0)]
        val arg1 = model.assignments[TermFactory.getArgument(TypeFactory.getIntType(), 1)]
        val retval = model.assignments[TermFactory.getReturn(method)]
        assertNotNull(arg0)
        assertNotNull(arg1)
        assertNotNull(retval)
    }

    @Test
    fun testIf() {
        val method = `class`.getMethod("testIf", "(II)I")
        log.debug("Checking $method")
        log.debug(method.print())

        val psa = getPSA(method)
        val `true` = method.getBBbyLoc(Location(Package(packageName), "basics.kt", 15))
        val `false` = method.getBBbyLoc(Location(Package(packageName), "basics.kt", 17))
        assertNotNull(`true`)
        assertNotNull(`false`)
        `true` as BasicBlock
        `false` as BasicBlock

        val checkReachable = { bb: BasicBlock ->
            log.debug("Checking ${bb.name} reachability")
            val state = psa.getInstructionState(bb.getTerminator())
            val optimized = StateOptimizer().transform(state)

            val propagated = ConstantPropagator().transform(optimized)
            val memspaced = MemorySpacer(propagated).transform(propagated)

            log.run {
                debug(method)
                debug(memspaced)
            }

            SMTProxySolver().isReachable(memspaced)
        }

        val arg0 = TermFactory.getArgument(TypeFactory.getIntType(), 0)
        val arg1 = TermFactory.getArgument(TypeFactory.getIntType(), 1)

        val trueResult = checkReachable(`true`)
        assertTrue(trueResult is Result.SatResult)
        val trueModel = (trueResult as Result.SatResult).model
        log.debug(trueModel)
        assertNotNull(trueModel.assignments[arg0])
        assertNotNull(trueModel.assignments[arg1])
        assertTrue((trueModel.assignments[arg0] as ConstIntTerm).value > (trueModel.assignments[arg1] as ConstIntTerm).value)

        val falseResult = checkReachable(`false`)
        assertTrue(falseResult is Result.SatResult)
        val falseModel = (falseResult as Result.SatResult).model
        log.debug(falseModel)
        assertNotNull(falseModel.assignments[arg0])
        assertNotNull(falseModel.assignments[arg1])
        assertTrue((falseModel.assignments[arg0] as ConstIntTerm).value <= (falseModel.assignments[arg1] as ConstIntTerm).value)

        val endResult = checkReachable(method.basicBlocks.last())
        assertTrue(endResult is Result.SatResult)
        val endModel = (endResult as Result.SatResult).model
        log.debug(endModel)
        assertNotNull(endModel.assignments[arg0])
        assertNotNull(endModel.assignments[arg1])
    }

    @Test
    fun testLoop() {
        val method = `class`.getMethod("testLoop", "(II)I")
        log.debug("Checking $method")
        log.debug(method.print())

        val psa = getPSA(method)
        val `true` = method.getBBbyLoc(Location(Package(packageName), "basics.kt", 28))
        val `false` = method.getBBbyLoc(Location(Package(packageName), "basics.kt", 30))
        val end = method.getBBbyLoc(Location(Package(packageName), "basics.kt", 32))
        assertNotNull(`true`)
        assertNotNull(`false`)
        assertNotNull(end)
        `true` as BasicBlock
        `false` as BasicBlock
        end as BasicBlock

        val checkReachable = { bb: BasicBlock ->
            log.debug("Checking ${bb.name} reachability")
            val state = psa.getInstructionState(bb.getTerminator())
            val optimized = StateOptimizer().transform(state)

            val propagated = ConstantPropagator().transform(optimized)
            val memspaced = MemorySpacer(propagated).transform(propagated)

            log.run {
                debug(method)
                debug(memspaced)
            }

            SMTProxySolver().isReachable(memspaced)
        }
        val trueResult = checkReachable(`true`)
        assertTrue(trueResult is Result.SatResult)
        val trueModel = (trueResult as Result.SatResult).model
        log.debug(trueModel)

        val falseResult = checkReachable(`false`)
        assertTrue(falseResult is Result.SatResult)
        val falseModel = (falseResult as Result.SatResult).model
        log.debug(falseModel)

        val endResult = checkReachable(end)
        assertTrue(endResult is Result.SatResult)
        val endModel = (endResult as Result.SatResult).model
        log.debug(endModel)
    }

    @Test
    fun testUnreachableIf() {
        val method = `class`.getMethod("testUnreachableIf", "(I)I")
        log.debug("Checking $method")
        log.debug(method.print())

        val psa = getPSA(method)
        val `true` = method.getBBbyLoc(Location(Package(packageName), "basics.kt", 42))
        val `false` = method.getBBbyLoc(Location(Package(packageName), "basics.kt", 39))
        val end = method.getBBbyLoc(Location(Package(packageName), "basics.kt", 45))
        assertNotNull(`true`)
        assertNotNull(`false`)
        assertNotNull(end)
        `true` as BasicBlock
        `false` as BasicBlock
        end as BasicBlock

        val checkReachable = { bb: BasicBlock ->
            log.debug("Checking ${bb.name} reachability")
            val state = psa.getInstructionState(bb.getTerminator())
            val optimized = StateOptimizer().transform(state)

            val propagated = ConstantPropagator().transform(optimized)
            val memspaced = MemorySpacer(propagated).transform(propagated)

            log.run {
                debug(method)
                debug(memspaced)
            }

            SMTProxySolver().isReachable(memspaced)
        }
        val trueResult = checkReachable(`true`)
        assertTrue(trueResult is Result.SatResult)
        val trueModel = (trueResult as Result.SatResult).model
        log.debug(trueModel)

        val falseResult = checkReachable(`false`)
        assertTrue(falseResult is Result.UnsatResult)

        val endResult = checkReachable(end)
        assertTrue(endResult is Result.SatResult)
        val endModel = (endResult as Result.SatResult).model
        log.debug(endModel)
    }

    @Test
    fun testUnreachableLoop() {
        val method = `class`.getMethod("testUnreachableLoop", "()I")
        log.debug("Checking $method")
        log.debug(method.print())

        val psa = getPSA(method)
        val `true` = method.getBBbyLoc(Location(Package(packageName), "basics.kt", 51))
        val `false` = method.getBBbyLoc(Location(Package(packageName), "basics.kt", 53))
        assertNotNull(`true`)
        assertNotNull(`false`)
        `true` as BasicBlock
        `false` as BasicBlock

        val checkReachable = { bb: BasicBlock ->
            log.debug("Checking ${bb.name} reachability")
            val state = psa.getInstructionState(bb.getTerminator())
            val optimized = StateOptimizer().transform(state)

            val propagated = ConstantPropagator().transform(optimized)
            val memspaced = MemorySpacer(propagated).transform(propagated)

            log.run {
                debug(method)
                debug(memspaced)
            }

            SMTProxySolver().isReachable(memspaced)
        }
        val trueResult = checkReachable(`true`)
        assertTrue(trueResult is Result.SatResult)
        val trueModel = (trueResult as Result.SatResult).model
        log.debug(trueModel)

        val falseResult = checkReachable(`false`)
        assertTrue(falseResult is Result.UnsatResult)
    }
}