package org.jetbrains.research.kex.reanimator.codegen.javagen

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.descriptor.*
import org.jetbrains.research.kex.ktype.KexNull
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
    private lateinit var callMethod: JavaBuilder.JavaFunction

    init {
        initReflection()
    }

    private fun initReflection() {
        with(builder) {
            import("java.lang.Class")
            import("java.lang.reflect.Method")
            import("java.lang.reflect.Field")
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
                    +"System.out.println(instance)"
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

    override fun printUnknown(owner: CallStack, call: UnknownCall): List<String> = with(current) {
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

    private fun printDescriptor(descriptor: Descriptor, result: MutableList<String>): String = with(current) {
        val name = "${descriptor.term}"
        if (name in printedStacks) return@with name
        printedStacks += name

        val actualType = when {
            descriptor.type is KexNull -> ctx.types.objectType.csType
            else -> descriptor.type.getKfgType(ctx.types).csType
        }
        val decl = printVarDeclaration(name, actualType)
        when (descriptor) {
            ConstantDescriptor.Null -> result += "$decl = null"
            is ConstantDescriptor.Bool -> result += "$decl = ${descriptor.value}"
            is ConstantDescriptor.Byte -> result += "$decl = ${descriptor.value}"
            is ConstantDescriptor.Char -> result += "$decl = ${descriptor.value}"
            is ConstantDescriptor.Double -> result += "$decl = ${descriptor.value}"
            is ConstantDescriptor.Float -> result += "$decl = ${descriptor.value}"
            is ConstantDescriptor.Int -> result += "$decl = ${descriptor.value}"
            is ConstantDescriptor.Long -> result += "$decl = ${descriptor.value}"
            is ConstantDescriptor.Short -> result += "$decl = ${descriptor.value}"
            is ArrayDescriptor -> {
                val (depth, elementType) = actualType.elementTypeDepth()
                result += "$decl = new $elementType[${descriptor.length}]${"[]".repeat(depth)}"
                for ((index, element) in descriptor.elements) {
                    val elementName = printDescriptor(element, result)
                    result += "$name[$index] = $elementName"
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
                result += "$decl = ($actualType) ${newInstance.name}(\"${klass.canonicalDesc}\")"
                for ((field, element) in descriptor.fields) {
                    val fieldName = printDescriptor(element, result)
                    result += "${setField.name}($name, \"${field.first}\", $fieldName)"
                }
            }
        }
        return name
    }
}