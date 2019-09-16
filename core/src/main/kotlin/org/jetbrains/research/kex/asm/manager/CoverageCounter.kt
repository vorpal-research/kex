package org.jetbrains.research.kex.asm.manager

import org.jetbrains.research.kex.asm.analysis.isImpactable
import org.jetbrains.research.kex.asm.transform.originalBlock
import org.jetbrains.research.kex.trace.file.FileTraceManager
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.ClassVisitor

data class CoverageInfo(val bodyCoverage: Double, val fullCoverage: Double)

class CoverageCounter(override val cm: ClassManager) : ClassVisitor {
    val tm = FileTraceManager()
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

        for (method in `class`.methods.values) {
            if (method.isAbstract || method.isConstructor) continue
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
