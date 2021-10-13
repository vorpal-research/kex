package org.jetbrains.research.kex.asm.analysis.libchecker

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.usageContext
import org.jetbrains.research.kfg.visitor.ClassVisitor
import org.jetbrains.research.libsl.asg.*

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
        val field = klass.addField(name, type.kfgType(cm))

        val constructor = klass.constructors.first()
        val usage = constructor.usageContext
        val `this` = cm.value.getThis(klass)
        val storeInstr = cm.instruction.getFieldStore(usage, `this`, field, value)
        val firstBodyBlock = constructor.bodyBlocks.first()
        firstBodyBlock.insertBefore(firstBodyBlock.terminator, storeInstr)

        return field
    }

    override fun cleanup() { }

    private val Expression?.primaryValue: Value
        get() {
            this ?: return cm.value.nullConstant
            return when(this) {
                is IntegerLiteral -> cm.value.getInt(this.value)
                is FloatLiteral -> cm.value.getFloat(this.value)
                is StringLiteral -> cm.value.getString(this.value)
                is BoolLiteral -> cm.value.getBool(this.value)
                else -> error("only primary values are allowed in initializers")
            }
        }

    private val Any?.toPrimaryValue: Value
        get() {
            this ?: return cm.value.nullConstant
            return when(this) {
                is Int -> cm.value.getInt(this)
                is Float -> cm.value.getFloat(this)
                is String -> cm.value.getString(this)
                is Boolean -> cm.value.getBool(this)
                else -> error("only primary values are allowed in initializers")
            }
        }
}