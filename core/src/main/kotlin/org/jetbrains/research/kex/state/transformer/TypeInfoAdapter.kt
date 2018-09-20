package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.KexPointer
import org.jetbrains.research.kex.ktype.KexReference
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.CallTerm
import org.jetbrains.research.kex.state.term.ConstStringTerm
import org.jetbrains.research.kex.state.term.FieldLoadTerm
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kex.util.getClass
import org.jetbrains.research.kex.util.toInt
import org.jetbrains.research.kex.util.tryOrNull
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties

class TypeInfoAdapter(val method: Method, val loader: ClassLoader) : RecollectingTransformer<TypeInfoAdapter> {
    override val builders = ArrayDeque<StateBuilder>()

    init {
        builders.add(StateBuilder())
    }

    companion object {
        private val intrinsics = CM.getByName("kotlin/jvm/internal/Intrinsics")
        val checkNotNull = intrinsics.getMethod(
                "checkParameterIsNotNull",
                MethodDesc(
                        arrayOf(TF.objectType, TF.stringType),
                        TF.voidType
                )
        )
    }

    private val KType.isNonNullable get() = this.isMarkedNullable.not()

    private fun KexType.isNonNullable(kType: KType) = when (this) {
        is KexPointer -> kType.isNonNullable
        else -> false
    }

    private fun KFunction<*>.eq(method: Method): Boolean {
        val parameters = this.parameters.drop(method.isAbstract.not().toInt())

        return this.name == method.name && parameters.zip(method.argTypes).fold(true) { acc, pair ->
            val type = pair.first.type
            val paramType = type.classifier?.toString() ?: type.toString()
            acc && paramType == pair.second.canonicalDesc
        }
    }

    private fun getKClass(type: KexType) = getClass(type.kfgType, loader).kotlin

    private fun getKFunction(method: Method) =
            tryOrNull { getKClass(KexClass(method.`class`)).declaredMemberFunctions }?.find { it.eq(method) }

    private fun getKProperty(field: Field) =
            tryOrNull { getKClass(KexClass(field.`class`)).declaredMemberProperties }?.find { it.name == field.name }

    override fun apply(ps: PredicateState): PredicateState {
        if (!method.isAbstract) {
            val `null` = tf.getNull()
            val `this` = tf.getThis(method.`class`)
            currentBuilder += pf.getInequality(`this`, `null`, PredicateType.Assume())
        }
        return super.apply(ps)
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm

        if (call.method == checkNotNull) return pf.getInequality(call.arguments[0], tf.getNull(), PredicateType.Assume())

        val kFunction = getKFunction(call.method)
        if (!predicate.hasLhv || kFunction == null) return predicate

        val lhv = predicate.lhv
        return when {
            lhv.type.isNonNullable(kFunction.returnType) -> pf.getInequality(lhv, tf.getNull(), PredicateType.Assume())
            else -> predicate
        }
    }

    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        val adaptedPredicates = when {
            predicate.rhv is FieldLoadTerm -> adaptFieldLoad(predicate)
            else -> listOf(predicate)
        }

        adaptedPredicates.dropLast(1).forEach { currentBuilder += it }
        return adaptedPredicates.last()
    }

    private fun adaptFieldLoad(predicate: EqualityPredicate): List<Predicate> {
        val result = arrayListOf<Predicate>()
        result += predicate

        val field = (predicate.rhv as FieldLoadTerm).field as FieldTerm
        val fieldType = (field.type as KexReference).reference
        val `class` = field.getClass()
        val actualField = `class`.getField((field.fieldName as ConstStringTerm).name, fieldType.kfgType)

        val prop = getKProperty(actualField)
        val returnType = tryOrNull { prop?.getter?.returnType }

        if (returnType != null && fieldType.isNonNullable(returnType)) {
            result += pf.getInequality(predicate.lhv, tf.getNull(), PredicateType.Assume())
        }

        return result
    }
}