package org.jetbrains.research.kex.reanimator.codegen.javagen

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.descriptor.*
import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.ktype.KexNull
import org.jetbrains.research.kex.ktype.KexPointer
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.callstack.UnknownCall
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.runIf

class ExecutorCS2JavaPrinter(
    ctx: ExecutionContext,
    packageName: String,
    klassName: String,
    val setupName: String
) : CallStack2JavaPrinter(ctx, packageName, klassName) {
    private val testParams = mutableListOf<JavaBuilder.JavaClass.JavaField>()
    private lateinit var newInstance: JavaBuilder.JavaFunction
    private lateinit var setField: JavaBuilder.JavaFunction
    private lateinit var setElement: JavaBuilder.JavaFunction
    private lateinit var callMethod: JavaBuilder.JavaFunction

    init {
        initReflection()
    }

    private fun initReflection() {
        with(builder) {
            import("java.lang.Class")
            import("java.lang.reflect.Method")
            import("java.lang.reflect.Field")
            import("java.lang.reflect.Array")
            import("sun.misc.Unsafe")

            with(klass) {
                field("UNSAFE", type("Unsafe")) {
                    visibility = Visibility.PRIVATE
                    modifiers += "static"
                    modifiers += "final"
                }

                static {
                    aTry {
                        +"final Field uns = Unsafe.class.getDeclaredField(\"theUnsafe\")"
                        +"uns.setAccessible(true)"
                        +"UNSAFE = (Unsafe) uns.get(null)"
                    }.catch {
                        exceptions += type("Throwable")
                        +"throw new RuntimeException()"
                    }
                }

                method("newInstance") {
                    arguments += arg("klass", type("Class<?>"))
                    returnType = type("Object")
                    visibility = Visibility.PRIVATE
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Object instance = klass.cast(UNSAFE.allocateInstance(klass))"
                    +"return instance"
//                    +"return klass.cast(UNSAFE.allocateInstance(klass))"
                }

                newInstance = method("newInstance") {
                    arguments += arg("klass", type("String"))
                    returnType = type("Object")
                    visibility = Visibility.PRIVATE
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Class<?> reflect = Class.forName(klass)"
                    +"return newInstance(reflect)"
                }

                setField = method("setField") {
                    arguments += arg("instance", type("Object"))
                    arguments += arg("name", type("String"))
                    arguments += arg("value", type("Object"))
                    returnType = void
                    visibility = Visibility.PRIVATE
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Field field = instance.getClass().getDeclaredField(name)"
                    +"field.setAccessible(true)"
                    +"field.set(instance, value)"
                }

                setElement = method("setElement") {
                    arguments += arg("array", type("Object[]"))
                    arguments += arg("index", type("int"))
                    arguments += arg("element", type("Object"))
                    returnType = void
                    visibility = Visibility.PRIVATE
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Array.set(array, index, element)"
                }

                callMethod = method("callMethod") {
                    arguments += arg("instance", type("Object"))
                    arguments += arg("name", type("String"))
                    arguments += arg("argTypes", type("Class<?>[]"))
                    arguments += arg("args", type("Object[]"))
                    returnType = type("Object")
                    visibility = Visibility.PRIVATE
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Method method = instance.getClass().getDeclaredMethod(name, argTypes)"
                    +"method.setAccessible(true)"
                    +"return method.invoke(instance, args)"
                }
            }
        }
    }

    override fun cleanup() {
        super.cleanup()
        testParams.clear()
    }

    override fun printCallStack(
        testName: String,
        method: org.jetbrains.research.kfg.ir.Method,
        callStacks: Parameters<CallStack>
    ) {
        cleanup()

        for (cs in callStacks.asList)
            resolveTypes(cs)

        with(builder) {
            import("org.junit.Before")

            with(klass) {
                runIf(!method.isConstructor) {
                    callStacks.instance?.let {
                        testParams += field(it.name, JavaBuilder.StringType(method.klass.javaString))
                    }
                }
                callStacks.arguments.forEach { arg ->
                    val type = when (val call = arg.first()) {
                        is UnknownCall -> call.type
                        else -> unreachable { log.error("Unexpected call in arg") }
                    }
                    if (testParams.all { it.name != arg.name })
                        testParams += field(arg.name, type(type.javaString))
                }

                current = method(setupName) {
                    returnType = void
                    annotations += "Before"
                    exceptions += "Throwable"
                }
            }
        }

        runIf(!method.isConstructor) {
            callStacks.instance?.printAsJava()
        }
        for (cs in callStacks.asList)
            cs.printAsJava()

        printedStacks.clear()
        with(builder) {
            with(klass) {
                current = method(testName) {
                    returnType = void
                    annotations += "Test"
                    exceptions += "Throwable"
                }
            }
        }

        printTestCall(method, callStacks)
    }

    override fun printVarDeclaration(name: String, type: CSType) = when {
        testParams.any { it.name == name } -> name
        else -> super.printVarDeclaration(name, type)
    }

    override fun printUnknown(owner: CallStack, call: UnknownCall): List<String> {
        val descriptor = call.target
        printedStacks -= "${descriptor.term}"
        val result = mutableListOf<String>()
        printDescriptor(descriptor, result)
        return result
    }

    private fun printTestCall(method: org.jetbrains.research.kfg.ir.Method, callStacks: Parameters<CallStack>) =
        with(current) {
            val args = callStacks.arguments.joinToString(", ") { it.name }
            +when {
                method.isStatic -> "${method.klass.javaString}.${method.name}($args)"
                method.isConstructor -> "new ${CSClass(method.klass.type)}($args)"
                else -> "${callStacks.instance!!.name}.${method.name}($args)"
            }
        }

    protected val ConstantDescriptor.asConstant: String
        get() = when (this) {
            is ConstantDescriptor.Null -> "null"
            is ConstantDescriptor.Bool -> "$value"
            is ConstantDescriptor.Byte -> "(byte) $value"
            is ConstantDescriptor.Char -> when (value) {
                in 'a'..'z' -> "'$value'"
                in 'A'..'Z' -> "'$value'"
                else -> "(char) ${value.code}"
            }
            is ConstantDescriptor.Short -> "(short) $value"
            is ConstantDescriptor.Int -> "$value"
            is ConstantDescriptor.Long -> "${value}L"
            is ConstantDescriptor.Float -> when {
                value.isNaN() -> "Float.NaN".also {
                    builder.import("java.lang.Float")
                }
                value.isInfinite() && value < 0.0 -> "Float.NEGATIVE_INFINITY".also {
                    builder.import("java.lang.Float")
                }
                value.isInfinite() -> "Float.POSITIVE_INFINITY".also {
                    builder.import("java.lang.Float")
                }
                else -> "${value}F"
            }
            is ConstantDescriptor.Double -> when {
                value.isNaN() -> "Double.NaN".also {
                    builder.import("java.lang.Double")
                }
                value.isInfinite() && value < 0.0 -> "Double.NEGATIVE_INFINITY".also {
                    builder.import("java.lang.Double")
                }
                value.isInfinite() -> "Double.POSITIVE_INFINITY".also {
                    builder.import("java.lang.Double")
                }
                else -> "$value"
            }
            else -> unreachable { log.error("Unknown constant descriptor $this") }
        }

    private fun printDescriptor(descriptor: Descriptor, result: MutableList<String>): String = with(current) {
        val name = "${descriptor.term}"
        if (name in printedStacks) return@with name
        printedStacks += name

        val actualType = when (descriptor.type) {
            is KexNull -> ctx.types.objectType.csType
            else -> descriptor.type.getKfgType(ctx.types).csType
        }
        val resolveType = when (descriptor.type) {
            is KexArray -> ctx.types.getArrayType(ctx.types.objectType).csType
            is KexPointer -> ctx.types.objectType.csType
            else -> actualType
        }
        val decl = printVarDeclaration(name, resolveType)
        when (descriptor) {
            ConstantDescriptor.Null -> result += "$decl = null"
            is ConstantDescriptor.Bool -> result += "$decl = ${descriptor.asConstant}"
            is ConstantDescriptor.Byte -> result += "$decl = ${descriptor.asConstant}"
            is ConstantDescriptor.Char -> result += "$decl = ${descriptor.asConstant}"
            is ConstantDescriptor.Double -> result += "$decl = ${descriptor.asConstant}"
            is ConstantDescriptor.Float -> result += "$decl = ${descriptor.asConstant}"
            is ConstantDescriptor.Int -> result += "$decl = ${descriptor.asConstant}"
            is ConstantDescriptor.Long -> result += "$decl = ${descriptor.asConstant}"
            is ConstantDescriptor.Short -> result += "$decl = ${descriptor.asConstant}"
            is ArrayDescriptor -> {
                val (depth, elementType) = resolveType.elementTypeDepth()
                val cast = when {
                    testParams.any { it.name == name } -> " ($actualType)"
                    else -> ""
                }
                result += "$decl =$cast new $elementType[${descriptor.length}]${"[]".repeat(depth)}"
                for ((index, element) in descriptor.elements) {
                    val elementName = printDescriptor(element, result)
                    result += "${setElement.name}($name, $index, $elementName)"
                }
            }
            is ClassDescriptor -> {
                for ((field, element) in descriptor.fields) {
                    val fieldName = printDescriptor(element, result)
                    result += "${setField.name}(null, \"${field.first}\", $fieldName)"
                }
            }
            is ObjectDescriptor -> {
                val klass = (descriptor.type.getKfgType(ctx.types) as ClassType).klass
                val cast = when {
                    testParams.any { it.name == name } -> " ($actualType)"
                    else -> ""
                }
                result += "$decl =$cast ${newInstance.name}(\"${klass.canonicalDesc}\")"
                for ((field, element) in descriptor.fields) {
                    val fieldName = printDescriptor(element, result)
                    result += "${setField.name}($name, \"${field.first}\", $fieldName)"
                }
            }
        }
        return name
    }
}