package org.jetbrains.research.kex.reanimator.codegen.javagen

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.reanimator.actionsequence.*
import org.jetbrains.research.kex.util.getMethod
import org.jetbrains.research.kex.util.kapitalize
import org.jetbrains.research.kex.util.loadClass
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.PrimaryType
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.runIf

private val generateSetup by lazy { kexConfig.getBooleanValue("testGen", "generateSetup", false) }

class ExecutorAS2JavaPrinter(
    ctx: ExecutionContext,
    packageName: String,
    klassName: String,
    val setupName: String
) : ActionSequence2JavaPrinter(ctx, packageName, klassName) {
    private val testParams = mutableListOf<JavaBuilder.JavaClass.JavaField>()
    private lateinit var newInstance: JavaBuilder.JavaFunction
    private lateinit var newArray: JavaBuilder.JavaFunction
    private val newPrimitiveArrayMap = mutableMapOf<String, JavaBuilder.JavaFunction>()
    private lateinit var getField: JavaBuilder.JavaFunction
    private lateinit var setField: JavaBuilder.JavaFunction
    private val setPrimitiveFieldMap = mutableMapOf<String, JavaBuilder.JavaFunction>()
    private lateinit var setElement: JavaBuilder.JavaFunction
    private val setPrimitiveElementMap = mutableMapOf<String, JavaBuilder.JavaFunction>()
    private lateinit var callConstructor: JavaBuilder.JavaFunction
    private lateinit var callMethod: JavaBuilder.JavaFunction
    private var klassCounter = 0
    private val printedDeclarations = hashSetOf<String>()
    private val printedInsides = hashSetOf<String>()

    init {
        initReflection()
    }

    private val KexType.primitiveName: String?
        get() = when (this) {
            is KexBool -> "boolean"
            is KexByte -> "byte"
            is KexChar -> "char"
            is KexShort -> "short"
            is KexInt -> "int"
            is KexLong -> "long"
            is KexFloat -> "float"
            is KexDouble -> "double"
            else -> null
        }

    private fun initReflection() {
        with(builder) {
            import("java.lang.Class")
            import("java.lang.reflect.Method")
            import("java.lang.reflect.Constructor")
            import("java.lang.reflect.Field")
            import("java.lang.reflect.Array")
            import("java.lang.reflect.Modifier")
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
                }
                val primitiveTypes = listOf("boolean", "byte", "char", "short", "int", "long", "double", "float")

                newInstance = method("newInstance") {
                    arguments += arg("klass", type("String"))
                    returnType = type("Object")
                    visibility = Visibility.PRIVATE
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Class<?> reflect = Class.forName(klass)"
                    +"return newInstance(reflect)"
                }

                newArray = method("newArray") {
                    arguments += arg("elementType", type("String"))
                    arguments += arg("length", type("int"))
                    returnType = type("Object")
                    visibility = Visibility.PRIVATE
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Class<?> reflect = Class.forName(elementType)"
                    +"return Array.newInstance(reflect, length)"
                }

                for (type in primitiveTypes) {
                    newPrimitiveArrayMap[type] = method("new${type.kapitalize()}Array") {
                        arguments += arg("length", type("int"))
                        returnType = type("Object")
                        visibility = Visibility.PRIVATE
                        modifiers += "static"
                        exceptions += "Throwable"

                        +"return Array.newInstance(${type}.class, length)"
                    }
                }

                getField = method("getField") {
                    arguments += arg("klass", type("Class<?>"))
                    arguments += arg("name", type("String"))
                    returnType = type("Field")
                    visibility = Visibility.PRIVATE
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Field result = null"
                    +"Class<?> current = klass"
                    aDo {
                        aTry {
                            +"result = current.getDeclaredField(name)"
                        }.catch {
                            exceptions += type("Throwable")
                        }
                        +"current = current.getSuperclass()"
                    }.aWhile("current != null")
                    anIf("result == null") {
                        +"throw new NoSuchFieldException()"
                    }
                    +"return result"
                }

                setField = method("setField") {
                    arguments += arg("instance", type("Object"))
                    arguments += arg("klass", type("Class<?>"))
                    arguments += arg("name", type("String"))
                    arguments += arg("value", type("Object"))
                    returnType = void
                    visibility = Visibility.PRIVATE
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Field field = ${getField.name}(klass, name)"
                    +"field.setAccessible(true)"
                    +"field.set(instance, value)"
                }

                for (type in primitiveTypes) {
                    setPrimitiveFieldMap[type] = method("set${type.kapitalize()}Field") {
                        arguments += arg("instance", type("Object"))
                        arguments += arg("klass", type("Class<?>"))
                        arguments += arg("name", type("String"))
                        arguments += arg("value", type(type))
                        returnType = void
                        visibility = Visibility.PRIVATE
                        modifiers += "static"
                        exceptions += "Throwable"

                        +"Field field = ${getField.name}(klass, name)"
                        +"Field mods = Field.class.getDeclaredField(\"modifiers\")"
                        +"mods.setAccessible(true)"
                        +"int modifiers = mods.getInt(field)"
                        +"mods.setInt(field, modifiers & ~Modifier.FINAL)"
                        +"field.setAccessible(true)"
                        +"field.set${type.kapitalize()}(instance, value)"
                    }
                }

                setElement = method("setElement") {
                    arguments += arg("array", type("Object"))
                    arguments += arg("index", type("int"))
                    arguments += arg("element", type("Object"))
                    returnType = void
                    visibility = Visibility.PRIVATE
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Array.set(array, index, element)"
                }
                for (type in primitiveTypes) {
                    setPrimitiveElementMap[type] = method("set${type.kapitalize()}Element") {
                        arguments += arg("array", type("Object"))
                        arguments += arg("index", type("int"))
                        arguments += arg("element", type(type))
                        returnType = void
                        visibility = Visibility.PRIVATE
                        modifiers += "static"
                        exceptions += "Throwable"

                        +"Array.set${type.kapitalize()}(array, index, element)"
                    }
                }

                callConstructor = method("callConstructor") {
                    arguments += arg("klass", type("Class<?>"))
                    arguments += arg("argTypes", type("Class<?>[]"))
                    arguments += arg("args", type("Object[]"))
                    returnType = type("Object")
                    visibility = Visibility.PRIVATE
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Constructor<?> method = klass.getDeclaredConstructor(argTypes)"
                    +"method.setAccessible(true)"
                    +"return method.newInstance(args)"
                }

                callMethod = method("callMethod") {
                    arguments += arg("klass", type("Class<?>"))
                    arguments += arg("name", type("String"))
                    arguments += arg("argTypes", type("Class<?>[]"))
                    arguments += arg("instance", type("Object"))
                    arguments += arg("args", type("Object[]"))
                    returnType = type("Object")
                    visibility = Visibility.PRIVATE
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Method method = klass.getDeclaredMethod(name, argTypes)"
                    +"method.setAccessible(true)"
                    +"return method.invoke(instance, args)"
                }
            }
        }
    }

    override fun cleanup() {
        super.cleanup()
        testParams.clear()
        printedDeclarations.clear()
        printedInsides.clear()
    }

    override fun printActionSequence(
        testName: String,
        method: org.jetbrains.research.kfg.ir.Method,
        actionSequences: Parameters<ActionSequence>
    ) {
        cleanup()

        for (cs in actionSequences.asList)
            resolveTypes(cs)

        with(builder) {
            import("org.junit.Before")

            with(klass) {
                if (generateSetup) {
                    runIf(!method.isConstructor) {
                        actionSequences.instance?.let {
                            if (it !is PrimaryValue<*>)
                                testParams += field(it.name, type("Object"))
                        }
                    }
                    actionSequences.arguments.forEach { arg ->
                        val type = when (arg) {
                            is UnknownSequence -> arg.type
                            is ActionList -> arg.firstNotNullOfOrNull {
                                when (it) {
                                    is DefaultConstructorCall -> it.klass.type
                                    is ConstructorCall -> it.constructor.klass.type
                                    is NewArray -> it.asArray
                                    is ExternalConstructorCall -> it.constructor.returnType
                                    is ExternalMethodCall -> it.method.returnType
                                    is InnerClassConstructorCall -> it.constructor.klass.type
                                    is EnumValueCreation -> it.klass.type
                                    is StaticFieldGetter -> it.field.type
                                    else -> null
                                }
                            } ?: unreachable { log.error("Unexpected call in arg") }
                            is ReflectionList -> arg.firstNotNullOfOrNull {
                                when (it) {
                                    is ReflectionNewInstance -> it.type
                                    is ReflectionNewArray -> it.type
                                    else -> null
                                }
                            } ?: unreachable { log.error("Unexpected call in arg") }
                            is PrimaryValue<*> -> return@forEach
                            else -> unreachable { log.error("Unexpected call in arg") }
                        }
                        val fieldType = type.kexType.primitiveName?.let { type(it) } ?: type("Object")
                        if (testParams.all { it.name != arg.name }) {
                            testParams += field(arg.name, fieldType)
                        }
                    }
                }

                current = if (generateSetup) method(setupName) {
                    returnType = void
                    annotations += "Before"
                    exceptions += "Throwable"
                } else method(testName) {
                    returnType = void
                    annotations += "Test"
                    exceptions += "Throwable"
                }
            }
        }

        runIf(!method.isConstructor) {
            actionSequences.instance?.printAsJava()
        }
        for (cs in actionSequences.asList)
            cs.printAsJava()

        printedStacks.clear()
        if (generateSetup) {
            with(builder) {
                with(klass) {
                    current = method(testName) {
                        returnType = void
                        annotations += "Test"
                        exceptions += "Throwable"
                    }
                }
            }
        }

        printTestCall(method, actionSequences)
    }

    override fun printVarDeclaration(name: String, type: ASType) = when {
        testParams.any { it.name == name } -> name
        else -> super.printVarDeclaration(name, type)
    }

    val Type.klassType: String
        get() = when (this) {
            is PrimaryType -> "${kexType.primitiveName}.class"
            is ClassType -> "Class.forName(\"${klass.canonicalDesc}\")"
            is ArrayType -> "Array.newInstance(${component.klassType}, 0).getClass()"
            else -> unreachable { }
        }

    private fun printTestCall(
        method: org.jetbrains.research.kfg.ir.Method,
        actionSequences: Parameters<ActionSequence>
    ) =
        with(current) {
            +"Class<?> klass = Class.forName(\"${method.klass.canonicalDesc}\")"
            +"Class<?>[] argTypes = new Class<?>[${method.argTypes.size}]"
            for ((index, type) in method.argTypes.withIndex()) {
                +"argTypes[$index] = ${type.klassType}"
            }
            +"Object[] args = new Object[${method.argTypes.size}]"
            for ((index, arg) in actionSequences.arguments.withIndex()) {
                +"args[$index] = ${arg.name}"
            }
            +when {
                method.isConstructor -> "${callConstructor.name}(klass, argTypes, args)"
                else -> "${callMethod.name}(klass, \"${method.name}\", argTypes, ${actionSequences.instance?.name}, args)"
            }
        }

    override fun printConstructorCall(owner: ActionSequence, call: ConstructorCall): List<String> {
        call.args.forEach { it.printAsJava() }
        val args = call.args.joinToString(", ") {
            val prefix = if (it !is PrimaryValue<*>) "(${resolvedTypes[it]}) " else ""
            prefix + it.stackName
        }
        val actualType = ASClass(call.constructor.klass.type)
        return listOf(
            if (resolvedTypes[owner] != null) {
                val rest = resolvedTypes[owner]!!
                val type = actualType.merge(rest)
                actualTypes[owner] = type
                "${printVarDeclaration(owner.name, type)} = new $type($args)"
            } else {
                actualTypes[owner] = actualType
                "${printVarDeclaration(owner.name, actualType)} = new $actualType($args)"
            }
        )
    }

    override fun printMethodCall(owner: ActionSequence, call: MethodCall): List<String> {
        call.args.forEach { it.printAsJava() }
        val method = call.method
        val args = call.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
        return listOf("((${actualTypes[owner]}) ${owner.name}).${method.name}($args)")
    }

    override fun printExternalConstructorCall(owner: ActionSequence, call: ExternalConstructorCall): List<String> {
        call.args.forEach { it.printAsJava() }
        val constructor = call.constructor
        val args = call.args.withIndex().joinToString(", ") { (index, arg) ->
            "(${constructor.argTypes[index].javaString}) ${arg.stackName}"
        }

        val reflection = ctx.loader.loadClass(call.constructor.klass)
        val ctor = reflection.getMethod(call.constructor, ctx.loader)
        val actualType = ctor.genericReturnType.asType
        return listOf(
            if (resolvedTypes[owner] != null) {
                val rest = resolvedTypes[owner]!!
                val type = actualType.merge(rest)
                actualTypes[owner] = type
                "${
                    printVarDeclaration(
                        owner.name,
                        type
                    )
                } = ${type.cast(rest)} ${constructor.klass.javaString}.${constructor.name}($args)"
            } else {
                actualTypes[owner] = actualType
                "${
                    printVarDeclaration(
                        owner.name,
                        actualType
                    )
                } = ${constructor.klass.javaString}.${constructor.name}($args)"
            }
        )
    }

    override fun printExternalMethodCall(owner: ActionSequence, call: ExternalMethodCall): List<String> {
        call.instance.printAsJava()
        call.args.forEach { it.printAsJava() }
        val method = call.method
        val instance = "((${method.klass.javaString}) ${call.instance.stackName})"
        val args = call.args.withIndex().joinToString(", ") { (index, arg) ->
            "(${method.argTypes[index].javaString}) ${arg.stackName}"
        }

        val reflection = ctx.loader.loadClass(call.method.klass)
        val ctor = reflection.getMethod(call.method, ctx.loader)
        val actualType = ctor.genericReturnType.asType
        return listOf(
            if (resolvedTypes[owner] != null) {
                val rest = resolvedTypes[owner]!!
                val type = actualType.merge(rest)
                actualTypes[owner] = type
                "${
                    printVarDeclaration(
                        owner.name,
                        type
                    )
                } = ${type.cast(rest)} $instance.${method.name}($args)"
            } else {
                actualTypes[owner] = actualType
                "${
                    printVarDeclaration(
                        owner.name,
                        actualType
                    )
                } = $instance.${method.name}($args)"
            }
        )
    }

    override fun printReflectionList(reflectionList: ReflectionList): List<String> {
        val res = mutableListOf<String>()
        printDeclarations(reflectionList, res)
        printInsides(reflectionList, res)
        return res
    }

    fun printDeclarations(
        owner: ActionSequence,
        result: MutableList<String>
    ) {
        if (owner is ReflectionList) {
            if (owner.name in printedDeclarations) return
            printedDeclarations += owner.name

            for (api in owner) {
                when (api) {
                    is ReflectionNewInstance -> result += printReflectionNewInstance(owner, api)
                    is ReflectionNewArray -> result += printReflectionNewArray(owner, api)
                    is ReflectionSetField -> printDeclarations(api.value, result)
                    is ReflectionArrayWrite -> printDeclarations(api.value, result)
                }
            }
        } else {
            owner.printAsJava()
        }
    }

    fun printInsides(
        owner: ActionSequence,
        result: MutableList<String>
    ) {
        if (owner is ReflectionList) {
            if (owner.name in printedInsides) return
            printedInsides += owner.name

            for (api in owner) {
                when (api) {
                    is ReflectionSetField -> {
                        printInsides(api.value, result)
                        result += printReflectionSetField(owner, api)
                    }
                    is ReflectionArrayWrite -> {
                        printInsides(api.value, result)
                        result += printReflectionArrayWrite(owner, api)
                    }
                    else -> {}
                }
            }
        } else {
            owner.printAsJava()
        }
    }

    override fun printReflectionNewInstance(owner: ActionSequence, call: ReflectionNewInstance): List<String> {
        val actualType = ASClass(ctx.types.objectType)
        val kfgClass = (call.type as ClassType).klass
        return listOf(
            if (resolvedTypes[owner] != null) {
                val rest = resolvedTypes[owner]!!
                val type = actualType.merge(rest)
                actualTypes[owner] = type
                "${
                    printVarDeclaration(
                        owner.name,
                        type
                    )
                } = ${newInstance.name}(Class.forName(\"${kfgClass.canonicalDesc}\"))"
            } else {
                actualTypes[owner] = actualType
                "${
                    printVarDeclaration(
                        owner.name,
                        actualType
                    )
                } = ${newInstance.name}(Class.forName(\"${kfgClass.canonicalDesc}\"))"
            }
        )
    }

    override fun printReflectionNewArray(owner: ActionSequence, call: ReflectionNewArray): List<String> {
        val elementType = call.asArray.component
        val actualType = when {
            elementType.isPrimary -> call.asArray.asType
            else -> ASArray(ASClass(ctx.types.objectType))
        }
        actualTypes[owner] = actualType
        val newArrayCall = elementType.kexType.primitiveName?.let {
            "${newPrimitiveArrayMap[it]!!.name}(${call.length})"
        } ?: "${newArray.name}(\"${(elementType as ClassType).klass.canonicalDesc}\", ${call.length})"
        return listOf(
            "${printVarDeclaration(owner.name, actualType)} = ($actualType) $newArrayCall"
        )
    }

    override fun printReflectionSetField(owner: ActionSequence, call: ReflectionSetField): List<String> {
        val setFieldMethod = call.field.type.kexType.primitiveName?.let { setPrimitiveFieldMap[it]!! } ?: setField
        return listOf("${setFieldMethod.name}(${owner.name}, ${owner.name}.getClass(), \"${call.field.name}\", ${call.value.stackName})")
    }

    override fun printReflectionArrayWrite(owner: ActionSequence, call: ReflectionArrayWrite): List<String> {
        val elementType = call.elementType
        val setElementMethod = elementType.kexType.primitiveName?.let { setPrimitiveElementMap[it]!! } ?: setElement
        return listOf("${setElementMethod.name}(${owner.name}, ${call.index.stackName}, ${call.value.stackName})")
    }
}
