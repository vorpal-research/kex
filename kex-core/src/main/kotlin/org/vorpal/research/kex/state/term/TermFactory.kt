@file:Suppress("MemberVisibilityCanBePrivate")

package org.vorpal.research.kex.state.term

import org.vorpal.research.kex.ktype.KexArray
import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.ktype.KexByte
import org.vorpal.research.kex.ktype.KexChar
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexDouble
import org.vorpal.research.kex.ktype.KexFloat
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.KexJavaClass
import org.vorpal.research.kex.ktype.KexLong
import org.vorpal.research.kex.ktype.KexPointer
import org.vorpal.research.kex.ktype.KexReference
import org.vorpal.research.kex.ktype.KexShort
import org.vorpal.research.kex.ktype.KexString
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.ktype.mergeTypes
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.Argument
import org.vorpal.research.kfg.ir.value.BoolConstant
import org.vorpal.research.kfg.ir.value.ByteConstant
import org.vorpal.research.kfg.ir.value.CharConstant
import org.vorpal.research.kfg.ir.value.ClassConstant
import org.vorpal.research.kfg.ir.value.Constant
import org.vorpal.research.kfg.ir.value.DoubleConstant
import org.vorpal.research.kfg.ir.value.FloatConstant
import org.vorpal.research.kfg.ir.value.IntConstant
import org.vorpal.research.kfg.ir.value.LongConstant
import org.vorpal.research.kfg.ir.value.NullConstant
import org.vorpal.research.kfg.ir.value.ShortConstant
import org.vorpal.research.kfg.ir.value.StringConstant
import org.vorpal.research.kfg.ir.value.ThisRef
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.instruction.BinaryOpcode
import org.vorpal.research.kfg.ir.value.instruction.CmpOpcode
import org.vorpal.research.kfg.ir.value.instruction.UnaryOpcode
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

object TermFactory {
    fun getThis(type: KexType) = getValue(type, "this")
    fun getArgument(argument: Argument) = getArgument(argument.type.kexType, argument.index)
    fun getArgument(type: KexType, index: Int) = ArgumentTerm(type, index)

    fun getConstant(const: Constant) = when (const) {
        is BoolConstant -> getBool(const)
        is ByteConstant -> getByte(const)
        is ShortConstant -> getShort(const)
        is CharConstant -> getChar(const)
        is IntConstant -> getInt(const)
        is LongConstant -> getLong(const)
        is FloatConstant -> getFloat(const)
        is DoubleConstant -> getDouble(const)
        is StringConstant -> getString(const)
        is NullConstant -> getNull()
        is ClassConstant -> getClass(const)
        else -> unreachable { log.error("Unknown constant type: $const of type ${const::class}") }
    }

    fun <T : Number> getConstant(number: T) = when (number) {
        is Long -> getLong(number)
        is Int -> getInt(number)
        is Short -> getShort(number)
        is Byte -> getByte(number)
        is Double -> getDouble(number)
        is Float -> getFloat(number)
        else -> unreachable("Unknown numeric type")
    }

    fun getTrue() = getBool(true)
    fun getFalse() = getBool(false)
    fun getBool(value: Boolean) = ConstBoolTerm(value)
    fun getBool(const: BoolConstant) = getBool(const.value)
    fun getByte(value: Byte) = ConstByteTerm(value)
    fun getByte(const: ByteConstant) = getByte(const.value)
    fun getShort(value: Short) = ConstShortTerm(value)
    fun getShort(const: ShortConstant) = getShort(const.value)
    fun getChar(value: Char) = ConstCharTerm(value)
    fun getChar(const: CharConstant) = getChar(const.value)
    fun getInt(value: Int) = ConstIntTerm(value)
    fun getInt(const: IntConstant) = getInt(const.value)
    fun getLong(value: Long) = ConstLongTerm(value)
    fun getLong(const: LongConstant) = getLong(const.value)
    fun getFloat(value: Float) = ConstFloatTerm(value)
    fun getFloat(const: FloatConstant) = getFloat(const.value)
    fun getDouble(value: Double) = ConstDoubleTerm(value)
    fun getDouble(const: DoubleConstant) = getDouble(const.value)
    fun getString(type: KexType, value: String) = ConstStringTerm(type, value)
    fun getString(value: String) = ConstStringTerm(KexString(), value)
    fun getString(const: StringConstant) = getString(const.value)
    fun getNull() = NullTerm()
    fun getClass(klass: Class) = getClass(KexJavaClass(), klass.kexType)
    fun getClass(type: KexType, constantType: KexType) = ConstClassTerm(type, constantType)
    fun getClass(const: ClassConstant) = ConstClassTerm(const.type.kexType, const.constantType.kexType)
    fun getStaticRef(klass: Class) = getStaticRef(klass.kexType)
    fun getStaticRef(klass: KexClass) = StaticClassRefTerm(klass)

    fun getUnaryTerm(operand: Term, opcode: UnaryOpcode) = when (opcode) {
        UnaryOpcode.NEG -> getNegTerm(operand)
        UnaryOpcode.LENGTH -> getArrayLength(operand)
    }

    fun getArrayLength(arrayRef: Term) = ArrayLengthTerm(arrayRef)

    fun getArrayIndex(arrayRef: Term, index: Term): Term {
        val arrayType = arrayRef.type as? KexArray
            ?: unreachable { log.debug("Non-array type of array load term operand") }
        return getArrayIndex(KexReference(arrayType.element), arrayRef, index)
    }

    fun getArrayIndex(type: KexType, arrayRef: Term, index: Term) = ArrayIndexTerm(type, arrayRef, index)

    fun getNegTerm(operand: Term) = getNegTerm(operand.type, operand)
    fun getNegTerm(type: KexType, operand: Term) = NegTerm(type, operand)

    fun getArrayLoad(arrayRef: Term): Term {
        val arrayType = arrayRef.type as? KexReference
            ?: unreachable { log.debug("Non-array type of array load term operand") }
        return getArrayLoad(arrayType.reference, arrayRef)
    }

    fun getArrayLoad(type: KexType, arrayRef: Term) = ArrayLoadTerm(type, arrayRef)

    fun getFieldLoad(type: KexType, field: Term) = FieldLoadTerm(type, field)

    fun getBinary(tf: TypeFactory, opcode: BinaryOpcode, lhv: Term, rhv: Term): Term {
        val merged = mergeTypes(tf, setOf(lhv.type, rhv.type))
        return getBinary(merged, opcode, lhv, rhv)
    }

    fun getBinary(type: KexType, opcode: BinaryOpcode, lhv: Term, rhv: Term) = BinaryTerm(type, opcode, lhv, rhv)

    fun getBound(ptr: Term) = getBound(KexInt, ptr)
    fun getBound(type: KexType, ptr: Term) = BoundTerm(type, ptr)

    fun getCall(method: Method, arguments: List<Term>) = getCall(method.returnType.kexType, method, arguments)
    fun getCall(method: Method, objectRef: Term, arguments: List<Term>) =
        getCall(method.returnType.kexType, objectRef, method, arguments)

    fun getCall(type: KexType, method: Method, arguments: List<Term>) =
        CallTerm(type, getStaticRef(method.klass), method, arguments)

    fun getCall(type: KexType, objectRef: Term, method: Method, arguments: List<Term>) =
        CallTerm(type, objectRef, method, arguments)

    fun getCast(type: KexType, operand: Term) = CastTerm(type, operand)
    fun getCmp(opcode: CmpOpcode, lhv: Term, rhv: Term): Term {
        val resType = when (opcode) {
            CmpOpcode.CMPG -> KexInt
            CmpOpcode.CMPL -> KexInt
            else -> KexBool
        }
        return getCmp(resType, opcode, lhv, rhv)
    }

    fun getConcat(lhv: Term, rhv: Term): Term = getConcat(KexString(), lhv, rhv)
    fun getConcat(type: KexType, lhv: Term, rhv: Term): Term = ConcatTerm(type, lhv, rhv)

    fun getArrayContains(arrayRef: Term, value: Term): Term = ArrayContainsTerm(arrayRef, value)

    fun getEquals(lhv: Term, rhv: Term): Term {
        ktassert(lhv.type is KexPointer) { log.error("Non-pointer type in equals") }
        ktassert(rhv.type is KexPointer) { log.error("Non-pointer type in equals") }
        return EqualsTerm(lhv, rhv)
    }

    fun getCmp(type: KexType, opcode: CmpOpcode, lhv: Term, rhv: Term) = CmpTerm(type, opcode, lhv, rhv)

    fun getField(type: KexType, owner: Term, name: String) = FieldTerm(type, owner, name)
    fun getField(type: KexType, classType: Class, name: String) = FieldTerm(type, getClass(classType), name)

    fun getInstanceOf(checkedType: KexType, operand: Term) = InstanceOfTerm(checkedType, operand)

    fun getReturn(method: Method) = getReturn(method.returnType.kexType, method)
    fun getReturn(type: KexType, method: Method) = ReturnValueTerm(type, method)

    fun getValue(value: Value) = when (value) {
        is Argument -> getArgument(value)
        is Constant -> getConstant(value)
        is ThisRef -> getThis(value.type.kexType)
        else -> getValue(value.type.kexType, value.toString())
    }

    fun getValue(type: KexType, name: String) = ValueTerm(type, name)

    fun getUndef(type: KexType) = UndefTerm(type)

    fun getStringLength(string: Term) = StringLengthTerm(string)
    fun getSubstring(string: Term, offset: Term, length: Term) = getSubstring(KexString(), string, offset, length)
    fun getSubstring(type: KexType, string: Term, offset: Term, length: Term) =
        SubstringTerm(type, string, offset, length)

    fun getIndexOf(string: Term, substring: Term, offset: Term) = IndexOfTerm(string, substring, offset)
    fun getCharAt(string: Term, index: Term) = CharAtTerm(string, index)
    fun getStringContains(string: Term, substring: Term): Term = StringContainsTerm(string, substring)
    fun getFromString(string: Term, type: KexType): Term = StringParseTerm(type, string)
    fun getToString(value: Term): Term = getToString(KexString(), value)
    fun getToString(type: KexType, value: Term): Term = ToStringTerm(type, value)
    fun getStartsWith(string: Term, prefix: Term): Term = StartsWithTerm(string, prefix)
    fun getEndsWith(string: Term, suffix: Term): Term = EndsWithTerm(string, suffix)

    fun getLambda(type: KexType, params: List<Term>, body: Term) = LambdaTerm(type, params, body)

    fun getForAll(
        start: Term,
        end: Term,
        body: Term
    ) = ForAllTerm(start, end, body)

    fun getExists(
        start: Term,
        end: Term,
        body: Term
    ) = ExistsTerm(start, end, body)

    fun getIte(
        type: KexType,
        cond: Term,
        trueValue: Term,
        falseValue: Term
    ) = IteTerm(type, cond, trueValue, falseValue)

    fun getClassAccess(operand: Term) = getClassAccess(KexJavaClass(), operand)
    fun getClassAccess(type: KexType, operand: Term) = ClassAccessTerm(type, operand)
}

@Suppress("FunctionName")
interface TermBuilder {
    val termFactory get() = TermFactory

    private object TermGenerator {
        private var index = 0

        val nextName: String get() = "term${index++}"

        fun nextTerm(type: KexType) = term { value(type, nextName) }
    }

    fun generate(type: KexType) = TermGenerator.nextTerm(type)

    fun `this`(type: KexType) = termFactory.getThis(type)

    fun default(type: KexType) = when (type) {
        is KexBool -> const(false)
        is KexByte -> const(0.toByte())
        is KexChar -> const(0.toChar())
        is KexShort -> const(0.toShort())
        is KexInt -> const(0)
        is KexLong -> const(0L)
        is KexFloat -> const(0.0f)
        is KexDouble -> const(0.0)
        is KexPointer -> const(null)
        else -> unreachable { log.error("Unknown type $type") }
    }

    fun arg(argument: Argument) = termFactory.getArgument(argument)
    fun arg(type: KexType, index: Int) = termFactory.getArgument(type, index)

    fun const(constant: Constant) = termFactory.getConstant(constant)
    fun const(bool: Boolean) = termFactory.getBool(bool)
    fun const(str: String) = termFactory.getString(str)
    fun const(char: Char) = termFactory.getChar(char)
    fun <T : Number> const(number: T) = termFactory.getConstant(number)

    fun Char.asType(type: KexType): Term = when (type) {
        is KexChar ->  termFactory.getChar(this)
        is KexByte -> termFactory.getByte(this.code.toByte())
        else -> unreachable { log.error("Unexpected cast from char to $type") }
    }

    fun const(nothing: Nothing?) = termFactory.getNull()
    fun `class`(klass: Class) = termFactory.getClass(klass)
    fun `class`(type: KexType, constantType: KexType) = termFactory.getClass(type, constantType)
    fun staticRef(type: Class) = termFactory.getStaticRef(type)
    fun staticRef(type: KexClass) = termFactory.getStaticRef(type)

    fun Term.apply(opcode: UnaryOpcode) = termFactory.getUnaryTerm(this, opcode)
    operator fun Term.not() = termFactory.getNegTerm(this)
    fun Term.length() = when (this.type) {
        is KexArray -> termFactory.getArrayLength(this)
        else -> termFactory.getStringLength(this)
    }

    operator fun Term.get(index: Term) = termFactory.getArrayIndex(this, index)
    operator fun Term.get(index: Int) = termFactory.getArrayIndex(this, const(index))

    fun Term.load() = when (this) {
        is ArrayIndexTerm -> termFactory.getArrayLoad(this)
        is FieldTerm -> {
            val type = (this.type as KexReference).reference
            termFactory.getFieldLoad(type, this)
        }
        else -> unreachable { log.error("Unknown term type in load: $this") }
    }

    infix fun Term.add(rhv: Term) = termFactory.getBinary(type, BinaryOpcode.ADD, this, rhv)
    operator fun Term.plus(rhv: Term) = this add rhv
    operator fun Int.plus(rhv: Term) = const(this) add rhv
    operator fun Double.plus(rhv: Term) = const(this) add rhv

    infix fun Term.sub(rhv: Term) = termFactory.getBinary(type, BinaryOpcode.SUB, this, rhv)
    operator fun Term.minus(rhv: Term) = this sub rhv
    operator fun Int.minus(rhv: Term) = const(this) sub rhv
    operator fun Double.minus(rhv: Term) = const(this) sub rhv

    infix fun Term.mul(rhv: Term) = termFactory.getBinary(type, BinaryOpcode.MUL, this, rhv)
    operator fun Term.times(rhv: Term) = this mul rhv
    operator fun Int.times(rhv: Term) = const(this) mul rhv
    operator fun Double.times(rhv: Term) = const(this) mul rhv

    operator fun Term.div(rhv: Term) = termFactory.getBinary(type, BinaryOpcode.DIV, this, rhv)
    operator fun Int.div(rhv: Term) = const(this) / rhv
    operator fun Double.div(rhv: Term) = const(this) / rhv
    operator fun Term.rem(rhv: Term) = termFactory.getBinary(type, BinaryOpcode.REM, this, rhv)
    operator fun Int.rem(rhv: Term) = const(this) % rhv
    operator fun Double.rem(rhv: Term) = const(this) % rhv

    infix fun Term.shl(shift: Term) = termFactory.getBinary(type, BinaryOpcode.SHL, this, shift)
    infix fun Term.shr(shift: Term) = termFactory.getBinary(type, BinaryOpcode.SHR, this, shift)
    infix fun Term.ushr(shift: Term) = termFactory.getBinary(type, BinaryOpcode.USHR, this, shift)

    infix fun Term.and(rhv: Term) = termFactory.getBinary(type, BinaryOpcode.AND, this, rhv)
    infix fun Term.and(bool: Boolean) = termFactory.getBinary(type, BinaryOpcode.AND, this, const(bool))
    infix fun Term.and(int: Int) = termFactory.getBinary(type, BinaryOpcode.AND, this, const(int))
    infix fun Term.or(rhv: Term) = termFactory.getBinary(type, BinaryOpcode.OR, this, rhv)
    infix fun Term.or(bool: Boolean) = termFactory.getBinary(type, BinaryOpcode.OR, this, const(bool))
    infix fun Term.or(int: Int) = termFactory.getBinary(type, BinaryOpcode.OR, this, const(int))
    infix fun Term.xor(rhv: Term) = termFactory.getBinary(type, BinaryOpcode.XOR, this, rhv)
    infix fun Term.xor(bool: Boolean) = termFactory.getBinary(type, BinaryOpcode.XOR, this, const(bool))

    infix fun Term.implies(rhv: Term) = !this or rhv
    infix fun Term.implies(rhv: Boolean) = !this or rhv

    fun Term.apply(types: TypeFactory, opcode: BinaryOpcode, rhv: Term) = termFactory.getBinary(types, opcode, this, rhv)
    fun Term.apply(type: KexType, opcode: BinaryOpcode, rhv: Term) = termFactory.getBinary(type, opcode, this, rhv)
    fun Term.apply(opcode: CmpOpcode, rhv: Term) = termFactory.getCmp(opcode, this, rhv)

    infix fun Term.eq(rhv: Term) = termFactory.getCmp(CmpOpcode.EQ, this, rhv)
    infix fun <T : Number> Term.eq(rhv: T) = termFactory.getCmp(CmpOpcode.EQ, this, const(rhv))
    infix fun Term.eq(rhv: Boolean) = termFactory.getCmp(CmpOpcode.EQ, this, const(rhv))
    infix fun Term.eq(rhv: Nothing?) = termFactory.getCmp(CmpOpcode.EQ, this, const(rhv))

    infix fun Term.neq(rhv: Term) = termFactory.getCmp(CmpOpcode.NEQ, this, rhv)
    infix fun <T : Number> Term.neq(rhv: T) = termFactory.getCmp(CmpOpcode.NEQ, this, const(rhv))
    infix fun Term.neq(rhv: Boolean) = termFactory.getCmp(CmpOpcode.NEQ, this, const(rhv))
    infix fun Term.neq(rhv: Nothing?) = termFactory.getCmp(CmpOpcode.NEQ, this, const(rhv))

    infix fun Term.lt(rhv: Term) = termFactory.getCmp(CmpOpcode.LT, this, rhv)
    infix fun <T : Number> Term.lt(rhv: T) = termFactory.getCmp(CmpOpcode.LT, this, const(rhv))
    infix fun Term.lt(rhv: Boolean) = termFactory.getCmp(CmpOpcode.LT, this, const(rhv))
    infix fun Term.lt(rhv: Nothing?) = termFactory.getCmp(CmpOpcode.LT, this, const(rhv))

    infix fun Term.gt(rhv: Term) = termFactory.getCmp(CmpOpcode.GT, this, rhv)
    infix fun <T : Number> Term.gt(rhv: T) = termFactory.getCmp(CmpOpcode.GT, this, const(rhv))
    infix fun Term.gt(rhv: Boolean) = termFactory.getCmp(CmpOpcode.GT, this, const(rhv))
    infix fun Term.gt(rhv: Nothing?) = termFactory.getCmp(CmpOpcode.GT, this, const(rhv))

    infix fun Term.le(rhv: Term) = termFactory.getCmp(CmpOpcode.LE, this, rhv)
    infix fun <T : Number> Term.le(rhv: T) = termFactory.getCmp(CmpOpcode.LE, this, const(rhv))
    infix fun Term.le(rhv: Boolean) = termFactory.getCmp(CmpOpcode.LE, this, const(rhv))
    infix fun Term.le(rhv: Nothing?) = termFactory.getCmp(CmpOpcode.LE, this, const(rhv))

    infix fun Term.ge(rhv: Term) = termFactory.getCmp(CmpOpcode.GE, this, rhv)
    infix fun <T : Number> Term.ge(rhv: T) = termFactory.getCmp(CmpOpcode.GE, this, const(rhv))
    infix fun Term.ge(rhv: Boolean) = termFactory.getCmp(CmpOpcode.GE, this, const(rhv))
    infix fun Term.ge(rhv: Nothing?) = termFactory.getCmp(CmpOpcode.GE, this, const(rhv))

    infix fun Term.cmp(rhv: Term) = termFactory.getCmp(CmpOpcode.CMP, this, rhv)
    infix fun Term.cmpg(rhv: Term) = termFactory.getCmp(CmpOpcode.CMPG, this, rhv)
    infix fun Term.cmpl(rhv: Term) = termFactory.getCmp(CmpOpcode.CMPL, this, rhv)

    infix fun Term.`in`(container: Term) = when (container.type) {
        is KexArray -> termFactory.getArrayContains(container, this)
        else -> termFactory.getStringContains(container, this)
    }

    infix fun Term.equls(rhv: Term) = termFactory.getEquals(this, rhv)

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated(message = "not used in current SMT model")
    fun Term.bound() = termFactory.getBound(this)

    fun Term.call(method: Method, arguments: List<Term>) = termFactory.getCall(method, this, arguments)
    fun Term.call(method: Method, vararg arguments: Term) = termFactory.getCall(method, this, arguments.toList())

    fun Term.field(type: KexReference, name: String) = termFactory.getField(type, this, name)
    fun Term.field(type: KexType, name: String) = termFactory.getField(KexReference(type), this, name)
    fun Term.field(field: Pair<String, KexType>) = termFactory.getField(KexReference(field.second), this, field.first)

    infix fun Term.`as`(type: KexType) = termFactory.getCast(type, this)
    infix fun Term.`is`(type: KexType) = termFactory.getInstanceOf(type, this)

    infix fun Term.`++`(rhv: Term) = termFactory.getConcat(this, rhv)
    infix fun Term.`++`(rhv: String) = termFactory.getConcat(this, const(rhv))
    infix fun String.`++`(rhv: String) = termFactory.getConcat(const(this), const(rhv))
    infix fun String.`++`(rhv: Term) = termFactory.getConcat(const(this), rhv)

    fun Term.substring(offset: Term, length: Term) = termFactory.getSubstring(this, offset, length)
    fun Term.substring(offset: Int, length: Int) = this.substring(const(offset), const(length))

    fun Term.indexOf(substring: Term, offset: Term) = termFactory.getIndexOf(this, substring, offset)
    fun Term.indexOf(substring: Term) = this.indexOf(substring, const(0))
    fun Term.indexOf(substring: String, offset: Term) = termFactory.getIndexOf(this, const(substring), offset)
    fun Term.indexOf(substring: String, offset: Int) = termFactory.getIndexOf(this, const(substring), const(offset))
    fun Term.indexOf(substring: String) = this.indexOf(const(substring), const(0))

    fun Term.charAt(index: Term) = termFactory.getCharAt(this, index)
    fun Term.charAt(index: Int) = this.charAt(const(index))

    fun KexType.fromString(string: Term) = termFactory.getFromString(string, this)
    fun KexType.fromString(string: String) = this.fromString(const(string))

    fun Term.toStr() = termFactory.getToString(this)

    fun Term.startsWith(prefix: Term) = termFactory.getStartsWith(this, prefix)
    fun Term.startsWith(prefix: String) = startsWith(const(prefix))

    fun Term.endsWith(suffix: Term) = termFactory.getEndsWith(this, suffix)
    fun Term.endsWith(suffix: String) = endsWith(const(suffix))

    fun `return`(method: Method) = termFactory.getReturn(method)

    fun value(value: Value) = termFactory.getValue(value)
    fun value(type: KexType, name: String) = termFactory.getValue(type, name)
    fun undef(type: KexType) = termFactory.getUndef(type)

    fun lambda(type: KexType, params: List<Term>, bodyBuilder: TermBuilder.() -> Term) =
        lambda(type, params, bodyBuilder())

    fun lambda(type: KexType, params: List<Term>, body: Term) =
        termFactory.getLambda(type, params, body)

    fun lambda(type: KexType, vararg params: Term, bodyBuilder: TermBuilder.() -> Term) =
        lambda(type, *params, body = bodyBuilder())


    fun lambda(type: KexType, vararg params: Term, body: Term) =
        termFactory.getLambda(type, params.toList(), body)

    fun forAll(start: Term, end: Term, body: Term) = termFactory.getForAll(start, end, body)
    fun forAll(start: Term, end: Term, body: TermBuilder.() -> Term) = forAll(start, end, body())
    fun forAll(start: Int, end: Int, body: Term) = (start..end).forAll(body)
    fun forAll(start: Int, end: Int, body: TermBuilder.() -> Term) = forAll(start, end, body())
    fun forAll(start: Int, end: Term, body: TermBuilder.() -> Term) = forAll(const(start), end, body())
    fun forAll(start: Int, end: Term, body: Term) = forAll(const(start), end, body)
    fun forAll(start: Term, end: Int, body: Term) = forAll(start, const(end), body)
    fun forAll(start: Term, end: Int, body: TermBuilder.() -> Term) = forAll(start, const(end), body())
    fun IntRange.forAll(body: Term) = forAll(const(start), const(last), body)
    fun IntRange.forAll(body: TermBuilder.() -> Term) = forAll(const(start), const(last), body)

    fun exists(start: Term, end: Term, body: Term) = termFactory.getExists(start, end, body)
    fun exists(start: Term, end: Term, body: TermBuilder.() -> Term) = exists(start, end, body())
    fun exists(start: Int, end: Int, body: Term) = (start..end).exists(body)
    fun exists(start: Int, end: Int, body: TermBuilder.() -> Term) = exists(start, end, body())
    fun exists(start: Int, end: Term, body: TermBuilder.() -> Term) = exists(const(start), end, body())
    fun exists(start: Int, end: Term, body: Term) = exists(const(start), end, body)
    fun exists(start: Term, end: Int, body: Term) = exists(start, const(end), body)
    fun exists(start: Term, end: Int, body: TermBuilder.() -> Term) = exists(start, const(end), body())
    fun IntRange.exists(body: Term) = exists(const(start), const(last), body)
    fun IntRange.exists(body: TermBuilder.() -> Term) = exists(const(start), const(last), body)

    fun ite(type: KexType, cond: Term, trueValue: Term, falseValue: Term) = termFactory.getIte(type, cond, trueValue, falseValue)

    val Term.klass get() = termFactory.getClassAccess(this)

    object Terms : TermBuilder
}

inline fun term(body: TermBuilder.() -> Term) = TermBuilder.Terms.body()
