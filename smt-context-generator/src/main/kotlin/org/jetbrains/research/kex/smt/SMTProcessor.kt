package org.jetbrains.research.kex.smt

import org.jetbrains.research.kex.util.unreachable
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties

class SMTProcessor : AbstractProcessor() {
    private companion object {
        const val CODEGEN_DIR = "codegen.dir"
        const val TEMPLATE_DIR = "template.dir"
    }

    val targetDirectory get() = processingEnv.options[CODEGEN_DIR] ?: unreachable { error("No codegen directory") }
    val templates get() = processingEnv.options[TEMPLATE_DIR] ?: unreachable { error("No template directory") }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {
        roundEnv?.run {
            getElementsAnnotatedWith(SMTExpr::class.java)?.forEach {
                processAnnotation(it, SMTExpr::class, "Expr")
            }
            getElementsAnnotatedWith(SMTMemory::class.java)?.forEach {
                processAnnotation(it, SMTMemory::class, "Memory")
            }
            getElementsAnnotatedWith(SMTExprFactory::class.java)?.forEach {
                processAnnotation(it, SMTExprFactory::class, "ExprFactory")
            }
            getElementsAnnotatedWith(SMTContext::class.java)?.forEach {
                processAnnotation(it, SMTContext::class, "Context")
            }
            getElementsAnnotatedWith(SMTConverter::class.java)?.forEach {
                processAnnotation(it, SMTConverter::class, "Converter")
            }
            Unit
        }
        return true
    }

    override fun getSupportedSourceVersion() = SourceVersion.RELEASE_8
    override fun getSupportedAnnotationTypes() = setOf(
            "org.jetbrains.research.kex.smt.SMTExpr",
            "org.jetbrains.research.kex.smt.SMTMemory",
            "org.jetbrains.research.kex.smt.SMTExprFactory",
            "org.jetbrains.research.kex.smt.SMTContext",
            "org.jetbrains.research.kex.smt.SMTConverter"
    )

    override fun getSupportedOptions() = setOf(CODEGEN_DIR)

    private fun <T : Annotation> processAnnotation(element: Element, annotation: KClass<T>, nameTemplate: String) {
        val `class` = element.simpleName.toString()
        val `package` = processingEnv.elementUtils.getPackageOf(element).toString()
        val anno = element.getAnnotation(annotation.java)
                ?: unreachable { error("Element $element have no annotation $annotation") }

        val parameters = mutableMapOf<String, Any>()
        for (property in annotation.declaredMemberProperties) {
            val prop = anno::class.memberFunctions.first { it.name == property.name }
            parameters[property.name] = prop.call(anno)
                    ?: unreachable { error("Annotation $anno have no property named ${property.name}") }
        }
        val solver = parameters.getValue("solver") as String
        val newPackage = "$`package`.${solver.toLowerCase()}"
        val newClass = "$solver$nameTemplate"

        parameters["packageName"] = newPackage

        info("Generating $nameTemplate for $`class` in package $`package` with parameters $parameters")
        writeClass(newPackage, newClass, parameters, "SMT$nameTemplate")
    }

    private fun writeClass(`package`: String, `class`: String, parameters: Map<String, Any>, template: String) {
        val file = File("$targetDirectory/${`package`.replace('.', '/')}/$`class`.kt")
        if (!file.parentFile.exists()) file.parentFile.mkdirs()
        val fileWriter = file.writer()
        ClassGenerator(parameters, templates, "$template.vm").doit(fileWriter)
        fileWriter.close()
    }

    private fun error(msg: String) = processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, msg)
    private fun info(msg: String) = processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, msg)
}