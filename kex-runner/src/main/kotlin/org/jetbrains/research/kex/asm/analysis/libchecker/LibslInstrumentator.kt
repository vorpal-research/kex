package org.jetbrains.research.kex.asm.analysis.libchecker

import org.jetbrains.research.kex.asm.manager.MethodManager
import org.jetbrains.research.kex.libsl.LibslDescriptor
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.*
import org.jetbrains.research.kfg.ir.value.BoolConstant
import org.jetbrains.research.kfg.ir.value.IntConstant
import org.jetbrains.research.kfg.ir.value.StringConstant
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.CallOpcode
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.ReturnInst
import org.jetbrains.research.kfg.type.BoolType
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.IntType
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FieldNode
import ru.spbstu.insys.libsl.parser.Automaton
import ru.spbstu.insys.libsl.parser.FunctionDecl

class LibslInstrumentator(
    override val cm: ClassManager,
    private val librarySpecification: LibslDescriptor,
    private val stateFields: Map<Class, Field>
) : MethodVisitor{
    private val prefix = "\$KEX\$INSTRUMENTED\$"
    private var assertIdInc = 0
    private val instFactory = cm.instruction
    private val valueFactory = cm.value
    private val typeFactory = cm.type

    override fun cleanup() { }

    override fun visit(method: Method) {
        val klass = method.klass
        val classQualifiedName = klass.canonicalDesc
        if (!librarySpecification.automatonByQualifiedName.containsKey(classQualifiedName)) {
            return
        }
        val automaton = librarySpecification.automatonByQualifiedName[classQualifiedName] ?: return
        val methodName = if (method.isConstructor) {
            librarySpecification.functionsByAutomaton[automaton]?.firstOrNull { it.name == automaton.name.typeName }?.name
                ?: error("constructor method not found")
        } else {
            method.name
        }

        val methodDescription = librarySpecification
            .functionsByAutomaton[automaton]
            ?.firstOrNull { mth -> mth.name == methodName } ?: return

        addAutomataAssignments(methodDescription, method)

        if (automaton.states.isNotEmpty()) {
            addShifts(automaton, methodDescription, method, stateFields[klass]!!)
        }

        // requires contract
        val requires = methodDescription.contracts.requires
        if (requires != null) {
            val newEntry = method.entry
            val generator = ContractsGenerator(method, cm)
            val condition = generator.visitConjunctionNode(requires)
            if (generator.oldCallStorage.isNotEmpty()) error("usage of old() now allowed in requires contract")
            val block = generator.contractBlock

            insertKexAssert(block, newEntry, null, condition)
        }

        // ensures contract
        val ensures = methodDescription.contracts.ensures
        if (ensures != null) {
            val lastBlock = method.basicBlocks.last { it.instructions.any { instr -> instr is ReturnInst } }
            val prelastBlocks = lastBlock.predecessors.toSet()
            prelastBlocks.toSet().forEach { prev ->
                prev.remove(prev.terminator)

                lastBlock.removePredecessor(prev)
                prev.removeSuccessor(lastBlock)
            }

            val generator = ContractsGenerator(method, cm)
            val condition = generator.visitConjunctionNode(ensures)
            val block = generator.contractBlock
            val savedValuesInstructions = generator.oldCallStorage

            savedValuesInstructions.forEach { block.remove(it) }

            val blockSaver = BodyBlock("saver")

            val entryBlock = method.entry

            blockSaver.addAll(savedValuesInstructions)
            blockSaver.add(instFactory.getJump(entryBlock))

            method.addBefore(entryBlock, blockSaver)
            entryBlock.addPredecessors(blockSaver)
            blockSaver.addSuccessor(entryBlock)

            insertKexAssert(block, lastBlock, prelastBlocks, condition)
        }

        // insert block that configures state and variables in synthesized automaton
        if (!method.returnType.isVoid && methodDescription.returnValue != null) {
            val descriptorReturnValue = methodDescription.variableAssignments.firstOrNull { it.name == "result" } ?: return
            val foreignAutomatonState = descriptorReturnValue.calleeArguments.first()

            val terminateBlock = method.terminateBlock ?: return
            val returnStatement = terminateBlock.first { it is ReturnInst } as ReturnInst
            val returnValue = returnStatement.returnValue

            val returnType = returnValue.type as? ClassType  ?: error("can't get terminate block")

            val foreignAutomaton = librarySpecification.automatonByQualifiedName[returnType.klass.fullName.replace("/", ".")] ?: return
            val stateConst = librarySpecification.statesMap[foreignAutomaton to foreignAutomatonState]!!

            insertAutomatonFieldStore(terminateBlock, returnStatement, returnValue, prefix + "STATE", stateConst)
        }

        return
    }

    private fun insertAutomatonFieldStore(block: BasicBlock, returnStatement: ReturnInst, returnValue: Value, fieldName: String, value: Int) {
        val foreignClass = cm[returnStatement.returnType.name]
        val foreignStateField = foreignClass.getField(fieldName, typeFactory.intType)

        val storeInstr = instFactory.getFieldStore(
            returnValue,
            foreignStateField,
            valueFactory.getInt(value)
        )

        block.insertBefore(returnStatement, storeInstr)
    }

    private fun insertKexAssert(block: BasicBlock, nextBlock: BasicBlock, prevBlocks: Collection<BasicBlock>?, condition: Instruction) {
        val kexAssertion = MethodManager.KexIntrinsicManager.kexAssertWithId(cm)
        val conditionsArray = instFactory.getNewArray(BoolType, IntConstant(1, IntType))
        block.add(conditionsArray)
        val initArray = instFactory.getArrayStore(conditionsArray, IntConstant(0, IntType), condition)
        block.add(initArray)

        val kexAssertionArgs = arrayOf(
            StringConstant("id" + (assertIdInc++), typeFactory.stringType),
            conditionsArray
        )

        val kexAssertionCall = instFactory.getCall(CallOpcode.STATIC, kexAssertion, kexAssertion.klass, kexAssertionArgs, true)
        block.add(kexAssertionCall)

        val gotoNext = instFactory.getJump(nextBlock)
        block.add(gotoNext)

        val gotoCurrentBlock = instFactory.getJump(block)

        prevBlocks?.forEach { prevBlock ->
            prevBlock.addSuccessor(block)
            block.addPredecessors(prevBlock)

            prevBlock.add(gotoCurrentBlock)
        }

        nextBlock.parent.addBefore(nextBlock, block)
        nextBlock.addPredecessors(block)
        block.addSuccessor(nextBlock)
    }

    private fun addShifts(automaton: Automaton, function: FunctionDecl, method: Method, stateField: Field) {
        var previousBlock: BodyBlock? = null
        val entryBlock = method.entry
        val shifts = automaton.shifts.filter { it.functions.contains(function.name) }

        if (shifts.isEmpty()) return

        var branchBlock = BodyBlock("shifts")
        val `this` = valueFactory.getThis(method.klass)

        if (shifts.any { it.from.lowercase() == "any" }) {
            val shift = shifts.first()
            val changeToStateConst = librarySpecification.statesMap[automaton to shift.to] ?: error("unknown state: ${shift.from}")
            branchBlock.apply {

                add(instFactory.getFieldStore(`this`, stateField, IntConstant(changeToStateConst, IntType)))
                add(instFactory.getJump(entryBlock))
                method.addBefore(entryBlock, this)

                addSuccessor(entryBlock)
                entryBlock.addPredecessors(this)
            }

            return
        }

        for (shift in shifts) {
            val lhv = instFactory.getFieldLoad(`this`, stateField)
            branchBlock.add(lhv)

            val fromStateCode = librarySpecification.statesMap[automaton to shift.from] ?: error("unknown state $shift")

            val toStateConst = if (shift.to.lowercase() == "self") {
                fromStateCode
            } else {
                librarySpecification.statesMap[automaton to shift.to] ?: error("unknown state $shift")
            }

            val rhv = IntConstant(fromStateCode, IntType)

            val cmpInst = instFactory.getCmp(BoolType, CmpOpcode.EQ, lhv, rhv)
            branchBlock.add(cmpInst)

            val failureBlock = BodyBlock("shift-failure").apply {
                addPredecessors(branchBlock)
                branchBlock.addSuccessor(this)
            }
            method.add(failureBlock)

            val successBlock = BodyBlock("${automaton.name.typeName} success branch").apply {
                val constValue = IntConstant(toStateConst, IntType)
                val assignment = instFactory.getFieldStore(`this`, stateField, constValue)
                add(assignment)

                val goto = instFactory.getJump(entryBlock)
                add(goto)

                addPredecessors(branchBlock)
                branchBlock.addSuccessor(this)

                addSuccessor(entryBlock)
                entryBlock.addPredecessors(this)
            }
            method.add(successBlock)

            val branch = instFactory.getBranch(cmpInst, successBlock, failureBlock)
            branchBlock.add(branch)

            if (previousBlock != null) {
                branchBlock.addPredecessors(previousBlock)
                previousBlock.addSuccessor(branchBlock)
                method.addAfter(previousBlock, branchBlock)
            } else {
                method.addBefore(entryBlock, branchBlock)
            }

            previousBlock = branchBlock
            branchBlock = failureBlock
        }

        branchBlock.apply {
            val kexAssertion = MethodManager.KexIntrinsicManager.kexAssertWithId(cm)
            val conditionsArray = instFactory.getNewArray(BoolType, IntConstant(1, IntType))
            add(conditionsArray)
            val initArray = instFactory.getArrayStore(conditionsArray, IntConstant(0, IntType), BoolConstant(false, BoolType))
            add(initArray)

            val kexAssertionArgs = arrayOf(
                StringConstant("id" + (assertIdInc++), typeFactory.stringType),
                conditionsArray
            )

            val kexAssertionCall = instFactory.getCall(CallOpcode.STATIC, kexAssertion, kexAssertion.klass, kexAssertionArgs, true)
            add(kexAssertionCall)

            val goto = instFactory.getJump(entryBlock)
            add(goto)
        }

        branchBlock.addSuccessor(entryBlock)
        entryBlock.addPredecessors(branchBlock)

        method.add(branchBlock)

        return
    }

    private fun addAutomataAssignments(func: FunctionDecl, method: Method) {
        if (func.variableAssignments.isEmpty()) return
        val klass = method.klass

        val automataFields = mutableMapOf<Automaton, Field>()

        for (assignment in func.variableAssignments) {
            val automatonName = assignment.calleeAutomatonName
            val automaton = librarySpecification.library.automata.firstOrNull { it.name.typeName == automatonName }
                ?: error("unknown automaton")

            if (assignment.calleeAutomatonName == klass.name) {
                // this is automaton's constructor
                val initState = assignment.calleeArguments.firstOrNull()
                    ?: error("no init state provided in automaton $automatonName")
                val initStateCode = librarySpecification.statesMap[automaton to initState]
                    ?: error("unknown init state provided in automaton $automatonName")
                val stateField = stateFields[klass] ?: error("state field wasn't initialized")
                val `this` = valueFactory.getThis(method.klass)
                val store = instFactory.getFieldStore(`this`, stateField, valueFactory.getInt(initStateCode))
                method.entry.insertBefore(method.entry.first(), store)
                continue
            }

            val field = automataFields.getOrPut(automaton) {
                val stateFieldName = "${prefix}$automatonName"
                val fieldType = typeFactory.getRefType("${automaton.javaPackage?.name?.plus(".") ?: ""}${automaton.name}".replace(".", "/"))
                val fn = FieldNode(
                    Opcodes.ACC_PUBLIC,
                    stateFieldName,
                    fieldType.asmDesc,
                    null,
                    null
                )
                val stateField = Field(cm, fn, klass)
                klass.cn.fields.add(fn)
                klass.modifyField(stateField, fieldType)
            }
            val targetClass = cm.concreteClasses.firstOrNull { it.name == automatonName } ?: error("unknown class $automatonName")
            val assignmentValue = instFactory.getNew(automatonName, targetClass)
            val assignmentInstr = instFactory.getFieldStore(field, assignmentValue)
            method.entry.insertBefore(method.entry.first(), assignmentValue, assignmentInstr)
        }
    }

    private val Method.terminateBlock: BasicBlock?
        get() = this.basicBlocks.lastOrNull { block -> block.any { inst -> inst is ReturnInst } }

}