package org.jetbrains.research.kex.reanimator.codegen.javagen

import org.jetbrains.research.kex.asm.util.Visibility


class JavaBuilder(val pkg: String = "") {
    companion object {
        private fun offset(level: Int) = "    ".repeat(level)

        private val Int.asOffset get() = offset(this)
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
    data class StringStatement(val statement: String) : JavaStatement {
        override fun toString() = statement
        override fun print(level: Int): String = "${level.asOffset}$statement;"
    }

    interface ControlStatement : JavaStatement
    data class CatchStatement(
        val exceptions: MutableList<Type> = mutableListOf(),
        val catchStatements: MutableList<JavaStatement> = mutableListOf()
    ) : ControlStatement {

        operator fun String.unaryPlus() {
            catchStatements += StringStatement(this)
        }

        override fun print(level: Int) = buildString {
            appendLine("${level.asOffset}catch (${exceptions.joinToString("|")} e) {")
            catchStatements.forEach {
                appendLine(it.print(level + 1))
            }
            appendLine("${level.asOffset}}")
        }
    }

    data class FinallyStatement(
        val statements: MutableList<JavaStatement> = mutableListOf()
    ) : ControlStatement {

        operator fun String.unaryPlus() {
            statements += StringStatement(this)
        }

        override fun print(level: Int) = buildString {
            appendLine("${level.asOffset}finally {")
            statements.forEach {
                appendLine(it.print(level + 1))
            }
            appendLine("${level.asOffset}}")
        }

        fun isEmpty() = statements.isEmpty()
        fun isNotEmpty() = !isEmpty()
    }

    data class TryCatchStatement(
        val tryStatements: MutableList<JavaStatement> = mutableListOf(),
        val catchBlocks: MutableList<CatchStatement> = mutableListOf(),
        val finallyBlock: FinallyStatement = FinallyStatement()
    ) : ControlStatement {

        operator fun String.unaryPlus() {
            tryStatements += StringStatement(this)
        }

        override fun print(level: Int) = buildString {
            appendLine("${level.asOffset} try {")
            tryStatements.forEach {
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

    open class JavaFunction(val name: String, val typeArgs: List<Type> = listOf()) : JavaCode {
        lateinit var returnType: Type
        var visibility = Visibility.PUBLIC
        val modifiers = mutableListOf<String>()
        val arguments = mutableListOf<JavaArgument>()
        val statements = mutableListOf<JavaStatement>()
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

        operator fun String.unaryPlus() {
            statements += StringStatement(this)
        }

        fun statement(statement: String) {
            statements += StringStatement(statement)
        }

        fun body(body: String) {
            statements.addAll(body.split("\n").map { StringStatement(it) })
        }

        fun aTry(body: TryCatchStatement.() -> Unit): TryCatchStatement {
            val tryStatement = TryCatchStatement()
            tryStatement.body()
            statements += tryStatement
            return tryStatement
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
            for (statement in statements) {
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
            for (statement in statements) {
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

        data class JavaField(
            val name: String,
            val type: Type,
            var visibility: Visibility = Visibility.PACKAGE,
            var initializer: String? = null,
        ) {
            val modifiers = mutableListOf<String>()

            override fun toString() = buildString {
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
            appendLine("};")
        }
    }

    fun import(name: String) {
        imports += name
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
