package org.vorpal.research.kex.state.transformer

import kotlinx.metadata.isNullable
import kotlinx.metadata.jvm.KotlinClassMetadata
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.ktype.KexArray
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexPointer
import org.vorpal.research.kex.ktype.KexReference
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.StateBuilder
import org.vorpal.research.kex.state.predicate.CallPredicate
import org.vorpal.research.kex.state.predicate.EqualityPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.assume
import org.vorpal.research.kex.state.term.ArrayIndexTerm
import org.vorpal.research.kex.state.term.ArrayLengthTerm
import org.vorpal.research.kex.state.term.ArrayLoadTerm
import org.vorpal.research.kex.state.term.CallTerm
import org.vorpal.research.kex.state.term.FieldLoadTerm
import org.vorpal.research.kex.state.term.FieldTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.util.*
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.type.ArrayType
import org.vorpal.research.kthelper.collection.dequeOf
import org.vorpal.research.kthelper.`try`
import org.vorpal.research.kthelper.tryOrNull
import kotlin.collections.dropLast

class ReflectionInfoAdapter(
    val method: Method,
    val loader: ClassLoader,
    private val ignores: Set<Term> = setOf()
) : RecollectingTransformer<ReflectionInfoAdapter> {
    val cm get() = method.cm
    val types get() = method.cm.type

    override val builders = dequeOf(StateBuilder())

    private data class ArrayElementInfo(val nullable: Boolean)

    private val arrayElementInfo = hashMapOf<Term, ArrayElementInfo>()

    override fun apply(ps: PredicateState): PredicateState {
        val useReflectionInfo = kexConfig.getBooleanValue("kex", "useReflectionInfo", true)
        if (!useReflectionInfo) return ps

        val (_, arguments) = collectArguments(ps)
        val methodClassType = KexClass(method.klass.fullName).getKfgType(types)
        val clazz = `try` { loader.loadClass(methodClassType) }.getOrNull() ?: return super.apply(ps)
        val kMetadata = `try` { clazz.getKotlinMetadata() }.getOrNull() ?: return super.apply(ps)
        if (kMetadata !is KotlinClassMetadata.Class) return super.apply(ps)
        val kFunction = kMetadata.kmClass.getKFunction(method) ?: return super.apply(ps)

        val parameters = kFunction.valueParameters.mapIndexed { index, param -> index to param }

        for ((param, type) in parameters.zip(method.argTypes)) {
            val arg = arguments[param.first] ?: continue
            if (arg in ignores) continue

            if (!param.second.type.isNullable) {
                currentBuilder += assume { arg inequality null }
            }

            if (type is ArrayType) {
                arrayElementInfo[arg] = ArrayElementInfo(nullable = type.kexType.isElementNullable(param.second.type))
            }
        }

        return super.apply(ps)
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm

        val methodClassType = KexClass(call.method.klass.fullName).getKfgType(types)
        val clazz = `try` { loader.loadClass(methodClassType) }.getOrNull() ?: return predicate
        val kMetadata = `try` { clazz.getKotlinMetadata() }.getOrNull() ?: return predicate
        if (kMetadata !is KotlinClassMetadata.Class) return predicate
        val kFunction = kMetadata.kmClass.getKFunction(method) ?: return predicate

        if (!predicate.hasLhv) return predicate

        currentBuilder += predicate
        val lhv = predicate.lhv
        if (lhv.type.isNonNullable(kFunction.returnType) && lhv !in ignores)
            currentBuilder += assume { lhv inequality null }

        if (lhv.type is KexArray) {
            arrayElementInfo[lhv] = ArrayElementInfo(nullable = lhv.type.isElementNullable(kFunction.returnType))
        }

        return nothing()
    }

    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        val adaptedPredicates = when (predicate.rhv) {
            is FieldLoadTerm -> adaptFieldLoad(predicate)
            is ArrayLoadTerm -> adaptArrayLoad(predicate)
            is ArrayLengthTerm -> adaptArrayLength(predicate)
            else -> listOf(predicate)
        }

        adaptedPredicates.dropLast(1).forEach { currentBuilder += it }
        return adaptedPredicates.last()
    }

    private fun adaptFieldLoad(predicate: EqualityPredicate): List<Predicate> {
        // TODO: refactor with kotlinx-metadata
        val result = arrayListOf<Predicate>()
        result += predicate

        val field = (predicate.rhv as FieldLoadTerm).field as FieldTerm
        val fieldType = (field.type as KexReference).reference
        val kfgClass = cm[field.klass]
        val actualField = tryOrNull { field.unmappedKfgField(cm) } ?: return result

        val klass = tryOrNull { loader.loadKClass(kfgClass) } ?: return result
        val prop = klass.getKProperty(actualField)
        val returnType = tryOrNull { prop?.getter?.returnType }

        if (returnType != null && fieldType.isNonNullable(returnType) && field !in ignores) {
            result += assume { predicate.lhv inequality null }
        }

        return result
    }

    private fun adaptArrayLength(predicate: EqualityPredicate): List<Predicate> {
        val result = arrayListOf<Predicate>()
        result += predicate
        result += assume { (predicate.lhv ge 0) equality true }
        return result
    }

    private fun adaptArrayLoad(predicate: EqualityPredicate): List<Predicate> {
        val result = arrayListOf<Predicate>()
        result += predicate

        val lhv = predicate.lhv
        val arrayLoad = predicate.rhv as ArrayLoadTerm
        val arrayIndex = arrayLoad.arrayRef as ArrayIndexTerm

        val isNonNullable = arrayElementInfo[arrayIndex.arrayRef]?.nullable?.not() ?: false
        if (lhv.type is KexPointer && isNonNullable && lhv !in ignores) {
            currentBuilder += assume { lhv inequality null }
        }
        return result
    }
}
