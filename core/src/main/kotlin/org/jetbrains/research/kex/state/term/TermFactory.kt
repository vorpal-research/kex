package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode
import org.jetbrains.research.kfg.ir.value.instruction.UnaryOpcode
import org.jetbrains.research.kfg.type.ClassType

object TermFactory {
    fun getThis(type: KexType) = getValue(type, "this")
    fun getThis(`class`: Class) = getThis(KexClass(`class`))
    fun getArgument(argument: Argument) = getArgument(argument.type.kexType, argument.index)
    fun getArgument(type: KexType, index: Int) = ArgumentTerm(type, index)

    fun getConstant(const: Constant) = when (const) {
        is BoolConstant -> getBool(const)
        is ByteConstant -> getByte(const)
        is ShortConstant -> getShort(const)
        is IntConstant -> getInt(const)
        is LongConstant -> getLong(const)
        is FloatConstant -> getFloat(const)
        is DoubleConstant -> getDouble(const)
        is StringConstant -> getString(const)
        is NullConstant -> getNull()
        is ClassConstant -> getClass(const)
        else -> unreachable { log.error("Unknown constant type: $const") }
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
    fun getInt(value: Int) = ConstIntTerm(value)
    fun getInt(const: IntConstant) = getInt(const.value)
    fun getLong(value: Long) = ConstLongTerm(value)
    fun getLong(const: LongConstant) = getLong(const.value)
    fun getFloat(value: Float) = ConstFloatTerm(value)
    fun getFloat(const: FloatConstant) = getFloat(const.value)
    fun getDouble(value: Double) = ConstDoubleTerm(value)
    fun getDouble(const: DoubleConstant) = getDouble(const.value)
    fun getString(type: KexType, value: String) = ConstStringTerm(type, value)
    fun getString(value: String) = ConstStringTerm(TF.stringType.kexType, value)
    fun getString(const: StringConstant) = getString(const.value)
    fun getNull() = NullTerm()
    fun getClass(`class`: Class) = getClass(KexClass(`class`), `class`)
    fun getClass(type: KexType, `class`: Class) = ConstClassTerm(type, `class`)
    fun getClass(type: KexType) = ConstClassTerm(type,
            (type as? KexClass)?.`class` ?: unreachable({ log.debug("Non-ref type of class constant") }))

    fun getClass(const: ClassConstant) = ConstClassTerm(const.type.kexType,
            (const.type as? ClassType)?.`class` ?: unreachable({ log.debug("Non-ref type of class constant") }))

    fun getUnaryTerm(operand: Term, opcode: UnaryOpcode) = when (opcode) {
        UnaryOpcode.NEG -> getNegTerm(operand)
        UnaryOpcode.LENGTH -> getArrayLength(operand)
    }

    fun getArrayLength(arrayRef: Term) = getArrayLength(KexInt, arrayRef)
    fun getArrayLength(type: KexType, arrayRef: Term) = ArrayLengthTerm(type, arrayRef)

    fun getArrayIndex(arrayRef: Term, index: Term): Term {
        val arrayType = arrayRef.type as? KexArray
                ?: unreachable { log.debug("Non-array type of array load term operand") }
        return getArrayIndex(arrayType, arrayRef, index)
    }

    fun getArrayIndex(type: KexType, arrayRef: Term, index: Term) = ArrayIndexTerm(type, arrayRef, index)

    fun getNegTerm(operand: Term) = getNegTerm(operand.type, operand)
    fun getNegTerm(type: KexType, operand: Term) = NegTerm(type, operand)

    fun getArrayLoad(arrayRef: Term): Term {
        val arrayType = arrayRef.type as? KexArray
                ?: unreachable { log.debug("Non-array type of array load term operand") }
        return getArrayLoad(arrayType.element, arrayRef)
    }

    fun getArrayLoad(type: KexType, arrayRef: Term) = ArrayLoadTerm(type, arrayRef)

    fun getFieldLoad(type: KexType, field: Term) = FieldLoadTerm(type, field)

    fun getBinary(opcode: BinaryOpcode, lhv: Term, rhv: Term): Term {
        val merged = mergeTypes(setOf(lhv.type, rhv.type))
        return getBinary(merged, opcode, lhv, rhv)
    }

    fun getBinary(type: KexType, opcode: BinaryOpcode, lhv: Term, rhv: Term) = BinaryTerm(type, opcode, lhv, rhv)

    fun getBound(ptr: Term) = getBound(KexInt, ptr)
    fun getBound(type: KexType, ptr: Term) = BoundTerm(type, ptr)

    fun getCall(method: Method, arguments: List<Term>) = getCall(method.desc.retval.kexType, method, arguments)
    fun getCall(method: Method, objectRef: Term, arguments: List<Term>) =
            getCall(method.desc.retval.kexType, objectRef, method, arguments)

    fun getCall(type: KexType, method: Method, arguments: List<Term>) =
            CallTerm(type, getClass(method.`class`), method, arguments)

    fun getCall(type: KexType, objectRef: Term, method: Method, arguments: List<Term>) =
            CallTerm(type, objectRef, method, arguments)

    fun getCast(type: KexType, operand: Term) = CastTerm(type, operand)
    fun getCmp(opcode: CmpOpcode, lhv: Term, rhv: Term): Term {
        val resType = when (opcode) {
            is CmpOpcode.Cmpg -> KexInt
            is CmpOpcode.Cmpl -> KexInt
            else -> KexBool
        }
        return getCmp(resType, opcode, lhv, rhv)
    }

    fun getCmp(type: KexType, opcode: CmpOpcode, lhv: Term, rhv: Term) = CmpTerm(type, opcode, lhv, rhv)

    fun getField(type: KexType, owner: Term, name: Term) = FieldTerm(type, owner, name)
    fun getField(type: KexType, classType: Class, name: Term) = FieldTerm(type, getClass(classType), name)

    fun getInstanceOf(checkedType: KexType, operand: Term) = InstanceOfTerm(checkedType, operand)

    fun getReturn(method: Method) = getReturn(method.desc.retval.kexType, method)
    fun getReturn(type: KexType, method: Method) = ReturnValueTerm(type, method)

    fun getValue(value: Value) = when (value) {
        is Argument -> getArgument(value)
        is Constant -> getConstant(value)
        is ThisRef -> getThis(value.type.kexType)
        else -> getValue(value.type.kexType, value.toString())
    }

    fun getValue(type: KexType, name: String) = getValue(type, getString(name))
    fun getValue(type: KexType, name: Term) = ValueTerm(type, name)
}