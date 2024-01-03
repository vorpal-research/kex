package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.annotations.AnnotationManager
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.descriptor.ClassDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.Object2DescriptorConverter
import org.vorpal.research.kex.descriptor.descriptor
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.smt.Checker
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.StateBuilder
import org.vorpal.research.kex.state.predicate.state
import org.vorpal.research.kex.state.term.FieldLoadTerm
import org.vorpal.research.kex.state.term.FieldTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.util.KfgTargetFilter
import org.vorpal.research.kex.util.allFields
import org.vorpal.research.kex.util.isFinal
import org.vorpal.research.kex.util.isStatic
import org.vorpal.research.kex.util.loadClass
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Field
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kthelper.collection.dequeOf
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.`try`
import org.vorpal.research.kthelper.tryOrNull

val ignores by lazy {
    kexConfig.getMultipleStringValue("inliner", "ignoreStatic")
        .mapTo(mutableSetOf()) { KfgTargetFilter.parse(it) }
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
            +RecursiveInliner(psa) { index, psa ->
                ConcreteImplInliner(ctx.types, TypeInfoMap(), psa, inlineIndex = index)
            }
            +ClassAdapter(ctx.cm)
            +IntrinsicAdapter
            +KexIntrinsicsAdapter()
            +EqualsTransformer()
            +BasicInvariantsTransformer(method)
            +ReflectionInfoAdapter(method, ctx.loader, ignores)
            +Optimizer()
            +ConstantPropagator
            +BoolTypeAdapter(ctx.types)
            +ClassMethodAdapter(ctx.cm)
            +ConstStringAdapter(method.cm.type)
            +ArrayBoundsAdapter()
            +NullityInfoAdapter()
            +FieldNormalizer(ctx.cm, ".state.normalized")
            +TypeNameAdapter(ctx)
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
                    log.debug("Model: {}", result.model)
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
            .mapNotNullTo(mutableSetOf()) {
                val field = it.field as FieldTerm
                tryOrNull { field.unmappedKfgField(cm) }?.let { kfgField ->
                    if (kfgField.isStatic && kfgField.isFinal) {
                        kfgField
                    } else {
                        null
                    }
                }
            }
            .filterNotTo(mutableSetOf()) { field -> ignores.any { filter -> filter.matches(field.klass) } }
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


class StaticFieldWDescriptorInliner(
    val ctx: ExecutionContext
) : RecollectingTransformer<StaticFieldWDescriptorInliner>, IncrementalTransformer {
    override val builders = dequeOf(StateBuilder())

    companion object {
        private val objectConverter = Object2DescriptorConverter()
        private val staticFields = mutableMapOf<Class, MutableMap<String, Descriptor>>()

        private fun getStaticField(ctx: ExecutionContext, field: Field): Descriptor? = when (field.klass) {
            in staticFields -> staticFields[field.klass]!![field.name]
            else -> {
                val loadedKlass = ctx.loader.loadClass(field.klass)
                val fields = mutableMapOf<String, Descriptor>()
                staticFields[field.klass] = fields
                for (fieldReflection in loadedKlass.allFields) {
                    if (fieldReflection.isStatic && fieldReflection.isFinal) {
                        fields[fieldReflection.name] = tryOrNull {
                            objectConverter.convert(fieldReflection.get(null))
                        } ?: continue
                    }
                }
                fields[field.name]
            }
        }
    }

    private val Term.isEnum get() = (this.type.getKfgType(ctx.cm.type) as? ClassType)?.klass?.isEnum ?: false

    override fun apply(ps: PredicateState): PredicateState = `try` {
        val staticFields = TermCollector.getFullTermSet(ps)
            .asSequence()
            .filterIsInstance<FieldLoadTerm>()
            .mapNotNullTo(mutableSetOf()) {
                val field = it.field as FieldTerm
                tryOrNull { field.unmappedKfgField(ctx.cm) }?.let { kfgField ->
                    if (kfgField.isStatic && kfgField.isFinal && !field.isEnum) {
                        kfgField
                    } else {
                        null
                    }
                }
            }
            .filterNotTo(mutableSetOf()) { field -> ignores.any { filter -> filter.matches(field.klass) } }
        for (field in staticFields) {
            val descriptor = getStaticField(ctx, field) ?: continue
            currentBuilder += descriptor.initializerState
            currentBuilder += state {
                staticRef(field.klass).field(field.type.kexType, field.name).initialize(descriptor.term)
            }
        }
        super.apply(ps)
    }.getOrElse {
        log.error("Failed to inline static fields in $ps")
        ps
    }

    override fun apply(state: IncrementalPredicateState): IncrementalPredicateState {
        return IncrementalPredicateState(
            apply(state.state),
            state.queries
        )
    }
}
