package org.vorpal.research.kex.jacoco

import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log


interface CoverageInfo {
    val covered: Int
    val total: Int
    val ratio: Double
}

enum class CoverageUnit(unit: String) {
    INSTRUCTION("instructions"),
    BRANCH("branches"),
    LINE("lines"),
    COMPLEXITY("complexity");

    companion object {
        fun parse(unit: String) = when (unit) {
            INSTRUCTION.unitName -> INSTRUCTION
            BRANCH.unitName -> BRANCH
            LINE.unitName -> LINE
            COMPLEXITY.unitName -> COMPLEXITY
            else -> unreachable { log.error("Unknown coverage unit $unit") }
        }
    }

    val unitName: String = unit

    override fun toString(): String {
        return unitName
    }
}

enum class AnalysisUnit(unit: String) {
    METHOD("method"),
    CLASS("class"),
    PACKAGE("package");

    companion object {
        fun parse(unit: String) = when (unit) {
            METHOD.unitName -> METHOD
            CLASS.unitName -> CLASS
            PACKAGE.unitName -> PACKAGE
            else -> unreachable { log.error("Unknown analysis unit $unit") }
        }
    }

    val unitName: String = unit

    override fun toString(): String {
        return unitName
    }
}

data class GenericCoverageInfo(
    override val covered: Int,
    override val total: Int,
    val unit: CoverageUnit
) : CoverageInfo {
    override val ratio: Double get() = when (total) {
        0 -> 0.0
        else -> covered.toDouble() / total
    }
    override fun toString(): String = buildString {
        append(String.format("%s of %s %s covered", covered, total, unit))
        if (total > 0) {
            append(String.format(" = %.2f", ratio * 100))
            append("%")
        }
    }
}

abstract class CommonCoverageInfo(
    val name: String,
    val level: AnalysisUnit,
    val instructionCoverage: CoverageInfo,
    val branchCoverage: CoverageInfo,
    val linesCoverage: CoverageInfo,
    val complexityCoverage: CoverageInfo
) {
    open fun print(detailed: Boolean = false) = toString()

    override fun toString(): String = buildString {
        appendLine(String.format("Coverage of `%s` %s:", name, level))
        appendLine("    $instructionCoverage")
        appendLine("    $branchCoverage")
        appendLine("    $linesCoverage")
        append("    $complexityCoverage")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CommonCoverageInfo) return false

        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}

class MethodCoverageInfo(
    name: String,
    instructionCoverage: CoverageInfo,
    branchCoverage: CoverageInfo,
    linesCoverage: CoverageInfo,
    complexityCoverage: CoverageInfo
) : CommonCoverageInfo(
    name,
    AnalysisUnit.METHOD,
    instructionCoverage,
    branchCoverage,
    linesCoverage,
    complexityCoverage
)

class ClassCoverageInfo(
    name: String,
    instructionCoverage: CoverageInfo,
    branchCoverage: CoverageInfo,
    linesCoverage: CoverageInfo,
    complexityCoverage: CoverageInfo,
) : CommonCoverageInfo(
    name,
    AnalysisUnit.CLASS,
    instructionCoverage,
    branchCoverage,
    linesCoverage,
    complexityCoverage
) {
    val methods = mutableSetOf<MethodCoverageInfo>()

    override fun print(detailed: Boolean) = buildString {
        appendLine(this@ClassCoverageInfo.toString())
        if (detailed) {
            methods.forEach {
                appendLine()
                appendLine(it.print(true))
            }
        }
    }
}

class PackageCoverageInfo(
    name: String,
    instructionCoverage: CoverageInfo,
    branchCoverage: CoverageInfo,
    linesCoverage: CoverageInfo,
    complexityCoverage: CoverageInfo
) : CommonCoverageInfo(
    name,
    AnalysisUnit.PACKAGE,
    instructionCoverage,
    branchCoverage,
    linesCoverage,
    complexityCoverage
) {
    val classes = mutableSetOf<ClassCoverageInfo>()

    override fun print(detailed: Boolean) = buildString {
        if (detailed) {
            classes.forEach {
                appendLine(it.print(true))
            }
        }
        appendLine(this@PackageCoverageInfo.toString())
    }
}
