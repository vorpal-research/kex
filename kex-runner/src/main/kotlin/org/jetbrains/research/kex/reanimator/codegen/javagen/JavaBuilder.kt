package org.jetbrains.research.kex.reanimator.codegen.javagen


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

    open class JavaFunction(val name: String, val typeArgs: List<Type> = listOf()) : JavaCode {
        lateinit var returnType: Type
        val arguments = mutableListOf<JavaArgument>()
        val statements = mutableListOf<JavaStatement>()
        val annotations = mutableListOf<String>()
        val exceptions = mutableListOf<String>()

        open val signature
            get() = "public ${if (typeArgs.isNotEmpty()) typeArgs.joinToString(", ", prefix = "<", postfix = ">") else ""} " +
                    "$returnType $name(${arguments.joinToString(", ")})"

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
            get() = "${klass.name}(${arguments.joinToString(", ")})"
    }

    class JavaMethod(val klass: JavaClass, name: String, typeArgs: List<Type> = listOf()) : JavaFunction(name, typeArgs)

    data class JavaClass(val pkg: String, val name: String) : JavaCode {
        val fields = mutableListOf<JavaField>()
        val constructors = mutableListOf<JavaConstructor>()
        val methods = mutableListOf<JavaMethod>()

        data class JavaField(val name: String, val type: Type, val initializer: String? = null) {
            override fun toString() = "$type $name ${initializer ?: ""};"
        }

        fun field(name: String, type: Type) {
            fields += JavaField(name, type)
        }

        fun field(name: String, type: Type, initializer: String) {
            fields += JavaField(name, type, initializer)
        }

        fun constructor(body: JavaFunction.() -> Unit) {
            val funBuilder = JavaConstructor(this)
            funBuilder.body()
            constructors += funBuilder
        }

        fun method(name: String, body: JavaFunction.() -> Unit): JavaFunction {
            val funBuilder = JavaMethod(this, name)
            funBuilder.body()
            methods += funBuilder
            return funBuilder
        }

        fun method(name: String, typeArgs: List<Type>, body: JavaFunction.() -> Unit) {
            val funBuilder = JavaMethod(this, name, typeArgs)
            funBuilder.body()
            methods += funBuilder
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

}
