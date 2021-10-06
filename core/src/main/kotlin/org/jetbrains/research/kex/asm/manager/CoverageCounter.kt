package org.jetbrains.research.kex.asm.manager

import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.trace.AbstractTrace
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.logging.log
import java.util.*

private val visibilityLevel by lazy {
    kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC)
}

val Method.isImpactable: Boolean
    get() = when {
        this.isAbstract -> false
        this.isStaticInitializer -> false
        this.klass.isSynthetic -> false
        this.klass.isAbstract && this.isConstructor -> false
        this.isSynthetic -> false
        visibilityLevel > this.klass.visibility -> false
        visibilityLevel > this.visibility -> false
        else -> true
    }

data class CoverageInfo(
    val bodyCovered: Int,
    val bodyTotal: Int,
    val fullCovered: Int,
    val fullTotal: Int,
) {
    val bodyCoverage: Double get() = (bodyCovered * 100).toDouble() / bodyTotal
    val fullCoverage: Double get() = (fullCovered * 100).toDouble() / fullTotal

    constructor() : this(0, 0, 0, 0)

    operator fun plus(other: CoverageInfo): CoverageInfo {
        return CoverageInfo(
            this.bodyCovered + other.bodyCovered,
            this.bodyTotal + other.bodyTotal,
            this.fullCovered + other.fullCovered,
            this.fullTotal + other.fullTotal
        )
    }
}

class CoverageCounter<T : AbstractTrace> private constructor(
    override val cm: ClassManager,
    private val tm: TraceManager<T>,
    val methodFilter: (Method) -> Boolean
) : MethodVisitor {
    val methodInfos = hashMapOf<Method, CoverageInfo>()

    constructor(cm: ClassManager, tm: TraceManager<T>) : this(cm, tm, { true })
    constructor(cm: ClassManager, tm: TraceManager<T>, pkg: Package) :
            this(cm, tm, { pkg.isParent(it.klass.pkg) })

    constructor(cm: ClassManager, tm: TraceManager<T>, klass: Class) :
            this(cm, tm, { it.klass == klass })

    constructor(cm: ClassManager, tm: TraceManager<T>, methods: Set<Method>) :
            this(cm, tm, { it in methods })

    val totalCoverage: CoverageInfo
        get() = methodInfos.values.fold(CoverageInfo()) { acc, coverageInfo ->
            acc + coverageInfo
        }

    private val Method.isInteresting: Boolean
        get() = when {
            this.isAbstract -> false
            this.isStaticInitializer -> false
            this.klass.isSynthetic -> false
            this.isSynthetic -> false
            !this.hasBody -> false
            else -> true
        }

    override fun cleanup() {}

    override fun visit(method: Method) {
        if (!method.isInteresting) return
        if (!methodFilter(method)) return

        val bodyBlocks = method.bodyBlocks.filter { it.wrapper != null }.groupBy { it.wrapper!! }
        val catchBlocks = method.catchBlocks.filter { it.wrapper != null }.groupBy { it.wrapper!! }
        val bodyCovered = bodyBlocks.count { (_, blocks) -> blocks.any { tm.isCovered(it) } }
        val catchCovered = catchBlocks.count { (_, blocks) -> blocks.any { tm.isCovered(it) } }

        val info = CoverageInfo(
            bodyCovered, bodyBlocks.size,
            bodyCovered + catchCovered, bodyBlocks.size + catchBlocks.size
        )
        methodInfos[method] = info

        log.info(
            "Method $method coverage: " +
                    "body = ${String.format(Locale.ENGLISH, "%.2f", info.bodyCoverage)}; " +
                    "full = ${String.format(Locale.ENGLISH, "%.2f", info.fullCoverage)}"
        )
    }
}
