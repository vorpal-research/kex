package org.jetbrains.research.kex.smt

import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kex.InheritanceInfo
import org.jetbrains.research.kex.Inheritor
import org.jetbrains.research.kex.KexProcessor
import java.io.File
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedOptions
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement


@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("org.jetbrains.research.kex.smt.AbstractSolver", "org.jetbrains.research.kex.smt.Solver")
@SupportedOptions(SolverInfoProcessor.RUNNER_RESOURCES)
class SolverInfoProcessor : KexProcessor() {
    companion object {
        const val RUNNER_RESOURCES = "runner.resources"
    }

    private lateinit var inheritanceInfo: InheritanceInfo

    private val base = "solvers"

    private val targetDirectory: String
        get() = processingEnv.options[RUNNER_RESOURCES] ?: unreachable { error("No source directory") }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {
        roundEnv?.run {
            getElementsAnnotatedWith(AbstractSolver::class.java)?.forEach {
                processAbstractSolver(it)
            }
            getElementsAnnotatedWith(Solver::class.java)?.forEach {
                processSolver(it)
            }
        }
        writeInheritanceInfo(base, inheritanceInfo)
        return true
    }

    private fun processAbstractSolver(element: Element) {
        inheritanceInfo = InheritanceInfo(element.fullName, setOf())
    }

    private fun processSolver(element: Element) {
        val annotation = element.getAnnotation(Solver::class.java)
                ?: unreachable { error("Element $element have no annotation InheritorOf") }

        val name = annotation.getProperty("name") as String
        if (!::inheritanceInfo.isInitialized) inheritanceInfo = getInheritanceInfo(base)
        inheritanceInfo += Inheritor(name, element.fullName)
    }

    private fun getInheritanceInfo(name: String): InheritanceInfo {
        val targetFile = File(targetDirectory, "$name.json")
        return targetFile.takeIf { it.exists() }?.bufferedReader()?.use {
            InheritanceInfo.fromJson(it.readText())
        } ?: unreachable { error("Could not load $name with solver info") }
    }

    private fun writeInheritanceInfo(name: String, info: InheritanceInfo) {
        val targetFile = File(targetDirectory,"$name.json").also {
            it.parentFile?.mkdirs()
        }
        targetFile.bufferedWriter().use {
            it.write(info.toJson())
            it.flush()
        }
    }
}