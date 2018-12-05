package org.jetbrains.research.kex.smt

import org.jetbrains.research.kex.KexProcessor
import org.jetbrains.research.kex.util.unreachable
import java.io.ByteArrayOutputStream
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import kotlin.reflect.KClass

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes(
        "org.jetbrains.research.kex.smt.SMTExpr",
        "org.jetbrains.research.kex.smt.SMTMemory",
        "org.jetbrains.research.kex.smt.SMTExprFactory",
        "org.jetbrains.research.kex.smt.SMTContext",
        "org.jetbrains.research.kex.smt.SMTConverter")
@SupportedOptions(SMTProcessor.KAPT_GENERATED_SOURCES, SMTProcessor.KEX_TEMPLATES)
class SMTProcessor : KexProcessor() {
    companion object {
        const val KAPT_GENERATED_SOURCES = "kapt.kotlin.generated"
        const val KEX_TEMPLATES = "kex.templates"
    }

    private var printedGeneratedSourcesDir = false

    private val targetDirectory: String
        get() = processingEnv.options[KAPT_GENERATED_SOURCES] ?: unreachable { error("No codegen directory") }

    private val templates: String
        get() = processingEnv.options[KEX_TEMPLATES] ?: unreachable { error("No template directory") }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {
        roundEnv?.apply {
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
        }
        return true
    }

    private fun <T : Annotation> processAnnotation(element: Element, annotationClass: KClass<T>, nameTemplate: String) {
        val `package` = processingEnv.elementUtils.getPackageOf(element).toString()
        val annotation = element.getAnnotation(annotationClass.java)
                ?: unreachable { error("Element $element have no annotation $annotationClass") }

        val parameters = getAnnotationProperties(annotation, annotationClass).toMutableMap()
        val solver = parameters.getValue("solver") as String
        val newPackage = "$`package`.${solver.toLowerCase()}"
        val newClass = "$solver$nameTemplate"
        parameters["packageName"] = newPackage

        writeClass(newPackage, newClass, parameters, "SMT$nameTemplate")
    }

    private fun writeClass(`package`: String, `class`: String, parameters: Map<String, Any>, template: String) {
        val file = File("$targetDirectory/${`package`.replace('.', '/')}/$`class`.kt")
        if (!file.parentFile.exists()) file.parentFile.mkdirs()

        val stream = ByteArrayOutputStream()
        val writer = stream.bufferedWriter()
        ClassGenerator(parameters, templates, "$template.vm").doit(writer)
        writer.flush()
        val resultingFile = stream.toString()

        if (!file.exists() || file.readText() != resultingFile) {

            if (!printedGeneratedSourcesDir) {
                info("Generating sources to $targetDirectory")
                printedGeneratedSourcesDir = true
            }

            info("Generating $template for $`class` in package $`package` with parameters $parameters")
            val fileWriter = file.writer()
            fileWriter.write(resultingFile)
            fileWriter.close()
        }
    }
}