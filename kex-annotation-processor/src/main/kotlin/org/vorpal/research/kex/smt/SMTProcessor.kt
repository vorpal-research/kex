package org.vorpal.research.kex.smt

import org.vorpal.research.kex.KexProcessor
import org.vorpal.research.kthelper.assert.unreachable
import java.io.ByteArrayOutputStream
import java.io.File
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedOptions
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import kotlin.reflect.KClass

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes(
        "org.vorpal.research.kex.smt.SMTExpr",
        "org.vorpal.research.kex.smt.SMTMemory",
        "org.vorpal.research.kex.smt.SMTExprFactory",
        "org.vorpal.research.kex.smt.SMTContext",
        "org.vorpal.research.kex.smt.SMTConverter")
@SupportedOptions(SMTProcessor.KAPT_GENERATED_SOURCES)
class SMTProcessor : KexProcessor() {
    companion object {
        const val KAPT_GENERATED_SOURCES = "kapt.kotlin.generated"
    }

    private var printedGeneratedSourcesDir = false

    private val targetDirectory: String
        get() = processingEnv.options[KAPT_GENERATED_SOURCES] ?: unreachable { error("No codegen directory") }

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
        val newClass = "$solver$nameTemplate"
        parameters["packageName"] = `package`

        writeClass(`package`, newClass, parameters, "SMT$nameTemplate")
    }

    private fun writeClass(`package`: String, `class`: String, parameters: Map<String, Any>, template: String) {
        val file = File("$targetDirectory/${`package`.replace('.', '/')}/$`class`.kt")
        if (!file.parentFile.exists()) file.parentFile.mkdirs()

        val stream = ByteArrayOutputStream()
        stream.bufferedWriter().use {
            ClassGenerator(parameters, "$template.vm").write(it)
            it.flush()
        }
        val resultingFile = stream.toString()

        if (!file.exists() || file.readText() != resultingFile) {

            if (!printedGeneratedSourcesDir) {
                info("Generating sources to $targetDirectory")
                printedGeneratedSourcesDir = true
            }

            info("Generating $template for $`class` in package $`package` with parameters $parameters")
            file.writer().use {
                it.write(resultingFile)
            }
        }
    }
}