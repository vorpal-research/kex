package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.annotations.AnnotationManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.descriptor.ClassDescriptor
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.descriptor.descriptor
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.term.FieldLoadTerm
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.`try`
import org.jetbrains.research.kthelper.collection.dequeOf
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.tryOrNull

val ignores by lazy {
    kexConfig.getMultipleStringValue("inliner", "static-ignore")
        .map { it.replace(".", "/") }
        .toSet()
}

class StaticFieldInliner(
    val ctx: ExecutionContext,
    val psa: PredicateStateAnalysis
) : RecollectingTransformer<StaticFieldInliner> {
    companion object {
        private val staticFinalFields = mutableMapOf<Field, Descriptor>()
        private val failedClasses = mutableSetOf<Class>()

        fun getStaticField(ctx: ExecutionContext, psa: PredicateStateAnalysis, field: Field) = when {
            field in staticFinalFields -> staticFinalFields.getValue(field)
            field.klass in failedClasses -> descriptor { default(field.type.kexType) }
            else -> {
                val generatedFields = tryOrNull { generateFinalFieldValues(ctx, psa, field.klass) }
                if (generatedFields == null) failedClasses += field.klass
                staticFinalFields += (generatedFields ?: mapOf())
                staticFinalFields[field] ?: descriptor { default(field.type.kexType) }
            }
        }

        private fun prepareState(
            ctx: ExecutionContext,
            psa: PredicateStateAnalysis,
            method: Method,
            ps: PredicateState,
            ignores: Set<Term> = setOf()
        ) = transform(ps) {
            +AnnotationAdapter(method, AnnotationManager.defaultLoader)
            +StringMethodAdapter(ctx.cm)
            +KexRtAdapter(ctx.cm)
            +RecursiveInliner(psa) {  index, psa ->
                ConcreteImplInliner(ctx.types, TypeInfoMap(), psa, inlineIndex = index)
            }
            +IntrinsicAdapter
            +KexIntrinsicsAdapter()
            +ReflectionInfoAdapter(method, ctx.loader, ignores)
            +Optimizer()
            +ConstantPropagator
            +BoolTypeAdapter(ctx.types)
            +ArrayBoundsAdapter()
            +NullityInfoAdapter()
            +ConstStringAdapter()
            +FieldNormalizer(ctx.cm, ".state.normalized")
        }

        private fun generateFinalFieldValues(
            ctx: ExecutionContext,
            psa: PredicateStateAnalysis,
            klass: Class
        ): Map<Field, Descriptor>? {
            val staticInit = klass.getMethod("<clinit>", "()V")
            val staticFields = klass.fields.filter { it.isFinal && it.isStatic }

            val state = with(StateBuilder()) {
                for (field in staticFields) {
                    if (field.defaultValue != null) {
                        val value = field.defaultValue!!
                        state {
                            val fieldTerm = staticRef(klass).field(field.type.kexType, field.name)
                            fieldTerm.store(value(value))
                        }
                    }
                }
                this += psa.builder(staticInit).methodState ?: return null
                prepareState(ctx, psa, staticInit, apply())
            }


            val query = with(StateBuilder()) {
                for (field in klass.fields.filter { it.isFinal && it.isStatic }) {
                    val valuesField = term { staticRef(klass).field(field.type.kexType, field.name) }
                    val generatedTerm = term { generate(field.type.kexType) }
                    require { generatedTerm equality valuesField.load() }
                }
                apply()
            }

            val checker = Checker(staticInit, ctx, psa)
            val params = when (val result = checker.check(state + query)) {
                is Result.SatResult -> {
                    log.debug("Model: ${result.model}")
                    generateFinalDescriptors(staticInit, ctx, result.model, checker.state)
                }
                else -> return null
            }

            return params.statics.map { descriptor ->
                descriptor as ClassDescriptor
                val kfgKlass = descriptor.klass.kfgClass(ctx.types)
                descriptor.fields.mapKeys {
                    val (name, type) = it.key
                    kfgKlass.getField(name, type.getKfgType(ctx.types))
                }.filterKeys { it.isStatic && it.isFinal }.toList()
            }.flatten().toMap()
        }
    }


    override val builders = dequeOf(StateBuilder())
    val cm get() = ctx.cm

    override fun apply(ps: PredicateState): PredicateState = `try` {
        val staticInitializers = TermCollector.getFullTermSet(ps)
            .asSequence()
            .filterIsInstance<FieldLoadTerm>()
            .mapNotNull {
                val field = it.field as FieldTerm
                val kfgField = field.unmappedKfgField(cm)
                if (kfgField.isStatic && kfgField.isFinal) {
                    kfgField
                } else {
                    null
                }
            }.toSet()
            .filterNot { it.klass.fullName in ignores }
            .toSet()
        for (field in staticInitializers) {
            val descriptor = getStaticField(ctx, psa, field)
            currentBuilder += descriptor.query
        }
        super.apply(ps)
    }.getOrElse {
        log.error("Failed to inline static fields in $ps")
        ps
    }
}