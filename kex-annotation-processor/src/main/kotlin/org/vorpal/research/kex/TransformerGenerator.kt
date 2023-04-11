package org.vorpal.research.kex

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
@SupportedAnnotationTypes("org.vorpal.research.kex.TransformerBase")
@SupportedOptions(TransformerGenerator.KEX_RESOURCES, TransformerGenerator.KAPT_GENERATED_SOURCES)
class TransformerGenerator : KexProcessor() {
    companion object {
        const val KEX_RESOURCES = "kex.resources"
        const val KAPT_GENERATED_SOURCES = "kapt.kotlin.generated"

        private const val SHIFT = "    "
        private const val DOUBLE_SHIFT = SHIFT + SHIFT
        private const val TRIPLE_SHIFT = DOUBLE_SHIFT + SHIFT

        private const val header = """
package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.state.*
import org.vorpal.research.kex.state.predicate.*
import org.vorpal.research.kex.state.term.*
import org.vorpal.research.kfg.ir.Location
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import java.util.*

interface Transformer<T : Transformer<T>> {
    /**
     * Stub to return when you want to delete some predicate in predicate state
     * Needed to avoid using nullable types in transformer
     * Should *never* appear outside of transformers
     */
    private object Stub : Predicate() {
        override val type = PredicateType.State()
        override val location = Location()
        override val operands = listOf<Term>()

        override fun print() = "stub"
        override fun <T : Transformer<T>> accept(t: Transformer<T>) = Stub
    }

    fun nothing(): Predicate = Stub

    fun apply(ps: PredicateState) = transform(ps).simplify()
        """

        private const val predicateState = """
    ////////////////////////////////////////////////////////////////////
    // PredicateState
    ////////////////////////////////////////////////////////////////////
    fun transform(ps: PredicateState) = transformBase(ps)
    
    fun transformBase(ps: PredicateState): PredicateState {
        val res = delegate(ps)
        return transformPredicateState(res)
    }
    
    fun transformPredicateState(ps: PredicateState) = ps
    
    fun transformBasic(ps: BasicState): PredicateState = ps.map { p -> transformBase(p) }.filterNot { it is Stub }
    fun transformChain(ps: ChainState): PredicateState = ps.fmap { e -> transformBase(e) }
    fun transformChoice(ps: ChoiceState): PredicateState = ps.fmap { e -> transformBase(e) }
    
    fun transformBasicState(ps: BasicState): PredicateState = ps
    fun transformChainState(ps: ChainState): PredicateState = ps
    fun transformChoiceState(ps: ChoiceState): PredicateState = ps
    """
    }

    private val infoDirectory: String
        get() = processingEnv.options[KEX_RESOURCES] ?: unreachable { error("No source directory") }
    private val targetDirectory: String
        get() = processingEnv.options[KAPT_GENERATED_SOURCES] ?: unreachable { error("No source directory") }


    private fun delegate(
        baseClass: String,
        types: Set<String>,
        baseName: String = baseClass,
        checkStub: Boolean = false
    ) = buildString {
        appendLine("${SHIFT}private fun delegateType(argument: $baseClass): $baseClass = when (argument) {")
        for (type in types) {
            appendLine("${DOUBLE_SHIFT}is $type$baseName -> transform$type$baseName(argument)")
        }
        appendLine("${DOUBLE_SHIFT}else -> unreachable { log.error(\"Unknown argument \$argument of base $baseClass\") }")
        appendLine("${SHIFT}}")


        appendLine("${SHIFT}private fun delegate(argument: $baseClass): $baseClass = when (argument) {")
        for (type in types) {
            appendLine("${DOUBLE_SHIFT}is $type$baseName -> {")
            appendLine("${TRIPLE_SHIFT}val res = transform$type(argument)")
            when {
                checkStub -> {
                    appendLine("${TRIPLE_SHIFT}if (res is Stub) res")
                    appendLine("${TRIPLE_SHIFT}else delegateType(res)")
                }

                else -> {
                    appendLine("${TRIPLE_SHIFT}delegateType(res)")
                }
            }
            appendLine("${DOUBLE_SHIFT}}")
        }
        if (checkStub) {
            appendLine("${DOUBLE_SHIFT}is Stub -> argument")
        }
        appendLine("${DOUBLE_SHIFT}else -> unreachable { log.error(\"Unknown argument \$argument of base $baseClass\") }")
        appendLine("${SHIFT}}")
    }

    private fun beforeCall(type: String, base: String) =
        "${SHIFT}fun transform$type(${base.lowercase()}: $type$base): $base = ${base.lowercase()}.accept(this)"

    private fun afterCall(type: String, base: String) =
        "${SHIFT}fun transform$type$base(${base.lowercase()}: $type$base): $base = ${base.lowercase()}"

    private fun baseCall(base: String) = buildString {
        appendLine("${SHIFT}////////////////////////////////////////////////////////////////////")
        appendLine("${SHIFT}// $base")
        appendLine("${SHIFT}////////////////////////////////////////////////////////////////////")
        appendLine("${SHIFT}fun transform(${base.lowercase()}: $base): $base = transformBase(${base.lowercase()})")
        appendLine("${SHIFT}fun transformBase(${base.lowercase()}: $base): $base {")
        appendLine("${DOUBLE_SHIFT}val res = delegate(${base.lowercase()})")
        appendLine("${DOUBLE_SHIFT}return transform$base(res)")
        appendLine("${SHIFT}}")
        appendLine("${SHIFT}fun transform$base(${base.lowercase()}: $base): $base = ${base.lowercase()}")
        appendLine()
    }

    private val InheritanceInfo.baseClass get() = base.split(".").last()

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {
        roundEnv?.apply {
            getElementsAnnotatedWith(TransformerBase::class.java)?.forEach {
                processAnnotation(it)
            }
        }
        return true
    }

    private fun processAnnotation(element: Element) {
        val pkg = processingEnv.elementUtils.getPackageOf(element).toString()

        val klass = "Transformer"

        val terms = InheritanceInfo.fromJson(File("${infoDirectory}Term.json").readText())
        val predicates = InheritanceInfo.fromJson(File("${infoDirectory}Predicate.json").readText())
        val predicateStates = InheritanceInfo.fromJson(File("${infoDirectory}PredicateState.json").readText())

        writeClass(pkg, klass) {
            buildString {
                appendLine(header)

                appendLine(
                    delegate(
                        predicateStates.baseClass,
                        predicateStates.inheritors.mapTo(mutableSetOf()) { it.name },
                        baseName = "State"
                    )
                )
                appendLine(delegate(terms.baseClass, terms.inheritors.mapTo(mutableSetOf()) { it.name }))
                appendLine(
                    delegate(
                        predicates.baseClass,
                        predicates.inheritors.mapTo(mutableSetOf()) { it.name },
                        checkStub = true
                    )
                )

                appendLine(predicateState)
                appendLine()

                appendLine(baseCall(predicates.baseClass))
                for (subPredicate in predicates.inheritors) {
                    appendLine(beforeCall(subPredicate.name, predicates.baseClass))
                    appendLine(afterCall(subPredicate.name, predicates.baseClass))
                }
                appendLine()

                appendLine(baseCall(terms.baseClass))
                for (subTerm in terms.inheritors) {
                    appendLine(beforeCall(subTerm.name, terms.baseClass))
                    appendLine(afterCall(subTerm.name, terms.baseClass))
                }
                appendLine()

                appendLine("}")
            }
        }
    }

    private fun writeClass(pkg: String, klass: String, body: () -> String) = writeClass(pkg, klass, body())

    private fun writeClass(pkg: String, klass: String, body: String) {
        val file = File("$targetDirectory/${pkg.replace('.', '/')}/$klass.kt")
        if (!file.parentFile.exists()) file.parentFile.mkdirs()

        if (!file.exists() || file.readText() != body) {
            info("Generating new transformer to $targetDirectory")
            file.writer().use {
                it.write(body)
            }
        }
    }

}
