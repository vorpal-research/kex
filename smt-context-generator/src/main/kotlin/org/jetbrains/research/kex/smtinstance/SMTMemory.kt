package org.jetbrains.research.kex.smtinstance

import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic


annotation class SMTMemory(
        val solver: String,
        val solverImport: String,
        val context: String,
        val byteSize: Int)

class SMTMemoryGenerator : AbstractProcessor() {
    private companion object {
        const val CODEGEN_DIR = "codegen.dir"
        const val TEMPLATE_DIR = "template.dir"
    }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {
        roundEnv?.getElementsAnnotatedWith(SMTMemory::class.java)?.forEach { processSMTExpr(roundEnv, it) }
        return true
    }

    override fun getSupportedSourceVersion() = SourceVersion.RELEASE_8
    override fun getSupportedAnnotationTypes() = setOf("org.jetbrains.research.kex.smtinstance.SMTMemory")
    override fun getSupportedOptions() = setOf(CODEGEN_DIR)

    private fun processSMTExpr(roundEnv: RoundEnvironment, element: Element) {
        val targetDirectory = processingEnv.options[CODEGEN_DIR] ?: "./"
        val templates = processingEnv.options[TEMPLATE_DIR] ?: "./"

        val `class` = element.simpleName.toString()
        val `package` = processingEnv.elementUtils.getPackageOf(element).toString()
        val annotation = element.getAnnotation(SMTMemory::class.java)

        val newPackage = "$`package`.${annotation.solver.toLowerCase()}"
        val newClass = "${annotation.solver}Memory"

        val parameters = mutableMapOf<String, Any>("packageName" to newPackage, "className" to newClass)
        parameters["solver"] = annotation.solver
        parameters["context"] = annotation.context
        parameters["bytesize"] = annotation.byteSize
        parameters["import"] = annotation.solverImport

        processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, "Generating SMTMemory for $`class` in package $`package` with parameters $annotation")
        val file = File("$targetDirectory/${newPackage.replace('.', '/')}/$newClass.kt")
        if (!file.parentFile.exists()) file.parentFile.mkdirs()
        val fileWriter = file.writer()
        ClassGenerator(parameters, templates, "SMTMemory.vm").doit(fileWriter)
        fileWriter.close()
    }
}