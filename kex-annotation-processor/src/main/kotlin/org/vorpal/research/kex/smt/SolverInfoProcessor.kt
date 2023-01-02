package org.vorpal.research.kex.smt

import org.vorpal.research.kex.InheritanceInfo
import org.vorpal.research.kex.Inheritor
import org.vorpal.research.kex.KexProcessor
import org.vorpal.research.kthelper.assert.unreachable
import java.io.File
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedOptions
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement


@Suppress("SameParameterValue")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("org.vorpal.research.kex.smt.AbstractSolver", "org.vorpal.research.kex.smt.Solver")
@SupportedOptions(SolverInfoProcessor.RUNNER_RESOURCES)
class SolverInfoProcessor : KexProcessor() {
    companion object {
        const val RUNNER_RESOURCES = "runner.resources"
    }

    private lateinit var inheritanceInfo: InheritanceInfo
    private lateinit var asyncInheritanceInfo: InheritanceInfo

    private val base = "solvers"
    private val asyncBase = "async-solvers"

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
            getElementsAnnotatedWith(AbstractAsyncSolver::class.java)?.forEach {
                processAsyncAbstractSolver(it)
            }
            getElementsAnnotatedWith(AsyncSolver::class.java)?.forEach {
                processAsyncSolver(it)
            }
        }
        writeInheritanceInfo(base, inheritanceInfo)
        writeInheritanceInfo(asyncBase, asyncInheritanceInfo)
        return true
    }

    private fun processAbstractSolver(element: Element) {
        inheritanceInfo = InheritanceInfo(element.fullName, setOf())
    }

    private fun processAsyncAbstractSolver(element: Element) {
        asyncInheritanceInfo = InheritanceInfo(element.fullName, setOf())
    }

    private fun processSolver(element: Element) {
        val annotation = element.getAnnotation(Solver::class.java)
                ?: unreachable { error("Element $element have no annotation InheritorOf") }

        val name = annotation.getProperty("name") as String
        if (!::inheritanceInfo.isInitialized) inheritanceInfo = getInheritanceInfo(base)
        inheritanceInfo += Inheritor(name, element.fullName)
    }

    private fun processAsyncSolver(element: Element) {
        val annotation = element.getAnnotation(AsyncSolver::class.java)
            ?: unreachable { error("Element $element have no annotation InheritorOf") }

        val name = annotation.getProperty("name") as String
        if (!::asyncInheritanceInfo.isInitialized) asyncInheritanceInfo = getInheritanceInfo(asyncBase)
        asyncInheritanceInfo += Inheritor(name, element.fullName)
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
