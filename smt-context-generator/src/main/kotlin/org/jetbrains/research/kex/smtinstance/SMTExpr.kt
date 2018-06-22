package org.jetbrains.research.kex.smtinstance

import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

annotation class SMTExpr(
        val solver: String,
        val solverImport: String,
        val context: String,
        val expr: String,
        val sort: String)

class SMTInstanceGenerator : AbstractProcessor() {
    private companion object {
        const val CODEGEN_DIR = "codegen.dir"
        const val TEMPLATE_DIR = "template.dir"
    }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {
        roundEnv?.getElementsAnnotatedWith(SMTExpr::class.java)?.forEach { processSMTExpr(roundEnv, it) }
        return true
    }

    override fun getSupportedSourceVersion() = SourceVersion.RELEASE_8
    override fun getSupportedAnnotationTypes() = setOf("org.jetbrains.research.kex.smtinstance.SMTExpr")
    override fun getSupportedOptions() = setOf(CODEGEN_DIR)

    private fun processSMTExpr(roundEnv: RoundEnvironment, element: Element) {
        val targetDirectory = processingEnv.options[CODEGEN_DIR] ?: "./"
        val templates = processingEnv.options[TEMPLATE_DIR] ?: "./"

        val `class` = element.simpleName.toString()
        val `package` = processingEnv.elementUtils.getPackageOf(element).toString()
        val annotation = element.getAnnotation(SMTExpr::class.java)

        val newPackage = "$`package`.${annotation.solver.toLowerCase()}"
        val newClass = `class`

        val parameters = mutableMapOf<String, Any>("packageName" to newPackage, "className" to newClass)
        parameters["solver"] = annotation.solver
        parameters["import"] = annotation.solverImport
        parameters["context"] = annotation.context
        parameters["expr"] = annotation.expr
        parameters["sort"] = annotation.sort

        processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, "Generating SMTExpr for $`class` in package $`package` with parameters $annotation")
        val file = File("$targetDirectory/${newPackage.replace('.', '/')}/$newClass.kt")
        if (!file.parentFile.exists()) file.parentFile.mkdirs()
        val fileWriter = file.writer()
        ClassGenerator(parameters, templates, "SMTExpr.vm").doit(fileWriter)
        fileWriter.close()
    }
}