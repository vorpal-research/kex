package org.jetbrains.research.kex.asm.analysis.libchecker

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BodyBlock
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.ValueFactory
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.BoolType
import ru.spbstu.insys.libsl.parser.*
import ru.spbstu.insys.libsl.parser.visitors.AbstractExpressionVisitor

class ContractsGenerator(val method: Method, val cm: ClassManager): AbstractExpressionVisitor<Value>() {
    val contractBlock = BodyBlock("contract")
    val oldCallStorage = mutableListOf<Instruction>()

    private val instructionFactory = InstructionFactory(cm)
    private val valueFactory = ValueFactory(cm)
    private val klass = method.klass

    override fun visitConjunctionNode(node: ConjunctionNode): Instruction {
        var prevValue = visit(node.disjunctions.first()) as Instruction

        for (term in node.disjunctions.drop(1)) {
            val currentValue = visit(term)
            val subInstr = instructionFactory.getBinary(BinaryOpcode.AND, prevValue, currentValue)
            contractBlock.add(subInstr)
            prevValue = subInstr
        }

        return prevValue
    }

    override fun visitDisjunctionNode(node: DisjunctionNode): Value {
        var prevValue = visit(node.TermList.first())

        for (term in node.TermList.drop(1)) {
            val currentValue = visit(term)
            val subInstr = instructionFactory.getBinary(BinaryOpcode.OR, prevValue, currentValue)
            contractBlock.add(subInstr)
            prevValue = subInstr
        }

        return prevValue
    }

    override fun visitDivNode(node: DivNode): Instruction {
        val left = visit(node.left)
        val right = visit(node.right)
        val instr = instructionFactory.getBinary(BinaryOpcode.DIV, left, right)
        contractBlock.add(instr)
        return instr
    }

    override fun visitEqualityNode(node: EqualityNode): Instruction {
        val left = visit(node.left)
        val right = visit(node.right)
        val instr = if (!left.type.isPrimary) {
            equalsWithObject(left, cm[left.type.name], right)
        } else if (!right.type.isPrimary) {
            equalsWithObject(right, cm[right.type.name], left)
        } else {
            instructionFactory.getCmp(BoolType, node.sign.opcode, left, right)
        }
        contractBlock.add(instr)

        return instr
    }

    private fun equalsWithObject(obj: Value, objKlass: Class, other: Value): CallInst {
        val equalsMethod = objKlass.allMethods.firstOrNull { method ->
            method.name == "equals" && method.argTypes.size == 1 && method.argTypes.first() == cm.type.objectType
        } ?: error("cannot find equals method for $objKlass")
        val args = arrayOf(
            obj,
            other
        )

        return instructionFactory.getCall(CallOpcode.VIRTUAL, equalsMethod, equalsMethod.klass, args, true)
    }

    private val EqualitySign.opcode: CmpOpcode
        get() = CmpOpcode.parse(text)

    override fun visitFunctionCallNode(node: FunctionCallNode): Instruction {
        if (node.name == "old") {
            return visitInternalFunctionCall(node)
        }

        val method = klass.methods.firstOrNull { it.name == node.name && it.parameters.size == node.args.size }
            ?: error("unknown method call: ${node.name}")  // todo

        val args = node.args.map { visit(it) }.toTypedArray()

        val call = instructionFactory.getCall(CallOpcode.VIRTUAL, method, klass, args, isNamed = true)
        contractBlock.add(call)
        return call
    }

    private fun visitInternalFunctionCall(node: FunctionCallNode): Instruction {
        if (node.args.size != 1) throw IllegalArgumentException("expected one argument of old(), got ${node.args.size}")
        val argNode = node.args.first()
        val valueToSave = visit(argNode)

        val castedInstr = valueToSave as? Instruction ?: error("argument of old isn't instruction")
        oldCallStorage.add(castedInstr)

        if (argNode.isInverted) {
            argNode.isInverted = false
            return instructionFactory.getUnary(UnaryOpcode.NEG, castedInstr).also {
                oldCallStorage.add(it)
            }
        }

        return castedInstr
    }

    override fun visitMinusNode(node: MinusNode): Instruction {
        val left = visit(node.left)
        val right = visit(node.right)
        val instr = instructionFactory.getBinary(BinaryOpcode.SUB, left, right)
        contractBlock.add(instr)
        return instr
    }

    override fun visitMulNode(node: MulNode): Instruction {
        val left = visit(node.left)
        val right = visit(node.right)
        val instr = instructionFactory.getBinary(BinaryOpcode.MUL, left, right)
        contractBlock.add(instr)
        return instr
    }

    override fun visitNumberNode(node: NumberNode): Value {
        return valueFactory.getNumber(node.value)
    }

    override fun visitPlusNode(node: PlusNode): Instruction {
        val left = visit(node.left)
        val right = visit(node.right)
        val instr = instructionFactory.getBinary(BinaryOpcode.ADD, left, right)
        contractBlock.add(instr)
        return instr
    }

    override fun visitStringNode(node: StringNode): Value {
        return valueFactory.getString(node.value)
    }

    override fun visitVariableNode(node: VariableNode): Value {
        val field = klass.fields.firstOrNull { it.name == node.name }
        if (field == null) {
            val parameterType = method.argTypes.firstOrNull() ?: error("unknown variable name: ${node.name}")
            return valueFactory.getArgument(0, method, parameterType)
        }

        val `this` = valueFactory.getThis(klass)
        val instr = instructionFactory.getFieldLoad(`this`, field)
        contractBlock.add(instr)
        return instr
    }

    override fun visit(node: ExpressionNode): Value {
        val returnValue = super.visit(node)
        if (returnValue is Instruction && node is TermNode && node.isInverted) {
            val res = instructionFactory.getUnary(UnaryOpcode.NEG, returnValue)

            contractBlock.add(res)
            return res
        }

        return returnValue
    }
}