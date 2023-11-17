package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.KexReference
import org.vorpal.research.kex.ktype.KexString
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.StateBuilder
import org.vorpal.research.kex.state.basic
import org.vorpal.research.kex.state.emptyState
import org.vorpal.research.kex.state.predicate.axiom
import org.vorpal.research.kex.state.term.FieldLoadTerm
import org.vorpal.research.kex.state.term.StaticClassRefTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.TermBuilder.Terms.field
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.util.KfgTargetFilter
import org.vorpal.research.kex.util.allFields
import org.vorpal.research.kex.util.isStatic
import org.vorpal.research.kex.util.loadClass
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull

val ignores by lazy {
    kexConfig.getMultipleStringValue("inliner", "ignoreStatic")
        .mapTo(mutableSetOf()) { KfgTargetFilter.parse(it) }
}

class ConstEnumAdapter(
    val context: ExecutionContext
) : Transformer<ConstEnumAdapter>, IncrementalTransformer {
    val cm get() = context.cm
    private val Term.isEnum get() = (this.type.getKfgType(cm.type) as? ClassType)?.klass?.isEnum ?: false

    companion object {
        private val enumConstantsMap = mutableMapOf<KexType, Pair<Set<Term>, PredicateState>>()
        private fun getEnumInitializers(context: ExecutionContext, type: KexType) = enumConstantsMap.getOrPut(type) {
            prepareEnumConstants(context, type)
        }

        private fun getEnumFields(klass: Class<*>): List<Any> =
            klass.allFields
                .filter { it.isStatic && it.isEnumConstant }
                .map {
                    it.isAccessible = true
                    it.get(null)
                }

        private fun getEnumName(obj: Any): String {
            return (obj as Enum<*>).name
        }

        private fun getEnumOrdinal(obj: Any): Int {
            return (obj as Enum<*>).ordinal
        }

        private fun prepareEnumConstants(
            context: ExecutionContext,
            type: KexType
        ): Pair<Set<Term>, PredicateState> {
            val kfgType = type.getKfgType(context.types)
            ktassert(kfgType is ClassType && kfgType.klass.isEnum)
            val staticClass = term { staticRef((kfgType as ClassType).klass) }
            val enumFields = mutableSetOf<Term>()
            val state = emptyState().builder()

            try {
                val enumKlass = context.loader.loadClass(context.cm.type, staticClass.type)

                for (enumField in getEnumFields(enumKlass)) {
                    val enumName = getEnumName(enumField)
                    val enumOrdinal = getEnumOrdinal(enumField)
                    val enumFieldTerm = staticClass.field(staticClass.type, enumName)

                    state += basic {
                        val enumLoad = generate(staticClass.type)
                        state { enumLoad.initializeNew() }
                        state {
                            enumLoad.field(KexString(), "name").initialize(const(enumName))
                        }
                        state {
                            enumLoad.field(KexInt, "ordinal").initialize(const(enumOrdinal))
                        }
                        state {
                            enumFieldTerm.initialize(enumLoad)
                        }
                        enumFields += enumLoad
                    }
                }
            } catch (e: Throwable) {
                log.error("Error while inlining enum constant ${staticClass.type}", e)
            }
            return enumFields to state.apply()
        }
    }

    private fun mapEnumTerms(ps: PredicateState, enumClasses: Set<KexType>): PredicateState {
        val builder = StateBuilder()
        for (enumClass in enumClasses) {
            builder += getEnumInitializers(context, enumClass).second
        }

        val enumValueTerms = TermCollector
            .getFullTermSet(ps)
            .asSequence()
            .filter { it.isEnum }
            .filterNot { it.type is KexReference }
            .filterNot { it is FieldLoadTerm }
            .filterNot { it is StaticClassRefTerm }
            .filterNotTo(mutableListOf()) {
                it in getEnumInitializers(context, it.type).first
            }

        builder += ps

        for (enumValueTerm in enumValueTerms) {
            var constraint: Term? = null
            for (constant in getEnumInitializers(context, enumValueTerm.type).first) {
                constraint = when (constraint) {
                    null -> term { enumValueTerm eq constant }
                    else -> term { constraint!! or (enumValueTerm eq constant) }
                }
            }

            constraint?.apply {
                builder += axiom { constraint equality true }
            }
        }
        return builder.apply()
    }

    private fun findAccessedEnums(ps: PredicateState): Set<KexType> = TermCollector
        .getFullTermSet(ps)
        .mapNotNullTo(mutableSetOf()) {
            val kfgType = it.type.getKfgType(context.types)
            when {
                kfgType is ClassType && kfgType.klass.isEnum -> kfgType.kexType
                else -> null
            }
        }
        .filterNotTo(mutableSetOf()) { type ->
            val kfgType = type.getKfgType(context.types)
            ignores.any { filter -> filter.matches(kfgType) }
        }

    override fun apply(ps: PredicateState): PredicateState = apply(ps, findAccessedEnums(ps))

    private fun apply(ps: PredicateState, enumClasses: Set<KexType>) = tryOrNull {
        mapEnumTerms(ps, enumClasses).simplify()
    } ?: ps

    override fun apply(state: IncrementalPredicateState): IncrementalPredicateState {
        val enumConstantsMap = buildSet {
            addAll(findAccessedEnums(state.state))
            for (query in state.queries) {
                addAll(findAccessedEnums(query.hardConstraints))
            }
        }
        return IncrementalPredicateState(
            apply(state.state, enumConstantsMap),
            state.queries
        )
    }
}
