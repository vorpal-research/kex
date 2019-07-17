package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.util.*
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.Reference
import org.jetbrains.research.kfg.type.Type
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmErasure

class TypeInfoAdapter(val method: Method, val loader: ClassLoader) : RecollectingTransformer<TypeInfoAdapter> {
    val cm get() = method.cm
    val types get() = method.cm.type

    override val builders = ArrayDeque<StateBuilder>()

    private data class ArrayElementInfo(val nullable: Boolean)

    private val arrayElementInfo = hashMapOf<Term, ArrayElementInfo>()

    init {
        builders.add(StateBuilder())
    }

    private val KType.isNonNullable get() = this.isMarkedNullable.not()

    private fun KexType.isNonNullable(kType: KType) = when (this) {
        is KexPointer -> kType.isNonNullable
        else -> false
    }

    private fun KexType.isElementNullable(kType: KType) = when (this) {
        is KexArray -> when {
            kType.arguments.isEmpty() -> false
            else -> (kType.arguments[0].type
                    ?: unreachable { log.error("No type for array argument") }).isMarkedNullable
        }
        else -> false
    }

    private fun trimClassName(name: String) = buildString {
        val actualName = name.split(" ").last()
        val filtered = actualName.dropWhile { it == '[' }.removeSuffix(";")
        append(actualName.takeWhile { it == '[' })
        append(filtered.dropWhile { it == 'L' })
    }

    private val Type.trimmedName
        get() = when (this) {
            is Reference -> trimClassName(this.canonicalDesc)
            else -> this.name
        }
    private val Class<*>.trimmedName get() = trimClassName(this.toString())

    private infix fun KFunction<*>.eq(method: Method): Boolean {
        val parameters = this.parameters.drop(method.isAbstract.not().toInt())

        val name = tryOrNull { this.javaMethod?.name } ?: this.name
        return name == method.name && parameters.zip(method.argTypes).fold(true) { acc, pair ->
            val type = tryOrNull { pair.first.type.jvmErasure.java }
            acc && type?.trimmedName == pair.second.trimmedName
        }
    }

    private fun getKClass(type: KexType) = getClass(type.getKfgType(types), loader).kotlin

    private fun getKFunction(method: Method) =
            tryOrNull {
                getKClass(KexClass(method.`class`.fullname)).let { klass ->
                    klass.declaredMemberFunctions +
                            klass.declaredMemberProperties.map { it.getter } +
                            klass.declaredMemberProperties.filterIsInstance<KMutableProperty<*>>().map { it.setter }
                }
            }?.find { it eq method }

    private fun getKProperty(field: Field) =
            tryOrNull { getKClass(KexClass(field.`class`.fullname)).declaredMemberProperties }?.find { it.name == field.name }

    override fun apply(ps: PredicateState): PredicateState {
        val (`this`, arguments) = collectArguments(ps)
        val `null` = tf.getNull()

        if (`this` != null) {
            currentBuilder += pf.getInequality(`this`, `null`, PredicateType.Assume())
        }

        val kFunction = getKFunction(method) ?: run {
            log.warn("Could not load kFunction for $method")
            return super.apply(ps)
        }

        val parameters = kFunction.parameters.drop(method.isAbstract.not().toInt())

        for ((param, type) in parameters.zip(method.argTypes)) {
            val arg = arguments[param.index - 1] ?: continue

            if (arg.type.isNonNullable(param.type)) {
                currentBuilder += pf.getInequality(arg, `null`, PredicateType.Assume())
            }

            if (type is ArrayType) {
                arrayElementInfo[arg] = ArrayElementInfo(nullable = type.kexType.isElementNullable(param.type))
            }
        }

        return super.apply(ps)
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm

        val kFunction = getKFunction(call.method)
        if (!predicate.hasLhv || kFunction == null) return predicate

        currentBuilder += predicate
        val lhv = predicate.lhv
        if (lhv.type.isNonNullable(kFunction.returnType))
            currentBuilder += pf.getInequality(lhv, tf.getNull(), PredicateType.Assume())

        if (lhv.type is KexArray)
            arrayElementInfo[lhv] = ArrayElementInfo(nullable = lhv.type.isElementNullable(kFunction.returnType))

        call.arguments.filter { it.type is KexPointer }.forEach {
            currentBuilder += pf.getEquality(it, tf.getUndef(it.type), PredicateType.Assume())
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
        val result = arrayListOf<Predicate>()
        result += predicate

        val field = (predicate.rhv as FieldLoadTerm).field as FieldTerm
        val fieldType = (field.type as KexReference).reference
        val kfgClass = cm.getByName(field.getClass())
        val actualField = kfgClass.getField((field.fieldName as ConstStringTerm).value, fieldType.getKfgType(types))

        val prop = getKProperty(actualField)
        val returnType = tryOrNull { prop?.getter?.returnType }

        if (returnType != null && fieldType.isNonNullable(returnType)) {
            result += pf.getInequality(predicate.lhv, tf.getNull(), PredicateType.Assume())
        }

        return result
    }

    private fun adaptArrayLength(predicate: EqualityPredicate): List<Predicate> {
        val result = arrayListOf<Predicate>()
        result += predicate

        val lhv = predicate.lhv
        result += pf.getEquality(tf.getCmp(CmpOpcode.Ge(), lhv, tf.getInt(0)), tf.getTrue(), PredicateType.Assume())

        return result
    }

    private fun adaptArrayLoad(predicate: EqualityPredicate): List<Predicate> {
        val result = arrayListOf<Predicate>()
        result += predicate

        val lhv = predicate.lhv
        val arrayLoad = predicate.rhv as ArrayLoadTerm
        val arrayIndex = arrayLoad.arrayRef as ArrayIndexTerm

        val isNonNullable = arrayElementInfo[arrayIndex.arrayRef]?.nullable?.not() ?: false
        if (lhv.type is KexPointer && isNonNullable) {
            currentBuilder += pf.getInequality(lhv, tf.getNull(), PredicateType.Assume())
        }
        return result
    }
}