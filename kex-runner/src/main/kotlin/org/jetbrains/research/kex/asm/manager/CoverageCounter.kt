package org.jetbrains.research.kex.asm.manager

import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.logging.log
import java.util.*

private val visibilityLevel by lazy { kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC) }

val Method.isImpactable: Boolean
    get() = when {
        this.isAbstract -> false
        this.isStaticInitializer -> false
        this.`class`.isSynthetic -> false
        this.isSynthetic -> false
        visibilityLevel > this.`class`.visibility -> false
        visibilityLevel > this.visibility -> false
        else -> true
    }

data class CoverageInfo(val bodyCoverage: Double, val fullCoverage: Double)

class CoverageCounter<T> private constructor(
    override val cm: ClassManager,
    private val tm: TraceManager<T>,
    val methodFilter: (Method) -> Boolean
) : MethodVisitor {
    val methodInfos = hashMapOf<Method, CoverageInfo>()

    constructor(cm: ClassManager, tm: TraceManager<T>) : this(cm, tm, { true })
    constructor(cm: ClassManager, tm: TraceManager<T>, pkg: Package) : this(
        cm,
        tm,
        { pkg.isParent(it.`class`.`package`) })

    constructor(cm: ClassManager, tm: TraceManager<T>, klass: Class) : this(
        cm,
        tm,
        { it.`class` == klass })

    constructor(cm: ClassManager, tm: TraceManager<T>, methods: Set<Method>) : this(
        cm,
        tm,
        { it in methods })

    val totalCoverage: CoverageInfo
        get() {
            if (methodInfos.isEmpty()) return CoverageInfo(0.0, 0.0)

            val numberOfMethods = methodInfos.size
            val (body, full) = methodInfos.values.reduce { acc, coverageInfo ->
                CoverageInfo(
                    acc.bodyCoverage + coverageInfo.bodyCoverage,
                    acc.fullCoverage + coverageInfo.fullCoverage
                )
            }

            return CoverageInfo(body / numberOfMethods, full / numberOfMethods)
        }

    private val Method.isInteresting: Boolean
        get() = when {
            this.isAbstract -> false
            this.isStaticInitializer -> false
            this.`class`.isSynthetic -> false
            this.isSynthetic -> false
            !this.hasBody -> false
            else -> true
        }

    override fun cleanup() {}

    override fun visit(method: Method) {
        if (!method.isInteresting) return
        if (!methodFilter(method)) return

        val bodyBlocks = method.bodyBlocks.filterNot { it.isUnreachable }.map { it.originalBlock }.toSet()
        val catchBlocks = method.catchBlocks.filterNot { it.isUnreachable }.map { it.originalBlock }.toSet()
        val bodyCovered = bodyBlocks.count { tm.isCovered(method, it) }
        val catchCovered = catchBlocks.count { tm.isCovered(method, it) }

        val info = CoverageInfo(
            (bodyCovered * 100).toDouble() / bodyBlocks.size,
            ((bodyCovered + catchCovered) * 100).toDouble() / (bodyBlocks.size + catchBlocks.size)
        )
        methodInfos[method] = info

        log.info(
            "Method $method coverage: " +
                    "body = ${String.format(Locale.ENGLISH, "%.2f", info.bodyCoverage)}; " +
                    "full = ${String.format(Locale.ENGLISH, "%.2f", info.fullCoverage)}"
        )
    }
}
