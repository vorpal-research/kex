package org.jetbrains.research.kex.asm.analysis.libchecker

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.InstructionFactory
import org.jetbrains.research.kfg.visitor.ClassVisitor
import org.jetbrains.research.libsl.asg.*
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FieldNode

class ClassInstrumentator(
    override val cm: ClassManager,
    private val librarySpecification: Library,
    ) : ClassVisitor {
    val syntheticContexts = mutableMapOf<String, SyntheticContext>()

    private val prefix = "\$KEX\$INSTRUMENTED\$"

    override fun visit(klass: Class) {
        val fullName = klass.fullName.replace("/", ".")
        val classSpec = librarySpecification.automata.firstOrNull { it.name == fullName } ?: return

        val classSyntheticContext = SyntheticContext()
        syntheticContexts[klass.fullName] = classSyntheticContext

        var stateIndex = 0
        classSpec.states.forEach { state -> classSyntheticContext.statesMap[state] = ++stateIndex }
        classSpec.variables.forEach { variable ->
            val initValue = variable.initValue.primaryValue
            val field = insertField(prefix + variable.name, variable.type, initValue, klass)
            classSyntheticContext.fields[variable] = field
        }

        if (classSpec.states.isNotEmpty()) {
            val stateFieldName = prefix + "STATE"
            val intType = librarySpecification.semanticTypes.first { it is IntType }
            val defaultState = classSpec.states.firstOrNull { it.kind == StateKind.INIT } ?: error("init state not found")
            val defaultStateConst = classSyntheticContext.statesMap[defaultState] ?: error("unknown state")

            val field = insertField(stateFieldName, intType, defaultStateConst.toPrimaryValue, klass)
            classSyntheticContext.stateField = field
        }
    }

    private fun insertField(name: String, type: Type, value: Value, klass: Class): Field {
        val fn = FieldNode(Opcodes.ACC_PUBLIC, name, type.asmDescriptor, null, null)
        val field = Field(cm, fn, klass)
        klass.cn.fields.add(fn)
        klass.modifyField(field, type.kfgType(cm))

        val constructor = klass.constructors.first()
        val usage = constructor.usageContext
        val storeInstr = InstructionFactory(cm).getFieldStore(usage, field, value)
        val firstBodyBlock = constructor.bodyBlocks.first()
        firstBodyBlock.insertBefore(firstBodyBlock.terminator, storeInstr)

        return field
    }

    override fun cleanup() { }

    private val Expression?.primaryValue: Value
        get() {
            this ?: return ValueFactory(cm).nullConstant
            return when(this) {
                is IntegerLiteral -> ValueFactory(cm).getInt(this.value)
                is FloatLiteral -> ValueFactory(cm).getFloat(this.value)
                is StringLiteral -> ValueFactory(cm).getString(this.value)
                is BoolLiteral -> ValueFactory(cm).getBool(this.value)
                else -> error("only primary values are allowed in initializers")
            }
        }

    private val Any?.toPrimaryValue: Value
        get() {
            this ?: return ValueFactory(cm).nullConstant
            return when(this) {
                is Int -> ValueFactory(cm).getInt(this)
                is Float -> ValueFactory(cm).getFloat(this)
                is String -> ValueFactory(cm).getString(this)
                is Boolean -> ValueFactory(cm).getBool(this)
                else -> error("only primary values are allowed in initializers")
            }
        }
}