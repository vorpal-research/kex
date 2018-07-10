package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode
import org.jetbrains.research.kfg.ir.value.instruction.UnaryOpcode
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.mergeTypes

object TermFactory : Loggable {
    fun getThis(type: Type) = getValue(type, "this")
    fun getThis(`class`: Class) = getThis(TF.getRefType(`class`))
    fun getArgument(argument: Argument) = getArgument(argument.type, argument.index)
    fun getArgument(type: Type, index: Int) = ArgumentTerm(type, index)

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
    fun getString(type: Type, value: String) = ConstStringTerm(type, value)
    fun getString(value: String) = ConstStringTerm(TF.getString(), value)
    fun getString(const: StringConstant) = getString(const.value)
    fun getNull() = NullTerm()
    fun getClass(`class`: Class) = getClass(TF.getRefType(`class`), `class`)
    fun getClass(type: Type, `class`: Class) = ConstClassTerm(type, `class`)
    fun getClass(type: Type) = ConstClassTerm(type,
            (type as? ClassType)?.`class` ?: unreachable({ log.debug("Non-ref type of class constant") }))
    fun getClass(const: ClassConstant) = ConstClassTerm(const.type,
            (const.type as? ClassType)?.`class` ?: unreachable({ log.debug("Non-ref type of class constant") }))

    fun getUnaryTerm(operand: Term, opcode: UnaryOpcode) = when (opcode) {
        UnaryOpcode.NEG -> getNegTerm(operand)
        UnaryOpcode.LENGTH -> getArrayLength(operand)
    }

    fun getArrayLength(arrayRef: Term) = getArrayLength(TF.getIntType(), arrayRef)
    fun getArrayLength(type: Type, arrayRef: Term) = ArrayLengthTerm(type, arrayRef)
    fun getNegTerm(operand: Term) = getNegTerm(operand.type, operand)
    fun getNegTerm(type: Type, operand: Term) = NegTerm(type, operand)

    fun getArrayLoad(arrayRef: Term, index: Term): Term {
        val arrayType = arrayRef.type as? ArrayType
                ?: unreachable { log.debug("Non-array type of array load term operand") }
        return getArrayLoad(arrayType.component, arrayRef, index)
    }

    fun getArrayLoad(type: Type, arrayRef: Term, index: Term) = ArrayLoadTerm(type, arrayRef, index)

    fun getFieldLoad(type: Type, objectRef: Term, fieldName: Term) = FieldLoadTerm(type, objectRef, fieldName)
    fun getFieldLoad(type: Type, classType: Class, fieldName: Term) = getFieldLoad(type, TF.getRefType(classType), fieldName)
    fun getFieldLoad(type: Type, classType: Type, fieldName: Term) = FieldLoadTerm(type, getClass(classType), fieldName)

    fun getBinary(opcode: BinaryOpcode, lhv: Term, rhv: Term): Term {
        val merged = mergeTypes(setOf(lhv.type, rhv.type))
                ?: error(log.error("Cannot merge types of binary term operands: $lhv and $rhv"))
        return getBinary(merged, opcode, lhv, rhv)
    }

    fun getBinary(type: Type, opcode: BinaryOpcode, lhv: Term, rhv: Term) = BinaryTerm(type, opcode, lhv, rhv)

    fun getCall(method: Method, arguments: List<Term>) = getCall(method.desc.retval, method, arguments)
    fun getCall(method: Method, objectRef: Term, arguments: List<Term>) =
            getCall(method.desc.retval, objectRef, method, arguments)

    fun getCall(type: Type, method: Method, arguments: List<Term>) =
            CallTerm(type, getClass(method.`class`), method, arguments)

    fun getCall(type: Type, objectRef: Term, method: Method, arguments: List<Term>) =
            CallTerm(type, objectRef, method, arguments)

    fun getCast(type: Type, operand: Term) = CastTerm(type, operand)
    fun getCmp(opcode: CmpOpcode, lhv: Term, rhv: Term): Term {
        val resType = when (opcode) {
            is CmpOpcode.Cmpg -> TF.getIntType()
            is CmpOpcode.Cmpl -> TF.getIntType()
            else -> TF.getBoolType()
        }
        return getCmp(resType, opcode, lhv, rhv)
    }

    fun getCmp(type: Type, opcode: CmpOpcode, lhv: Term, rhv: Term) = CmpTerm(type, opcode, lhv, rhv)

    fun getInstanceOf(checkedType: Type, operand: Term) = InstanceOfTerm(checkedType, operand)

    fun getReturn(method: Method) = getReturn(method.desc.retval, method)
    fun getReturn(type: Type, method: Method) = ReturnValueTerm(type, method)

    fun getValue(value: Value) = when (value) {
        is Argument -> getArgument(value)
        is Constant -> getConstant(value)
        is ThisRef -> getThis(value.type)
        else -> getValue(value.type, value.toString())
    }

    fun getValue(type: Type, name: String) = getValue(type, getString(name))
    fun getValue(type: Type, name: Term) = ValueTerm(type, name)
}