package org.jetbrains.research.kex.asm.analysis.libchecker

import org.jetbrains.research.kex.asm.manager.MethodManager
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.*
import org.jetbrains.research.kfg.ir.value.*
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
    private lateinit var methodUsageContext: UsageContext
    private lateinit var classSyntheticContext: SyntheticContext


    override fun cleanup() { }

    override fun visit(method: Method) {
        val klass = method.klass
        val classQualifiedName = klass.canonicalDesc

        val automaton = librarySpecification.automata.firstOrNull { it.name == classQualifiedName } ?: return
        val methodDescription = automaton.functions.firstOrNull { mth -> mth.name == method.name } ?: return
        classSyntheticContext = syntheticContexts[klass.fullName] ?: error("synthetic context not found")
        methodUsageContext = method.usageContext

        addAutomataAssignments(methodDescription, method, classSyntheticContext)

        if (automaton.states.isNotEmpty()) {
            addShifts(automaton, methodDescription, method, classSyntheticContext.stateField, classSyntheticContext.statesMap)
        }

        // requires contract
        methodDescription.contracts.filter { it.kind == ContractKind.REQUIRES }.forEach { requires ->
            val newEntry = method.entry
            val generator = ExpressionGenerator(method, klass, cm, syntheticContexts)
            val condition = generator.visit(requires.expression)
            if (generator.oldCallStorage.isNotEmpty()) {
                unreachable<Unit> { log.error("usage of old() now allowed in requires contract") }
            }
            val block = generator.expressionBlock
            val name = requires.name ?: "assertion"

            insertKexAssert(name, block, newEntry, null, condition)
        }

        // ensures contract
        methodDescription.contracts.filter { it.kind == ContractKind.ENSURES }.forEach { ensures ->
            val lastBlock = method.basicBlocks.last { it.instructions.any { instr -> instr is ReturnInst } }
            val prelastBlocks = lastBlock.predecessors.toSet()
            prelastBlocks.toSet().forEach { prev ->
                prev.remove(prev.terminator)

                lastBlock.removePredecessor(methodUsageContext, prev)
                prev.removeSuccessor(methodUsageContext, lastBlock)
            }

            val generator = ExpressionGenerator(method, klass, cm, syntheticContexts)
            val condition = generator.visit(ensures.expression)
            val block = generator.expressionBlock
            val savedValuesInstructions = generator.oldCallStorage

            savedValuesInstructions.forEach { block.remove(it as Instruction) }

            val blockSaver = BodyBlock("saver")

            val entryBlock = method.entry

            blockSaver.addAll(savedValuesInstructions.filterIsInstance<Instruction>())
            blockSaver.add(instFactory.getJump(methodUsageContext, entryBlock))

            method.addBefore(methodUsageContext, entryBlock, blockSaver)
            entryBlock.addPredecessors(methodUsageContext, blockSaver)
            blockSaver.addSuccessor(methodUsageContext, entryBlock)

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

        return
    }

    private fun insertAutomatonFieldStore(block: BasicBlock, returnStatement: ReturnInst, returnValue: Value, fieldName: String, value: Int) {
        val foreignClass = cm[returnStatement.returnType.name]
        val foreignStateField = foreignClass.getField(fieldName, typeFactory.intType)
        val `this` = valueFactory.getThis(foreignClass)
        // todo: is it OK?

        val storeInstr = instFactory.getFieldStore(
            block.parent.usageContext,
            returnValue,
            foreignStateField,
            valueFactory.getInt(value)
        )

        block.insertBefore(returnStatement, storeInstr)
    }

    private fun insertKexAssert(name: String, block: BasicBlock, nextBlock: BasicBlock, prevBlocks: Collection<BasicBlock>?, condition: Value) {
        val kexAssertion = MethodManager.KexIntrinsicManager.kexAssertWithId(cm)
        val conditionsArray = instFactory.getNewArray(
            methodUsageContext,
            BoolType,
            IntConstant(1, IntType)
        )
        block.add(conditionsArray)
        val initArray = instFactory.getArrayStore(methodUsageContext, conditionsArray, IntConstant(0, IntType), condition)
        block.add(initArray)

        val kexAssertionArgs = arrayOf(
            StringConstant(name + (assertIdInc++), typeFactory.stringType),
            conditionsArray
        )

        val kexAssertionCall = instFactory.getCall(methodUsageContext, CallOpcode.STATIC, kexAssertion, kexAssertion.klass, kexAssertionArgs, isNamed=false)
        block.add(kexAssertionCall)

        val gotoNext = instFactory.getJump(methodUsageContext, nextBlock)
        block.add(gotoNext)

        val gotoCurrentBlock = instFactory.getJump(methodUsageContext, block)

        prevBlocks?.forEach { prevBlock ->
            prevBlock.addSuccessor(methodUsageContext, block)
            block.addPredecessors(methodUsageContext, prevBlock)

            prevBlock.add(gotoCurrentBlock)
        }

        nextBlock.parent.addBefore(methodUsageContext, nextBlock, block)
        nextBlock.addPredecessors(methodUsageContext, block)
        block.addSuccessor(methodUsageContext, nextBlock)
    }

    private fun addShifts(automaton: Automaton, function: Function, method: Method, stateField: Field, statesMap: Map<State, Int>) {
        var previousBlock: BodyBlock? = null
        val entryBlock = method.entry
        val shifts = automaton.shifts.filter { it.functions.contains(function) }
        val `this` = valueFactory.getThis(method.klass)

        if (shifts.isEmpty()) return

        var branchBlock = BodyBlock("shifts")

        if (shifts.any { it.from.isAny }) {
            val shift = shifts.first()
            val changeToStateConst = statesMap[shift.to] ?: error("state ${shift.to.name} constant wasn't initialized")
            branchBlock.apply {
                add(instFactory.getFieldStore(methodUsageContext, `this`, stateField, IntConstant(changeToStateConst, IntType)))
                add(instFactory.getJump(methodUsageContext, entryBlock))
                method.addBefore(methodUsageContext, entryBlock, this)

                addSuccessor(methodUsageContext, entryBlock)
                entryBlock.addPredecessors(methodUsageContext, this)
            }

            return
        }

        for (shift in shifts) {
            val lhv = instFactory.getFieldLoad(methodUsageContext, `this`, stateField)
            branchBlock.add(lhv)

            val fromStateCode = statesMap[shift.from] ?: error("state ${shift.from.name} constant wasn't initialized")

            val toStateConst = if (shift.to.isSelf) {
                fromStateCode
            } else {
                statesMap[shift.to] ?: error("state ${shift.from.name} constant wasn't initialized")
            }

            val rhv = IntConstant(fromStateCode, IntType)

            val cmpInst = instFactory.getCmp(methodUsageContext, BoolType, CmpOpcode.EQ, lhv, rhv)
            branchBlock.add(cmpInst)

            val failureBlock = BodyBlock("shift-failure").apply {
                addPredecessors(methodUsageContext, branchBlock)
                branchBlock.addSuccessor(methodUsageContext, this)
            }
            method.add(methodUsageContext, failureBlock)

            val successBlock = BodyBlock("${automaton.name} success branch").apply {
                val constValue = IntConstant(toStateConst, IntType)
                val assignment = instFactory.getFieldStore(methodUsageContext, `this`, stateField, constValue)
                add(assignment)

                val goto = instFactory.getJump(methodUsageContext, entryBlock)
                add(goto)

                addPredecessors(methodUsageContext, branchBlock)
                branchBlock.addSuccessor(methodUsageContext, this)

                addSuccessor(methodUsageContext, entryBlock)
                entryBlock.addPredecessors(methodUsageContext, this)
            }
            method.add(methodUsageContext, successBlock)

            val branch = instFactory.getBranch(methodUsageContext, cmpInst, successBlock, failureBlock)
            branchBlock.add(branch)

            if (previousBlock != null) {
                branchBlock.addPredecessors(methodUsageContext, previousBlock)
                previousBlock.addSuccessor(methodUsageContext, branchBlock)
                method.addAfter(methodUsageContext, previousBlock, branchBlock)
            } else {
                method.addBefore(methodUsageContext, entryBlock, branchBlock)
            }

            previousBlock = branchBlock
            branchBlock = failureBlock
        }

        branchBlock.apply {
            val kexAssertion = MethodManager.KexIntrinsicManager.kexAssertWithId(cm)
            val conditionsArray = instFactory.getNewArray(methodUsageContext, BoolType, IntConstant(1, IntType))
            add(conditionsArray)
            val initArray = instFactory.getArrayStore(methodUsageContext, conditionsArray, IntConstant(0, IntType), BoolConstant(false, BoolType))
            add(initArray)

            val kexAssertionArgs = arrayOf(
                StringConstant("id" + (assertIdInc++), typeFactory.stringType),
                conditionsArray
            )

            val kexAssertionCall = instFactory.getCall(methodUsageContext, CallOpcode.STATIC, kexAssertion, kexAssertion.klass, kexAssertionArgs, false)
            add(kexAssertionCall)

            val goto = instFactory.getJump(methodUsageContext, entryBlock)
            add(goto)
        }

        branchBlock.addSuccessor(methodUsageContext, entryBlock)
        entryBlock.addPredecessors(methodUsageContext, branchBlock)

        method.add(methodUsageContext, branchBlock)

        return
    }

    private fun addAutomataAssignments(func: Function, method: Method, syntheticContext: SyntheticContext) {
        if (func.statements.filterIsInstance<Assignment>().isEmpty()) return
        val klass = method.klass
        val `this` = valueFactory.getThis(klass)

        val automataFields = mutableMapOf<Automaton, Field>()

        for (assignment in func.statements.filterIsInstance<Assignment>()) {
            val variable = (assignment.left as? VariableAccess)?.variable ?: error("unresolved variable ${assignment.left}")
            val variableField = syntheticContext.fields[variable] ?: error("unresolved variable ${variable.name}")
            val (field, value) = when (val value = assignment.value) {
                is CallAutomatonConstructor -> {
                    val automaton = value.automaton
                    val automatonName = automaton.name
                    val targetClass = cm.concreteClasses.firstOrNull { it.name == automatonName }
                        ?: unreachable { log.error("unknown class $automatonName") }

                    val field = syntheticContexts[automatonName]?.stateField
                        ?: error("unknown state field for automaton $automatonName")
                    field to instFactory.getNew(methodUsageContext, automatonName, targetClass).also {
                        method.entry.insertBefore(method.entry.first(), it)
                    }
                }
                is BoolLiteral -> {
                    variableField to valueFactory.getBool(value.value)
                }
                is IntegerLiteral -> {
                    variableField to valueFactory.getInt(value.value)
                }
                is FloatLiteral -> {
                    variableField to valueFactory.getFloat(value.value)
                }
                is StringLiteral -> {
                    variableField to valueFactory.getString(value.value)
                }
                else -> error("unresolved literal type ${value::class.java}")
            }

            val assignmentInstr = instFactory.getFieldStore(methodUsageContext, `this`, field, value)
            method.entry.insertBefore(method.entry.first(), assignmentInstr)
        }
    }

    private val Method.terminateBlock: BasicBlock?
        get() = this.basicBlocks.lastOrNull { block -> block.any { inst -> inst is ReturnInst } }
}
