package org.jetbrains.research.kex.smtinstance

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement

annotation class SMTInstance(val solver: String, val context: String)

class SMTInstanceGenerator() : AbstractProcessor() {
    private companion object {
        const val SUFFIX_OPTION = "suffix"
        const val GENERATE_KOTLIN_CODE_OPTION = "generate.kotlin.code"
        const val GENERATE_ERROR = "generate.error"
        const val KAPT_KOTLIN_GENERATED_OPTION = "kapt.kotlin.generated"
    }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {
        println("process")
        roundEnv?.getElementsAnnotatedWith(SMTInstance::class.java)
                ?.forEach {
                    val className = it.simpleName.toString()
                    val pack = processingEnv.elementUtils.getPackageOf(it).toString()
                    println("Processing: $className in package $pack")
                }
        println(annotations.toString())
        return true
    }

    override fun getSupportedSourceVersion() = SourceVersion.RELEASE_8
    override fun getSupportedAnnotationTypes() = setOf("org.jetbrains.research.kex.smtinstance.SMTInstance")
    override fun getSupportedOptions() = setOf(SUFFIX_OPTION, GENERATE_KOTLIN_CODE_OPTION, GENERATE_ERROR)

}