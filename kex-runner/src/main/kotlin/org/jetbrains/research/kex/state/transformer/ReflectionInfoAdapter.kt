package org.jetbrains.research.kex.state.transformer

import com.abdullin.kthelper.logging.log
import com.abdullin.kthelper.toInt
import com.abdullin.kthelper.tryOrNull
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.assume
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.util.loadClass
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.Reference
import org.jetbrains.research.kfg.type.Type
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KType
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmErasure

class ReflectionInfoAdapter(val method: Method, val loader: ClassLoader, val ignores: Set<Term> = setOf()) :
        RecollectingTransformer<ReflectionInfoAdapter> {
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
            else -> kType.arguments[0].type?.isMarkedNullable ?: true
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

    private val KClass<*>.allFunctions
        get() = tryOrNull {
            constructors +
                    staticFunctions +
                    declaredMemberFunctions +
                    declaredMemberExtensionFunctions +
                    declaredMemberProperties.map { it.getter } +
                    declaredMemberExtensionProperties.map { it.getter } +
                    declaredMemberProperties.filterIsInstance<KMutableProperty<*>>().map { it.setter } +
                    declaredMemberExtensionProperties.filterIsInstance<KMutableProperty<*>>().map { it.setter } +
                    staticProperties.map { it.getter } +
                    staticProperties.filterIsInstance<KMutableProperty<*>>().map { it.setter }
        } ?: listOf()

    private fun KClass<*>.find(method: Method) = allFunctions.find { it eq method }

    private fun getKClass(type: KexType) = loader.loadClass(type.getKfgType(types)).kotlin

    private fun getKFunction(method: Method): KFunction<*>? {
        val queue = ArrayDeque<KClass<*>>()
        tryOrNull { getKClass(KexClass(method.`class`.fullname)) }?.apply {
            queue.add(this)
        }
        while (queue.isNotEmpty()) {
            val klass = queue.poll()
            when (val kFunction = klass.find(method)) {
                null -> {
                    val supertypes = tryOrNull { klass.supertypes } ?: listOf()
                    queue.addAll(supertypes.map { it.classifier }.filterIsInstance<KClass<*>>())
                }
                else -> return kFunction
            }
        }
        return null
    }

    private fun getKProperty(field: Field) =
            tryOrNull { getKClass(KexClass(field.`class`.fullname)).declaredMemberProperties }?.find { it.name == field.name }

    override fun apply(ps: PredicateState): PredicateState {
        val (`this`, arguments) = collectArguments(ps)

        if (`this` != null && `this` !in ignores) {
            currentBuilder += assume { `this` inequality null }
        }

        val kFunction = getKFunction(method) ?: run {
            log.warn("Could not load kFunction for $method")
            return super.apply(ps)
        }

        val parameters = when {
            method.isAbstract -> kFunction.parameters.map { it.index to it }
            method.isConstructor -> kFunction.parameters.map { it.index to it }
            else -> kFunction.parameters.drop(1).map { it.index - 1 to it }
        }

        for ((param, type) in parameters.zip(method.argTypes)) {
            val arg = arguments[param.first] ?: continue
            if (arg in ignores) continue

            if (arg.type.isNonNullable(param.second.type)) {
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

        val kFunction = getKFunction(call.method)
        if (!predicate.hasLhv || kFunction == null) return predicate

        currentBuilder += predicate
        val lhv = predicate.lhv
        if (lhv.type.isNonNullable(kFunction.returnType) && lhv !in ignores)
            currentBuilder += assume { lhv inequality null }

        if (lhv.type is KexArray)
            arrayElementInfo[lhv] = ArrayElementInfo(nullable = lhv.type.isElementNullable(kFunction.returnType))

        call.arguments.filter { it.type is KexPointer }.forEach {
            currentBuilder += assume { it equality undef(it.type) }
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
        val kfgClass = cm.getByName(field.klass)
        val actualField = kfgClass.getField((field.fieldName as ConstStringTerm).value, fieldType.getKfgType(types))

        val prop = getKProperty(actualField)
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