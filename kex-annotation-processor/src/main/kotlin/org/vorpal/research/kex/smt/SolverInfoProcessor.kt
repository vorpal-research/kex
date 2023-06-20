package org.vorpal.research.kex.smt

import org.vorpal.research.kex.InheritanceInfo
import org.vorpal.research.kex.Inheritor
import org.vorpal.research.kex.KexProcessor
import org.vorpal.research.kthelper.assert.unreachable
import java.io.File
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedOptions
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement


@Suppress("SameParameterValue")
@SupportedAnnotationTypes("org.vorpal.research.kex.smt.AbstractSolver", "org.vorpal.research.kex.smt.Solver")
@SupportedOptions(SolverInfoProcessor.RUNNER_RESOURCES)
class SolverInfoProcessor : KexProcessor() {
    companion object {
        const val RUNNER_RESOURCES = "runner.resources"
    }

    private val inheritanceInfos = mutableMapOf<String, InheritanceInfo>()

    private val base = "solvers"
    private val asyncBase = "async-solvers"
    private val incrementalBase = "incremental-solvers"
    private val asyncIncrementalBase = "async-incremental-solvers"

    private val targetDirectory: String
        get() = processingEnv.options[RUNNER_RESOURCES] ?: unreachable { error("No source directory") }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {
        roundEnv?.run {
            getElementsAnnotatedWith(AbstractSolver::class.java)?.forEach {
                processBaseSolver(base, it)
            }
            getElementsAnnotatedWith(Solver::class.java)?.forEach {
                processSolver(base, it, Solver::class.java)
            }


            getElementsAnnotatedWith(AbstractAsyncSolver::class.java)?.forEach {
                processBaseSolver(asyncBase, it)
            }
            getElementsAnnotatedWith(AsyncSolver::class.java)?.forEach {
                processSolver(asyncBase, it, AsyncSolver::class.java)
            }


            getElementsAnnotatedWith(AbstractIncrementalSolver::class.java)?.forEach {
                processBaseSolver(incrementalBase, it)
            }
            getElementsAnnotatedWith(IncrementalSolver::class.java)?.forEach {
                processSolver(incrementalBase, it, IncrementalSolver::class.java)
            }


            getElementsAnnotatedWith(AbstractAsyncIncrementalSolver::class.java)?.forEach {
                processBaseSolver(asyncIncrementalBase, it)
            }
            getElementsAnnotatedWith(AsyncIncrementalSolver::class.java)?.forEach {
                processSolver(asyncIncrementalBase, it, AsyncIncrementalSolver::class.java)
            }
        }
        for ((baseName, inheritanceInfo) in inheritanceInfos) {
            writeInheritanceInfo(baseName, inheritanceInfo)
        }
        return true
    }

    private fun processBaseSolver(baseName: String, element: Element) {
        inheritanceInfos[baseName] = InheritanceInfo(element.fullName, setOf())
    }

    private fun <T : Annotation> processSolver(baseName: String, element: Element, annotationClass: Class<T>) {
        val annotation = element.getAnnotation(annotationClass)
            ?: unreachable { error("Element $element have no annotation InheritorOf") }

        val name = annotation.getProperty("name") as String

        val inheritanceInfo = inheritanceInfos.getOrPut(baseName) { getInheritanceInfo(baseName) }
        inheritanceInfos[baseName] = inheritanceInfo + Inheritor(name, element.fullName)
    }

    private fun getInheritanceInfo(name: String): InheritanceInfo {
        val targetFile = File(targetDirectory, "$name.json")
        return targetFile.takeIf { it.exists() }?.bufferedReader()?.use {
            InheritanceInfo.fromJson(it.readText())
        } ?: unreachable { error("Could not load $name with solver info") }
    }

    private fun writeInheritanceInfo(name: String, info: InheritanceInfo) {
        val targetFile = File(targetDirectory, "$name.json").also {
            it.parentFile?.mkdirs()
        }
        targetFile.bufferedWriter().use {
            it.write(info.toJson())
            it.flush()
        }
    }
}
