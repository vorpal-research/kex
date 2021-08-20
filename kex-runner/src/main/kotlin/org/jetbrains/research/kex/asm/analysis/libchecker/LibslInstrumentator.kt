package org.jetbrains.research.kex.asm.analysis.libchecker

import org.jetbrains.research.kex.asm.manager.MethodManager
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
import org.jetbrains.research.kfg.type.IntType
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.libsl.asg.*
import org.jetbrains.research.libsl.asg.Function
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FieldNode

class LibslInstrumentator(
    override val cm: ClassManager,
    private val librarySpecification: Library,
    private val libraryContext: LslContext,
    private val syntheticContexts: Map<String, SyntheticContext>
) : MethodVisitor {
    private val prefix = "\$KEX\$INSTRUMENTED\$"
    private var assertIdInc = 0
    private val instFactory = cm.instruction
    private val valueFactory = cm.value
    private val typeFactory = cm.type


    override fun cleanup() { }

    override fun visit(method: Method) {
        val klass = method.klass
        val classQualifiedName = klass.canonicalDesc

        val automaton = librarySpecification.automata.firstOrNull { it.name == classQualifiedName } ?: return
        val methodDescription = automaton.functions.firstOrNull { mth -> mth.name == method.name } ?: return
        val classSyntheticContext = syntheticContexts[klass.fullName] ?: error("synthetic context not found")

        addAutomataAssignments(methodDescription, method, classSyntheticContext)

        if (automaton.states.isNotEmpty()) {
            addShifts(automaton, methodDescription, method, classSyntheticContext.stateField, classSyntheticContext.statesMap)
        }

        // requires contract
        methodDescription.contracts.filter { it.kind == ContractKind.REQUIRES }.forEach { requires ->
            val newEntry = method.entry
            val generator = ExpressionGenerator(method, klass, cm, classSyntheticContext)
            val condition = generator.visit(requires.expression)
            if (generator.oldCallStorage.isNotEmpty()) {
                unreachable<Unit> { log.error("usage of old() now allowed in requires contract") }
            }
            val block = generator.expressionBlock
            val name = requires.name ?: "assertion"

            insertKexAssert(name, block, newEntry, null, condition)
        }

        // ensures contract
        methodDescription.contracts.filter { it.kind == ContractKind.REQUIRES }.forEach { ensures ->
            val lastBlock = method.basicBlocks.last { it.instructions.any { instr -> instr is ReturnInst } }
            val prelastBlocks = lastBlock.predecessors.toSet()
            prelastBlocks.toSet().forEach { prev ->
                prev.remove(prev.terminator)

                lastBlock.removePredecessor(prev)
                prev.removeSuccessor(lastBlock)
            }

            val generator = ExpressionGenerator(method, klass, cm, classSyntheticContext)
            val condition = generator.visit(ensures.expression)
            val block = generator.expressionBlock
            val savedValuesInstructions = generator.oldCallStorage

            savedValuesInstructions.forEach { block.remove(it as Instruction) }

            val blockSaver = BodyBlock("saver")

            val entryBlock = method.entry

            blockSaver.addAll(savedValuesInstructions.filterIsInstance<Instruction>())
            blockSaver.add(instFactory.getJump(entryBlock))

            method.addBefore(entryBlock, blockSaver)
            entryBlock.addPredecessors(blockSaver)
            blockSaver.addSuccessor(entryBlock)

            val name = ensures.name ?: "assertion"

            insertKexAssert(name, block, lastBlock, prelastBlocks, condition)
        }

        // insert block that configures state and variables in synthesized automaton
        if (!method.returnType.isVoid && methodDescription.returnType != null) {
            val returnVariable = methodDescription.resultVariable
                ?: error("result variable not specified in function ${methodDescription.qualifiedName}")
            val returnAutomatonCall = methodDescription
                .statements
                .filterIsInstance<Assignment>()
                .firstOrNull { (it.left as? VariableAccess)?.variable == returnVariable }
                ?.value as? CallAutomatonConstructor
                ?: error("result statement not specified")

            val terminateBlock = method.terminateBlock ?: return
            val returnStatement = terminateBlock.first { it is ReturnInst } as ReturnInst
            val returnValue = returnStatement.returnValue

            val state = returnAutomatonCall.state
            val stateConst = classSyntheticContext.statesMap[state] ?: error("state constant wasn't initialized")

            insertAutomatonFieldStore(terminateBlock, returnStatement, returnValue, prefix + "STATE", stateConst)
        }

        if (klass.name.contains("Computer")) {
            println()
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

    private fun insertKexAssert(name: String, block: BasicBlock, nextBlock: BasicBlock, prevBlocks: Collection<BasicBlock>?, condition: Value) {
        val kexAssertion = MethodManager.KexIntrinsicManager.kexAssertWithId(cm)
        val conditionsArray = instFactory.getNewArray(BoolType, IntConstant(1, IntType))
        block.add(conditionsArray)
        val initArray = instFactory.getArrayStore(conditionsArray, IntConstant(0, IntType), condition)
        block.add(initArray)

        val kexAssertionArgs = arrayOf(
            StringConstant(name + (assertIdInc++), typeFactory.stringType),
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

    private fun addShifts(automaton: Automaton, function: Function, method: Method, stateField: Field, statesMap: Map<State, Int>) {
        var previousBlock: BodyBlock? = null
        val entryBlock = method.entry
        val shifts = automaton.shifts.filter { it.functions.contains(function) }

        if (shifts.isEmpty()) return

        var branchBlock = BodyBlock("shifts")
        val `this` = valueFactory.getThis(method.klass)

        if (shifts.any { it.from.isAny }) {
            val shift = shifts.first()
            val changeToStateConst = statesMap[shift.to] ?: error("state ${shift.to.name} constant wasn't initialized")
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

            val fromStateCode = statesMap[shift.from] ?: error("state ${shift.from.name} constant wasn't initialized")

            val toStateConst = if (shift.to.isSelf) {
                fromStateCode
            } else {
                statesMap[shift.from] ?: error("state ${shift.from.name} constant wasn't initialized")
            }

            val rhv = IntConstant(fromStateCode, IntType)

            val cmpInst = instFactory.getCmp(BoolType, CmpOpcode.EQ, lhv, rhv)
            branchBlock.add(cmpInst)

            val failureBlock = BodyBlock("shift-failure").apply {
                addPredecessors(branchBlock)
                branchBlock.addSuccessor(this)
            }
            method.add(failureBlock)

            val successBlock = BodyBlock("${automaton.name} success branch").apply {
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

    private fun addAutomataAssignments(func: Function, method: Method, syntheticContext: SyntheticContext) {
        if (func.statements.filterIsInstance<Assignment>().isEmpty()) return
        val klass = method.klass

        val automataFields = mutableMapOf<Automaton, Field>()

        for (assignment in func.statements.filterIsInstance<Assignment>()) {
            val automatonCall = (assignment.value as? CallAutomatonConstructor) ?: continue
            val automaton = automatonCall.automaton
            val automatonName = automaton.name

            if (automaton.name == klass.name) {
                // this is automaton's constructor
                val initState = automatonCall.state
                val initStateCode = syntheticContext.statesMap[initState]
                    ?: unreachable { log.error("unknown init state provided in automaton $automatonName") }
                val stateField = syntheticContext.stateField
                val `this` = valueFactory.getThis(method.klass)
                val store = instFactory.getFieldStore(`this`, stateField, valueFactory.getInt(initStateCode))
                method.entry.insertBefore(method.entry.first(), store)
                continue
            }

            val field = automataFields.getOrPut(automaton) {
                val stateFieldName = "${prefix}$automatonName"
                val fieldType = typeFactory.getRefType(automaton.name.replace(".", "/"))
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
            val targetClass = cm.concreteClasses.firstOrNull { it.name == automatonName }
                ?: unreachable { log.error("unknown class $automatonName") }
            val assignmentValue = instFactory.getNew(automatonName, targetClass)
            val assignmentInstr = instFactory.getFieldStore(field, assignmentValue)
            method.entry.insertBefore(method.entry.first(), assignmentValue, assignmentInstr)
        }
    }

    private val Method.terminateBlock: BasicBlock?
        get() = this.basicBlocks.lastOrNull { block -> block.any { inst -> inst is ReturnInst } }

}