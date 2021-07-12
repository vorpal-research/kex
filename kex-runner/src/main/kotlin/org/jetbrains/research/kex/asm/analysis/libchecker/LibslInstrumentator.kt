package org.jetbrains.research.kex.asm.analysis.libchecker

import org.jetbrains.research.kex.asm.manager.MethodManager
import org.jetbrains.research.kex.libsl.LibslDescriptor
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.*
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.BoolType
import org.jetbrains.research.kfg.type.IntType
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FieldNode
import ru.spbstu.insys.libsl.parser.Automaton
import ru.spbstu.insys.libsl.parser.FunctionDecl

class LibslInstrumentator(
    override val cm: ClassManager,
    private val librarySpecification: LibslDescriptor
) : MethodVisitor{
    private val prefix = "\$KEX\$INSTRUMENTED\$"
    private val stateFields = mutableMapOf<Class, Field>()
    private var assertIdInc = 0

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
        if (automaton.shifts.isEmpty()) return

        val methodDescription = librarySpecification
            .functionsByAutomaton[automaton]
            ?.firstOrNull { mth -> mth.name == methodName } ?: return

        if (klass !in stateFields.keys) {
            val stateFieldName = prefix + "STATE"

            val fn = FieldNode(Opcodes.ACC_PUBLIC, stateFieldName, "I", null, 0) // I = int
            val stateField = Field(cm, fn, klass)
            klass.cn.fields.add(fn)
            klass.modifyField(stateField, IntType)
            stateFields[klass] = stateField
        }

        addAutomataAssignments(methodDescription, method)

        addShifts(automaton, methodDescription, method, stateFields[klass]!!)

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
            blockSaver.add(InstructionFactory(cm).getJump(entryBlock))

            method.addBefore(entryBlock, blockSaver)
            entryBlock.addPredecessors(blockSaver)
            blockSaver.addSuccessor(entryBlock)

            insertKexAssert(block, lastBlock, prelastBlocks, condition)
        }

        return
    }

    private fun insertKexAssert(block: BasicBlock, nextBlock: BasicBlock, prevBlocks: Collection<BasicBlock>?, condition: Instruction) {
        val kexAssertion = MethodManager.KexIntrinsicManager.kexAssertWithId(cm)
        val conditionsArray = InstructionFactory(cm).getNewArray(BoolType, IntConstant(1, IntType))
        block.add(conditionsArray)
        val initArray = InstructionFactory(cm).getArrayStore(conditionsArray, IntConstant(0, IntType), condition)
        block.add(initArray)

        val kexAssertionArgs = arrayOf(
            StringConstant("id" + (assertIdInc++), TypeFactory(cm).stringType),
            conditionsArray
        )

        val kexAssertionCall = InstructionFactory(cm).getCall(CallOpcode.STATIC, kexAssertion, kexAssertion.klass, kexAssertionArgs, true)
        block.add(kexAssertionCall)

        val gotoNext = InstructionFactory(cm).getJump(nextBlock)
        block.add(gotoNext)

        val gotoCurrentBlock = InstructionFactory(cm).getJump(block)

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
        val `this` = ValueFactory(cm).getThis(method.klass)

        if (shifts.any { it.from.lowercase() == "any" }) {
            val shift = shifts.first()
            val changeToStateConst = librarySpecification.statesMap[automaton to shift.to] ?: error("unknown state: ${shift.from}")
            branchBlock.apply {

                add(InstructionFactory(cm).getFieldStore(`this`, stateField, IntConstant(changeToStateConst, IntType)))
                add(InstructionFactory(cm).getJump(entryBlock))
                method.addBefore(entryBlock, this)

                addSuccessor(entryBlock)
                entryBlock.addPredecessors(this)
            }

            return
        }

        for (shift in shifts) {
            val lhv = InstructionFactory(cm).getFieldLoad(`this`, stateField)
            branchBlock.add(lhv)

            val fromStateCode = librarySpecification.statesMap[automaton to shift.from] ?: error("unknown state $shift")

            val toStateConst = if (shift.to.lowercase() == "self") {
                fromStateCode
            } else {
                librarySpecification.statesMap[automaton to shift.to] ?: error("unknown state $shift")
            }

            val rhv = IntConstant(fromStateCode, IntType)

            val cmpInst = InstructionFactory(cm).getCmp(BoolType, CmpOpcode.EQ, lhv, rhv)
            branchBlock.add(cmpInst)

            val failureBlock = BodyBlock("shift-failure").apply {
                addPredecessors(branchBlock)
                branchBlock.addSuccessor(this)
            }
            method.add(failureBlock)

            val successBlock = BodyBlock("${automaton.name.typeName} success branch").apply {
                val constValue = IntConstant(toStateConst, IntType)
                val assignment = InstructionFactory(cm).getFieldStore(`this`, stateField, constValue)
                add(assignment)

                val goto = InstructionFactory(cm).getJump(entryBlock)
                add(goto)

                addPredecessors(branchBlock)
                branchBlock.addSuccessor(this)

                addSuccessor(entryBlock)
                entryBlock.addPredecessors(this)
            }
            method.add(successBlock)

            val branch = InstructionFactory(cm).getBranch(cmpInst, successBlock, failureBlock)
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
            val conditionsArray = InstructionFactory(cm).getNewArray(BoolType, IntConstant(1, IntType))
            add(conditionsArray)
            val initArray = InstructionFactory(cm).getArrayStore(conditionsArray, IntConstant(0, IntType), BoolConstant(false, BoolType))
            add(initArray)

            val kexAssertionArgs = arrayOf(
                StringConstant("id" + (assertIdInc++), TypeFactory(cm).stringType),
                conditionsArray
            )

            val kexAssertionCall = InstructionFactory(cm).getCall(CallOpcode.STATIC, kexAssertion, kexAssertion.klass, kexAssertionArgs, true)
            add(kexAssertionCall)

            val goto = InstructionFactory(cm).getJump(entryBlock)
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
                val `this` = ValueFactory(cm).getThis(method.klass)
                val store = InstructionFactory(cm).getFieldStore(`this`, stateField, ValueFactory(cm).getInt(initStateCode))
                method.entry.insertBefore(method.entry.first(), store)
                continue
            }

            val field = automataFields.getOrPut(automaton) {
                val stateFieldName = "${prefix}$automatonName"
                val fieldType = TypeFactory(cm).getRefType("${automaton.javaPackage?.name?.plus(".") ?: ""}${automaton.name}".replace(".", "/"))
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
            val assignmentValue = InstructionFactory(cm).getNew(automatonName, targetClass)
            val assignmentInstr = InstructionFactory(cm).getFieldStore(field, assignmentValue)
            method.entry.insertBefore(method.entry.first(), assignmentValue, assignmentInstr)
        }
    }


}