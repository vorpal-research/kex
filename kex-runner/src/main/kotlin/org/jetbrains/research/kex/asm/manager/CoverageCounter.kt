package org.jetbrains.research.kex.asm.manager

import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.asm.transform.originalBlock
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.CallInst
import org.jetbrains.research.kfg.ir.value.instruction.FieldLoadInst
import org.jetbrains.research.kfg.ir.value.instruction.FieldStoreInst
import org.jetbrains.research.kfg.visitor.ClassVisitor

val Method.isImpactable: Boolean
    get() {
        when {
            this.isAbstract -> return false
            this.isStatic && this.argTypes.isEmpty() -> return false
            this.argTypes.isEmpty() -> {
                val thisVal = this.cm.value.getThis(this.`class`)
                for (inst in this.flatten()) {
                    when (inst) {
                        is FieldLoadInst -> if (inst.hasOwner && inst.owner == thisVal) return true
                        is FieldStoreInst -> if (inst.hasOwner && inst.owner == thisVal) return true
                        is CallInst -> if (!inst.isStatic && inst.callee == thisVal) return true
                    }
                }
                return false
            }
            else -> return true
        }
    }

data class CoverageInfo(val bodyCoverage: Double, val fullCoverage: Double)

class CoverageCounter<T>(override val cm: ClassManager, val tm: TraceManager<T>) : ClassVisitor {
    val methodInfos = hashMapOf<Method, CoverageInfo>()

    val totalCoverage: CoverageInfo
        get() {
            if (methodInfos.isEmpty()) return CoverageInfo(0.0, 0.0)

            val numberOfMethods = methodInfos.size
            val (body, full) = methodInfos.values.reduce { acc, coverageInfo ->
                CoverageInfo(
                        acc.bodyCoverage + coverageInfo.bodyCoverage,
                        acc.fullCoverage + coverageInfo.fullCoverage)
            }

            return CoverageInfo(body / numberOfMethods, full / numberOfMethods)
        }

    override fun cleanup() {}

    override fun visit(`class`: Class) {
        if (`class`.isSynthetic) return

        for (method in `class`.allMethods) {
            if (method.isAbstract || method.isStaticInitializer) continue
            if (!method.isImpactable) continue

            val bodyBlocks = method.bodyBlocks.map { it.originalBlock }.toSet()
            val catchBlocks = method.catchBlocks.map { it.originalBlock }.toSet()
            val bodyCovered = bodyBlocks.count { tm.isCovered(method, it) }
            val catchCovered = catchBlocks.count { tm.isCovered(method, it) }

            val info = CoverageInfo(
                    (bodyCovered * 100).toDouble() / bodyBlocks.size,
                    ((bodyCovered + catchCovered) * 100).toDouble() / (bodyBlocks.size + catchBlocks.size)
            )
            methodInfos[method] = info

            log.info("Method $method coverage: " +
                    "body = ${String.format("%.2f", info.bodyCoverage)}; " +
                    "full = ${String.format("%.2f", info.fullCoverage)}")
        }
    }
}
