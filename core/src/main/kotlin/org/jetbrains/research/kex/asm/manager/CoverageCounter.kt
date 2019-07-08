package org.jetbrains.research.kex.asm.manager

import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.ClassVisitor

class CoverageCounter(override val cm: ClassManager) : ClassVisitor {
    val tm = TraceManager
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

    data class CoverageInfo(val bodyCoverage: Double, val fullCoverage: Double)

    override fun cleanup() {}

    override fun visit(`class`: Class) {
        if (`class`.isSynthetic) return

        for (method in `class`.methods.values) {
            if (method.isAbstract || method.isConstructor) continue
            if (method.isStatic && method.argTypes.isEmpty()) continue

            val bodyBlocks = method.bodyBlocks
            val catchBlocks = method.catchBlocks
            val bodyCovered = bodyBlocks.count { tm.isCovered(it) }
            val catchCovered = catchBlocks.count { tm.isCovered(it) }

            val info = CoverageInfo(
                    (bodyCovered * 100).toDouble() / bodyBlocks.size,
                    ((bodyCovered + catchCovered) * 100).toDouble() / (bodyBlocks.size + catchBlocks.size)
            )
            methodInfos[method] = info

            log.info("Method $method coverage: body = ${info.bodyCoverage}; full = ${info.fullCoverage}")
        }
    }
}
