package org.vorpal.research.kex.reanimator.codegen.javagen

import org.vorpal.research.kex.asm.util.Visibility


@Suppress("MemberVisibilityCanBePrivate", "unused")
class JavaBuilder(val pkg: String = "") {
    companion object {
        private fun offset(level: Int) = "    ".repeat(level)

        private val Int.asOffset get() = offset(this)

        fun isEscapeChar(char: Char) = when (char) {
            '\t', '\n', '\b', '\r', '\u000c', '\'', '\"', '\\' -> true
            else -> false
        }

        fun escapeCharIfNeeded(char: Char) = when (char) {
            '\t' -> "\\t"
            '\b' -> "\\b"
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\u000c' -> "\\f"
            '\'' -> "\\'"
            '\"' -> "\\\""
            '\\' -> "\\\\"
            else -> when {
                char.code in 32..126 -> char
                else -> String.format("\\u%04X", char.code)
            }
        }
    }

    private val imports = mutableSetOf<String>()
    private val instances = mutableListOf<JavaCode>()

    override fun toString(): String = buildString {
        if (pkg.isNotBlank()) {
            appendLine("package $pkg;")
            appendLine()
        }
        imports.forEach {
            appendLine("import $it;")
        }
        appendLine()

        instances.forEach {
            appendLine(it.print(0))
            appendLine()
        }
    }

    interface Type
    data class StringType(val name: String) : Type {
        override fun toString() = name
    }

    val void = StringType("void")

    interface JavaCode {
        fun print(level: Int): String
    }

    interface JavaStatement : JavaCode
    private object EmptyStatement : JavaStatement {
        override fun print(level: Int) = ""
    }

    data class StringStatement(val statement: String) : JavaStatement {
        override fun toString() = statement
        override fun print(level: Int): String = "${level.asOffset}$statement;"
    }

    interface ConditionalStatement : JavaStatement
    data class StringConditionStatement(val statement: String) : ConditionalStatement {
        override fun toString() = statement
        override fun print(level: Int): String = statement
    }

    interface ControlStatement : JavaStatement {
        val subStatements: MutableList<JavaStatement>

        fun isEmpty() = subStatements.isEmpty()
        fun isNotEmpty() = !isEmpty()

        operator fun String.unaryPlus() {
            subStatements += StringStatement(this)
        }

        fun aDo(body: DoWhileStatement.() -> Unit): DoWhileStatement {
            val doStatement = DoWhileStatement()
            doStatement.body()
            subStatements += doStatement
            return doStatement
        }

        fun aTry(body: TryCatchStatement.() -> Unit): TryCatchStatement {
            val tryStatement = TryCatchStatement()
            tryStatement.body()
            subStatements += tryStatement
            return tryStatement
        }

        fun anIf(condition: String, body: IfElseStatement.() -> Unit) = anIf(StringConditionStatement(condition), body)

        fun anIf(condition: ConditionalStatement, body: IfElseStatement.() -> Unit): IfElseStatement {
            val ifStatement = IfElseStatement(condition = condition)
            ifStatement.body()
            subStatements += ifStatement
            return ifStatement
        }
    }

    interface ElseBlock : ControlStatement

    data class ElseStatement(
        override val subStatements: MutableList<JavaStatement> = mutableListOf(),
    ) : ElseBlock {
        override fun print(level: Int) = buildString {
            appendLine("${level.asOffset}else {")
            subStatements.forEach {
                appendLine(it.print(level + 1))
            }
            append("${level.asOffset}}")
        }
    }

    data class ElseIfStatement(
        override val subStatements: MutableList<JavaStatement> = mutableListOf(),
        var condition: ConditionalStatement = StringConditionStatement(""),
    ) : ElseBlock {
        override fun print(level: Int) = buildString {
            appendLine("${level.asOffset}else if (${condition.print(0)}) {")
            subStatements.forEach {
                appendLine(it.print(level + 1))
            }
            append("${level.asOffset}}")
        }
    }

    data class IfElseStatement(
        override val subStatements: MutableList<JavaStatement> = mutableListOf(),
        var condition: ConditionalStatement = StringConditionStatement(""),
        val elseBlocks: MutableList<ElseBlock> = mutableListOf()
    ) : ControlStatement {
        override fun print(level: Int) = buildString {
            appendLine("${level.asOffset}if (${condition.print(0)}) {")
            subStatements.forEach {
                appendLine(it.print(level + 1))
            }
            appendLine("${level.asOffset}}")
            elseBlocks.forEach {
                appendLine(it.print(level))
            }
        }

        fun anElse(body: ElseStatement.() -> Unit): IfElseStatement {
            val elseBlock = ElseStatement()
            elseBlock.body()
            elseBlocks += elseBlock
            return this
        }

        fun ifElse(condition: ConditionalStatement, body: ElseIfStatement.() -> Unit): IfElseStatement {
            val elseBlock = ElseIfStatement(condition = condition)
            elseBlock.body()
            elseBlocks += elseBlock
            return this
        }

        fun ifElse(condition: String, body: ElseIfStatement.() -> Unit) = ifElse(StringConditionStatement(condition), body)
    }

    data class DoWhileStatement(
        override val subStatements: MutableList<JavaStatement> = mutableListOf(),
        var condition: ConditionalStatement = StringConditionStatement("")
    ) : ControlStatement {
        override fun print(level: Int) = buildString {
            appendLine("${level.asOffset}do {")
            subStatements.forEach {
                appendLine(it.print(level + 1))
            }
            appendLine("${level.asOffset}} while (${condition.print(0)});")
        }

        fun aWhile(condition: ConditionalStatement) {
            this.condition = condition
        }

        fun aWhile(condition: String) {
            this.condition = StringConditionStatement(condition)
        }
    }

    data class CatchStatement(
        val exceptions: MutableList<Type> = mutableListOf(),
        override val subStatements: MutableList<JavaStatement> = mutableListOf()
    ) : ControlStatement {
        override fun print(level: Int) = buildString {
            appendLine("${level.asOffset}catch (${exceptions.joinToString("|")} e) {")
            subStatements.forEach {
                appendLine(it.print(level + 1))
            }
            append("${level.asOffset}}")
        }
    }

    data class FinallyStatement(
        override val subStatements: MutableList<JavaStatement> = mutableListOf()
    ) : ControlStatement {
        override fun print(level: Int) = buildString {
            appendLine("${level.asOffset}finally {")
            subStatements.forEach {
                appendLine(it.print(level + 1))
            }
            append("${level.asOffset}}")
        }
    }

    data class TryCatchStatement(
        override val subStatements: MutableList<JavaStatement> = mutableListOf(),
        val catchBlocks: MutableList<CatchStatement> = mutableListOf(),
        val finallyBlock: FinallyStatement = FinallyStatement()
    ) : ControlStatement {

        override fun print(level: Int) = buildString {
            appendLine("${level.asOffset} try {")
            subStatements.forEach {
                appendLine(it.print(level + 1))
            }
            appendLine("${level.asOffset}}")
            catchBlocks.forEach {
                appendLine(it.print(level))
            }
            if (finallyBlock.isNotEmpty()) appendLine(finallyBlock.print(level))
        }

        fun catch(body: CatchStatement.() -> Unit): TryCatchStatement {
            val catch = CatchStatement()
            catch.body()
            catchBlocks += catch
            return this
        }

        fun finally(body: FinallyStatement.() -> Unit): TryCatchStatement {
            finallyBlock.body()
            return this
        }

    }

    open class JavaFunction(val name: String, val typeArgs: List<Type> = listOf()) : ControlStatement {
        lateinit var returnType: Type
        var visibility = Visibility.PUBLIC
        val modifiers = mutableListOf<String>()
        val arguments = mutableListOf<JavaArgument>()
        override val subStatements = mutableListOf<JavaStatement>()
        val annotations = mutableListOf<String>()
        val exceptions = mutableListOf<String>()

        open val signature: String
            get() = buildString {
                append(visibility)
                if (modifiers.isNotEmpty()) append(modifiers.joinToString(" ", prefix = " "))
                if (typeArgs.isNotEmpty()) append(typeArgs.joinToString(", ", prefix = " <", postfix = ">"))
                append(" $returnType $name(${arguments.joinToString(", ")})")
            }

        data class JavaArgument(val name: String, val type: Type) {
            override fun toString() = "$type $name"
        }

        fun statement(statement: String) {
            subStatements += StringStatement(statement)
        }

        fun body(body: String) {
            subStatements.addAll(body.split("\n").map { StringStatement(it) })
        }

        override fun toString() = print(0)

        override fun print(level: Int): String = buildString {
            for (anno in annotations) {
                appendLine("${level.asOffset}@$anno")
            }
            append("${level.asOffset}$signature")
            if (exceptions.isNotEmpty()) {
                append(exceptions.joinToString(separator = ", ", prefix = " throws "))
            }
            appendLine(" {")
            val innerLevel = level + 1
            for (statement in subStatements) {
                appendLine(statement.print(innerLevel))
            }
            appendLine("${level.asOffset}}")
        }
    }

    class JavaConstructor(val klass: JavaClass) : JavaFunction(klass.name) {
        override val signature: String
            get() = "$visibility ${klass.name}(${arguments.joinToString(", ")})"
    }

    class JavaStaticInitializer : JavaFunction("static") {
        override val signature: String
            get() = "<clinit>"

        override fun print(level: Int): String = buildString {
            appendLine("${level.asOffset}static {")
            val innerLevel = level + 1
            for (statement in subStatements) {
                appendLine(statement.print(innerLevel))
            }
            appendLine("${level.asOffset}}")
        }
    }

    class JavaMethod(val klass: JavaClass, name: String, typeArgs: List<Type> = listOf()) : JavaFunction(name, typeArgs)

    data class JavaClass(val pkg: String, val name: String) : JavaCode {
        val fields = mutableListOf<JavaField>()
        val constructors = mutableListOf<JavaConstructor>()
        val staticInits = mutableListOf<JavaStaticInitializer>()
        val methods = mutableListOf<JavaMethod>()
        val staticClasses = mutableListOf<JavaClass>()

        data class JavaField(
            val name: String,
            val type: Type,
            var visibility: Visibility = Visibility.PACKAGE,
            var initializer: String? = null,
        ) {
            val annotations = mutableListOf<String>()
            val modifiers = mutableListOf<String>()

            override fun toString() = buildString {
                if (annotations.isNotEmpty()) append(annotations.joinToString(" ", postfix = " ") { "@$it" })
                append(visibility)
                if (modifiers.isNotEmpty()) append(modifiers.joinToString(" ", prefix = " "))
                append(" $type $name${initializer?.let { " = $it" } ?: ""};")
            }
        }

        fun field(name: String, type: Type): JavaField = JavaField(name, type).also { fields += it }

        fun field(name: String, type: Type, body: JavaField.() -> Unit) = JavaField(name, type).also {
            it.body()
            fields += it
        }

        fun constructor(body: JavaFunction.() -> Unit) {
            val funBuilder = JavaConstructor(this)
            funBuilder.body()
            constructors += funBuilder
        }

        fun static(body: JavaFunction.() -> Unit): JavaFunction {
            val funBuilder = JavaStaticInitializer()
            funBuilder.body()
            staticInits += funBuilder
            return funBuilder
        }


        fun method(name: String, body: JavaFunction.() -> Unit): JavaFunction {
            val funBuilder = JavaMethod(this, name)
            funBuilder.body()
            methods += funBuilder
            return funBuilder
        }

        fun staticClass(name: String, body: JavaClass.() -> Unit): JavaClass {
            val classBuilder = JavaClass(pkg, name)
            classBuilder.body()
            staticClasses += classBuilder
            return classBuilder
        }

        fun method(name: String, typeArgs: List<Type>, body: JavaFunction.() -> Unit): JavaFunction {
            val funBuilder = JavaMethod(this, name, typeArgs)
            funBuilder.body()
            methods += funBuilder
            return funBuilder
        }

        fun constructor(typeArgs: List<Type>, body: JavaFunction.() -> Unit) {
            val funBuilder = JavaMethod(this, name, typeArgs)
            funBuilder.body()
            methods += funBuilder
        }

        override fun print(level: Int): String = buildString {
            appendLine("${level.asOffset}public class $name {")
            fields.forEach { appendLine("${(level + 1).asOffset}$it") }
            appendLine()
            staticInits.forEach {
                appendLine(it.print(level + 1))
            }
            constructors.forEach {
                appendLine(it.print(level + 1))
            }
            methods.forEach {
                appendLine(it.print(level + 1))
            }
            staticClasses.forEach {
                // TODO: Mock. Adding "static" here is crutch. Implement modifiers configuration (static, public, etc)
                appendLine("static" + it.print(level + 1))
            }
            appendLine("};")
        }
    }

    fun import(name: String) {
        imports += name
    }

    fun importStatic(name: String) {
        imports += "static $name"
    }

    fun type(name: String): Type = StringType(name)

    fun klass(pkg: String, name: String): JavaClass {
        val newKlass = JavaClass(pkg, name)
        instances += newKlass
        return newKlass
    }

    fun klass(pkg: String, name: String, body: JavaClass.() -> Unit) {
        val newKlass = JavaClass(pkg, name)
        newKlass.body()
        instances += newKlass
    }

    fun arg(name: String, type: Type) = JavaFunction.JavaArgument(name, type)
}
