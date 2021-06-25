package org.jetbrains.research.kex.reanimator.codegen.javagen

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.reanimator.callstack.*
import org.jetbrains.research.kthelper.runIf

class ExecutorCS2JavaPrinter(
    ctx: ExecutionContext,
    packageName: String,
    klassName: String,
    val setupName: String
) : CallStack2JavaPrinter(ctx, packageName, klassName) {
    private val testParams = mutableListOf<JavaBuilder.JavaClass.JavaField>()

    private fun buildApiCall(
        method: org.jetbrains.research.kfg.ir.Method, callStacks: Parameters<CallStack>
    ): CallStack = when {
        method.isStatic -> StaticMethodCall(method, callStacks.arguments).wrap("static${staticCounter++}")
        method.isConstructor -> callStacks.instance!!
        else -> MethodCall(method, callStacks.arguments).wrap(callStacks.instance!!.name)
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
                callStacks.arguments.withIndex().forEach { (index, arg) ->
                    val type = method.argTypes[index]
                    runIf(!type.isPrimary) {
                        testParams += field(arg.name, JavaBuilder.StringType(method.argTypes[index].javaString))
                    }
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

        val testCall = buildApiCall(method, callStacks)
        testCall.printAsJava()
    }

    override fun CallStack.printAsJava() {
        if (name in printedStacks) return
        if (this is PrimaryValue<*>) {
            asConstant
            return
        }
        printedStacks += name
        for (call in this) {
            with(current) {
                +printApiCall(this@printAsJava, call)
            }
        }
    }

    override fun printVarDeclaration(name: String, type: CSType) = when {
        testParams.any { it.name == name } -> name
        else -> super.printVarDeclaration(name, type)
    }
}