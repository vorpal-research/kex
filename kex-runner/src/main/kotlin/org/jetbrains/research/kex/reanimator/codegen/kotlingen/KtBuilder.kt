package org.jetbrains.research.kex.reanimator.codegen.kotlingen

import com.abdullin.kthelper.assert.ktassert

class KtBuilder {
    companion object {
        private fun offset(level: Int) = "    ".repeat(level)

        private val Int.asOffset get() = offset(this)
    }

    private val imports = mutableSetOf<String>()
    private val instances = mutableListOf<KtCode>()

    override fun toString(): String = buildString {
        imports.forEach {
            appendLine("import $it")
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
    val unit = StringType("Unit")

    interface KtCode {
        fun print(level: Int): String
    }

    interface KtStatement : KtCode
    data class StringStatement(val statement: String) : KtStatement {
        override fun toString() = statement
        override fun print(level: Int): String = "${level.asOffset}$statement"
    }
    interface ControlStatement : KtStatement

    open class KtFunction(val name: String) : KtCode {
        lateinit var returnType: Type
        val arguments = mutableListOf<KtArgument>()
        val statements = mutableListOf<KtStatement>()
        open val signature get() = "fun $name(${arguments.joinToString(", ")}): $returnType"

        data class KtArgument(val name: String, val type: Type)

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
            appendLine("${level.asOffset}$signature {")
            val innerLevel = level + 1
            for (statement in statements) {
                appendLine("${innerLevel.asOffset}$statement")
            }
            appendLine("${level.asOffset}}")
        }
    }

    class KtConstructor(val klass: KtClass) : KtFunction("constuctor") {
        override val signature: String
            get() = "constructor(${arguments.joinToString(", ")})"
    }
    class KtMethod(val klass: KtClass, name: String) : KtFunction(name)

    class KtExtension(val type: Type, name: String) : KtFunction(name) {
        override val signature: String
            get() = "fun $type.$name(${arguments.joinToString(", ")}): $returnType"
    }

    data class KtClass(val pkg: String, val name: String) : KtCode {
        val properties = mutableListOf<KtProperty>()
        val constructors = mutableListOf<KtConstructor>()
        val functions = mutableListOf<KtMethod>()

        data class KtProperty(val name: String, val type: Type?, val initializer: String? = null) {
            init {
                ktassert(type != null || initializer != null)
            }
        }

        fun property(name: String, type: Type) {
            properties += KtProperty(name, type)
        }

        fun property(name: String, initializer: String) {
            properties += KtProperty(name, null, initializer)
        }

        fun property(name: String, type: Type, initializer: String) {
            properties += KtProperty(name, type, initializer)
        }

        fun constructor(body: KtFunction.() -> Unit) {
            val funBuilder = KtConstructor(this)
            funBuilder.body()
            constructors += funBuilder
        }

        fun method(name: String, body: KtFunction.() -> Unit) {
            val funBuilder = KtMethod(this, name)
            funBuilder.body()
            functions += funBuilder
        }

        override fun print(level: Int): String = buildString {
            appendLine("${level.asOffset}class $name {")
            properties.forEach { appendLine("${(level + 1).asOffset}$it") }
        }
    }

    fun import(name: String) {
        imports += name
    }

    fun type(name: String): Type = StringType(name)

    fun function(name: String, body: KtFunction.() -> Unit) {
        val funBuilder = KtFunction(name)
        funBuilder.body()
        instances += funBuilder
    }

    fun klass(pkg: String, name: String, body: KtClass.() -> Unit) {
        val newKlass = KtClass(pkg, name)
        newKlass.body()
        instances += newKlass
    }

    fun extension(type: String, name: String, body: KtFunction.() -> Unit) {
        val extBuilder = KtExtension(type(type), name)
        extBuilder.body()
        instances += extBuilder
    }
}