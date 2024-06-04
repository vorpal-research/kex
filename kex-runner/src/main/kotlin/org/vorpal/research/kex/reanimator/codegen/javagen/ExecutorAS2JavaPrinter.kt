package org.vorpal.research.kex.reanimator.codegen.javagen

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.ktype.KexByte
import org.vorpal.research.kex.ktype.KexChar
import org.vorpal.research.kex.ktype.KexDouble
import org.vorpal.research.kex.ktype.KexFloat
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.KexLong
import org.vorpal.research.kex.ktype.KexShort
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.parameters.FinalParameters
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.parameters.hasReturnValueOrFalse
import org.vorpal.research.kex.parameters.isExceptionOrFalse
import org.vorpal.research.kex.parameters.isSuccessOrFalse
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.ConstructorCall
import org.vorpal.research.kex.reanimator.actionsequence.DefaultConstructorCall
import org.vorpal.research.kex.reanimator.actionsequence.EnumValueCreation
import org.vorpal.research.kex.reanimator.actionsequence.ExternalConstructorCall
import org.vorpal.research.kex.reanimator.actionsequence.ExternalMethodCall
import org.vorpal.research.kex.reanimator.actionsequence.InnerClassConstructorCall
import org.vorpal.research.kex.reanimator.actionsequence.MethodCall
import org.vorpal.research.kex.reanimator.actionsequence.MockList
import org.vorpal.research.kex.reanimator.actionsequence.MockNewInstance
import org.vorpal.research.kex.reanimator.actionsequence.MockSetField
import org.vorpal.research.kex.reanimator.actionsequence.MockSetupMethod
import org.vorpal.research.kex.reanimator.actionsequence.NewArray
import org.vorpal.research.kex.reanimator.actionsequence.PrimaryValue
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionArrayWrite
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionCall
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionGetField
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionGetStaticField
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionList
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionNewArray
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionNewInstance
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionSetField
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionSetStaticField
import org.vorpal.research.kex.reanimator.actionsequence.StaticFieldGetter
import org.vorpal.research.kex.reanimator.actionsequence.StringValue
import org.vorpal.research.kex.reanimator.actionsequence.UnknownSequence
import org.vorpal.research.kex.util.javaString
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.type.ArrayType
import org.vorpal.research.kfg.type.BoolType
import org.vorpal.research.kfg.type.ByteType
import org.vorpal.research.kfg.type.CharType
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.DoubleType
import org.vorpal.research.kfg.type.FloatType
import org.vorpal.research.kfg.type.IntType
import org.vorpal.research.kfg.type.LongType
import org.vorpal.research.kfg.type.PrimitiveType
import org.vorpal.research.kfg.type.ShortType
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.objectType
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.error
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.runIf


class ExecutorAS2JavaPrinter(
    ctx: ExecutionContext,
    packageName: String,
    klassName: String,
    private val setupName: String
) : ActionSequence2JavaPrinter(ctx, packageName, klassName) {
    private val surroundInTryCatch =
        kexConfig.getBooleanValue("testGen", "surroundInTryCatch", true)
    private val testParams = mutableListOf<JavaBuilder.JavaClass.JavaField>()
    private val equalityUtils = EqualityUtilsPrinter.equalityUtils(packageName)
    private val reflectionUtils = ReflectionUtilsPrinter.reflectionUtils(packageName)

    @Suppress("unused")
    private val mockUtils by lazy {
        MockUtilsPrinter.mockUtils(packageName)
            .also { builder.importStatic("$packageName.MockUtils.*") }
    }
    private val printedDeclarations = hashSetOf<String>()
    private val printedInsides = hashSetOf<String>()

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

    override fun cleanup() {
        super.cleanup()
        testParams.clear()
        printedDeclarations.clear()
        printedInsides.clear()
    }

    override fun printActionSequence(
        testName: String,
        method: Method,
        parameters: Parameters<ActionSequence>,
        finalParameters: FinalParameters<ActionSequence>?
    ) {
        cleanup()

        for (cs in parameters.asList)
            resolveTypes(cs)

        for (cs in finalParameters?.asList.orEmpty())
            resolveTypes(cs)

        with(builder) {
            import("org.junit.Before")
            import("java.lang.Class")
            import("java.lang.reflect.Method")
            import("java.lang.reflect.Constructor")
            import("java.lang.reflect.Field")
            import("java.lang.reflect.Array")

            import("java.util.Arrays")
            import("java.util.stream.Stream")
            import("java.util.stream.Collectors")

            finalParameters?.exceptionTypeUnsafe?.let {
                import(it)
            }
            importStatic("${reflectionUtils.klass.pkg}.${reflectionUtils.klass.name}.*")
            importStatic("org.junit.Assert.assertTrue")
            if (finalParameters.isSuccessOrFalse) {
                importStatic("${equalityUtils.klass.pkg}.${equalityUtils.klass.name}.*")
            }

            with(klass) {
                if (generateSetup) {
                    runIf(!method.isConstructor) {
                        parameters.instance?.let {
                            if (!it.isConstantValue)
                                testParams += field(it.name, type("Object"))
                        }
                    }
                    val terms = parameters.arguments.toMutableSet()
                    if (finalParameters.isSuccessOrFalse) {
                        terms += finalParameters!!.asList
                    }

                    terms.forEach { sequence ->
                        val type = when (sequence) {
                            is UnknownSequence -> sequence.type
                            is ActionList -> sequence.firstNotNullOfOrNull {
                                when (it) {
                                    is DefaultConstructorCall -> it.klass.asType
                                    is ConstructorCall -> it.constructor.klass.asType
                                    is NewArray -> it.asArray
                                    is ExternalConstructorCall -> it.constructor.returnType
                                    is ExternalMethodCall -> it.method.returnType
                                    is InnerClassConstructorCall -> it.constructor.klass.asType
                                    is EnumValueCreation -> it.klass.asType
                                    is StaticFieldGetter -> it.field.type
                                    else -> null
                                }
                            } ?: unreachable { log.error("Unexpected call in action list: {}", sequence) }

                            is ReflectionList -> sequence.firstNotNullOfOrNull {
                                when (it) {
                                    is ReflectionNewInstance -> it.type
                                    is ReflectionNewArray -> it.type
                                    is ReflectionGetField -> it.field.type
                                    is ReflectionGetStaticField -> it.field.type
                                    else -> null
                                }
                            } ?: unreachable { log.error("Unexpected call in reflection list {}", sequence) }

                            is MockList -> sequence.mockCalls.firstNotNullOfOrNull {
                                when (it) {
                                    is MockNewInstance -> it.klass.asType
                                    else -> null
                                }
                            } ?: unreachable { log.error { "Unexpected call in mock list" } }

                            is PrimaryValue<*> -> return@forEach
                            is StringValue -> return@forEach
                            else -> unreachable { log.error("Unexpected call in arg {}", sequence) }
                        }
                        val fieldType = type.kexType.primitiveName?.let { type(it) } ?: type("Object")
                        if (testParams.all { it.name != sequence.name }) {
                            testParams += field(sequence.name, fieldType)
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
                    if (finalParameters == null) exceptions += "Throwable"
                }
            }
        }

        with(current) {
            if (surroundInTryCatch) statement("try {")
            runIf(!method.isConstructor) {
                parameters.instance?.printAsJava()
            }
            for (cs in parameters.asList)
                cs.printAsJava()

            if (finalParameters.isSuccessOrFalse) {
                finalParameters!!.asList.forEach {
                    it.printAsJava()
                }
            }

            val catchExceptionType = when {
                finalParameters.isExceptionOrFalse -> finalParameters!!.exceptionType.substringAfterLast('.').also {
                    builder.import(finalParameters.exceptionType)
                }
                else -> "Throwable"
            }
            if (surroundInTryCatch) statement("} catch ($catchExceptionType e) {}")
        }

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

        with(current) {
            exceptions += "Throwable"
            printTestCall(method, parameters, finalParameters, this)
        }
    }

    override fun printVarDeclaration(name: String, type: ASType) = when {
        testParams.any { it.name == name } -> name
        else -> super.printVarDeclaration(name, type)
    }

    @Suppress("RecursivePropertyAccessor")
    private val Type.klassType: String
        get() = when (this) {
            is PrimitiveType -> "${kexType.primitiveName}.class"
            is ClassType -> "Class.forName(\"${klass.canonicalDesc}\")"
            is ArrayType -> "Array.newInstance(${component.klassType}, 0).getClass()"
            else -> unreachable { }
        }

    private fun printTestCall(
        method: Method,
        parameters: Parameters<ActionSequence>,
        finalParameters: FinalParameters<ActionSequence>?,
        methodBuilder: JavaBuilder.ControlStatement
    ) = with(methodBuilder) {
        +"Class<?> klass = Class.forName(\"${method.klass.canonicalDesc}\")"
        +"Class<?>[] argTypes = new Class<?>[${method.argTypes.size}]"
        for ((index, type) in method.argTypes.withIndex()) {
            +"argTypes[$index] = ${type.klassType}"
        }
        +"Object[] args = new Object[${method.argTypes.size}]"
        for ((index, arg) in parameters.arguments.withIndex()) {
            +"args[$index] = ${arg.stackName}"
        }

        val methodInvocation = when {
            method.isConstructor -> buildString {
                append("Object instance = ")
                append(reflectionUtils.callConstructor.name)
                append("(klass, argTypes, args)")
            }

            finalParameters.hasReturnValueOrFalse -> buildString {
                append("Object retValue = ")
                append(reflectionUtils.callMethod.name)
                append("(klass, \"")
                append(method.name)
                append("\", argTypes, ")
                append(parameters.instance?.name)
                append(", args)")
            }

            else -> buildString {
                append(reflectionUtils.callMethod.name)
                append("(klass, \"")
                append(method.name)
                append("\", argTypes, ")
                append(parameters.instance?.name)
                append(", args)")
            }
        }
        if (finalParameters.isExceptionOrFalse) {
            aTry {
                +methodInvocation
                +"assertTrue(false)"
            }.catch {
                val exceptionType = finalParameters!!.exceptionType.javaString
                builder.import(exceptionType)
                exceptions += JavaBuilder.StringType(exceptionType.substringAfterLast('.'))
            }
        } else {
            +methodInvocation
        }
        if (finalParameters.isSuccessOrFalse) {
            finalParameters!!.instance?.let { instanceInfo ->
                val instanceName = if (method.isConstructor) "instance" else parameters.instance?.stackName
                if (!instanceInfo.isConstantValue && instanceName != null) {
                    +buildAssert(instanceInfo.javaClass, instanceName, instanceInfo.stackName)
                }
            }
            for ((index, arg) in parameters.arguments.withIndex()) {
                if (!arg.isConstantValue) {
                    +buildAssert(arg.javaClass, arg.stackName, finalParameters.args[index].stackName)
                }
            }
            finalParameters.returnValueUnsafe?.let {
                +buildAssert(it.javaClass, "retValue", it.stackName)
            }
        }
    }

    private fun buildAssert(
        klass: Class<*>,
        lhv: String,
        rhv: String,
    ): String = when {
        isEqualsOverridden(klass) -> "assertTrue(${lhv}.equals(${rhv}))"
        else -> "assertTrue(${equalityUtils.recursiveEquals.name}(${lhv}, ${rhv}))"
    }

    private fun isEqualsOverridden(klass: Class<*>): Boolean {
        val method = klass.getMethod("equals", Object::class.java)
        return method.declaringClass != Object::class.java
    }

    override fun printConstructorCall(owner: ActionSequence, call: ConstructorCall): List<String> {
        call.args.forEach { it.printAsJava() }
        val args = call.args.joinToString(", ") {
            val prefix = if (!it.isConstantValue) "(${resolvedTypes[it]}) " else ""
            prefix + it.stackName
        }
        val actualType = ASClass(call.constructor.klass.asType)
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

    override fun printExternalConstructorCall(
        owner: ActionSequence,
        call: ExternalConstructorCall
    ): List<String> = printConstructorCommon(owner, call) { args ->
        val constructor = call.constructor
        args.withIndex().joinToString(", ") { (index, arg) ->
            "(${constructor.argTypes[index].javaString}) ${arg.stackName}"
        }
    }

    override fun printExternalMethodCall(
        owner: ActionSequence,
        call: ExternalMethodCall
    ): List<String> = printExternalMethodCallCommon(
        owner,
        call,
        { instance -> "((${call.method.klass.javaString}) ${instance.stackName})" },
        { args ->
            args.withIndex().joinToString(", ") { (index, arg) ->
                "(${call.method.argTypes[index].javaString}) ${arg.stackName}"
            }
        }
    )

    override fun printReflectionList(reflectionList: ReflectionList): List<String> {
        val res = mutableListOf<String>()
        printDeclarations(reflectionList, res)
        printInsides(reflectionList, res)
        return res
    }

    override fun printMockList(mockSequence: MockList): List<String> {
        val res = mutableListOf<String>()
        printDeclarations(mockSequence, res)
        printInsides(mockSequence, res)
        return res
    }

    private fun printDeclarations(owner: ActionSequence, result: MutableList<String>) {
        when (owner) {
            is ReflectionList -> printReflectionListDeclarations(owner, result)
            is MockList -> printMockSequenceDeclarations(owner, result)
            else -> {
                owner.printAsJava()
            }
        }
    }

    private fun printMockSequenceDeclarations(owner: MockList, result: MutableList<String>) {
        if (owner.name in printedDeclarations) return
        printedDeclarations += owner.name

        for (mockCall in owner.mockCalls) {
            when (mockCall) {
                is MockNewInstance -> result += printMockNewInstance(owner, mockCall)
                is MockSetupMethod -> mockCall.returnValues.forEach {
                    printDeclarations(it, result)
                }

                is MockSetField -> printDeclarations(mockCall.value, result)
            }
        }
    }

    private fun printReflectionListDeclarations(
        owner: ReflectionList,
        result: MutableList<String>
    ) {
        if (owner.name in printedDeclarations) return
        printedDeclarations += owner.name

        for (api in owner) {
            printReflectionCallDeclarations(api, result, owner)
        }
    }

    private fun printReflectionCallDeclarations(
        api: ReflectionCall,
        result: MutableList<String>,
        owner: ActionSequence
    ) {
        when (api) {
            is ReflectionNewInstance -> result += printReflectionNewInstance(owner, api)
            is ReflectionNewArray -> result += printReflectionNewArray(owner, api)
            is ReflectionGetField -> result += printReflectionGetField(owner, api)
            is ReflectionGetStaticField -> result += printReflectionGetStaticField(owner, api)
            is ReflectionSetField -> printDeclarations(api.value, result)
            is ReflectionSetStaticField -> printDeclarations(api.value, result)
            is ReflectionArrayWrite -> printDeclarations(api.value, result)
        }
    }


    private fun printInsides(owner: ActionSequence, result: MutableList<String>): Unit =
        when (owner) {
            is ReflectionList -> printReflectionListInsides(owner, result)
            is MockList -> printMockSequenceInsides(owner, result)
            else -> {
                owner.printAsJava()
            }
        }

    private fun printMockSequenceInsides(
        owner: MockList,
        result: MutableList<String>
    ) {
        if (owner.name in printedInsides) return
        printedInsides += owner.name

        for (mockCall in owner.mockCalls) {
            when (mockCall) {
                is MockSetupMethod -> {
                    mockCall.returnValues.forEach { printInsides(it, result) }
                    result += printMockSetupMethod(owner, mockCall)
                }

                is MockNewInstance -> {}
                is MockSetField -> {
                    printInsides(mockCall.value, result)
                    result += printMockSetField(owner, mockCall)
                }
            }
        }
    }

    private fun printReflectionListInsides(
        owner: ReflectionList,
        result: MutableList<String>
    ) {
        if (owner.name in printedInsides) return
        printedInsides += owner.name

        for (api in owner) {
            printReflectionCallInsides(api, result, owner)
        }
    }

    private fun printReflectionCallInsides(
        api: ReflectionCall,
        result: MutableList<String>,
        owner: ActionSequence
    ) {
        when (api) {
            is ReflectionSetField -> {
                printInsides(api.value, result)
                result += printReflectionSetField(owner, api)
            }

            is ReflectionSetStaticField -> {
                printInsides(api.value, result)
                result += printReflectionSetStaticField(owner, api)
            }

            is ReflectionArrayWrite -> {
                printInsides(api.value, result)
                result += printReflectionArrayWrite(owner, api)
            }

            else -> {}
        }
    }

    override fun printReflectionNewInstance(
        owner: ActionSequence,
        call: ReflectionNewInstance
    ): List<String> {
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
                } = ${reflectionUtils.newInstance.name}(Class.forName(\"${kfgClass.canonicalDesc}\"))"
            } else {
                actualTypes[owner] = actualType
                "${
                    printVarDeclaration(
                        owner.name,
                        actualType
                    )
                } = ${reflectionUtils.newInstance.name}(Class.forName(\"${kfgClass.canonicalDesc}\"))"
            }
        )
    }

    private fun getClass(type: Type): String = when {
        type.isPrimitive -> "${type.kexType.primitiveName}.class"
        type is ClassType -> "Class.forName(\"${type.klass.canonicalDesc}\")"
        type is ArrayType -> "Array.newInstance(${getClass(type.component)}, 0).getClass()"
        else -> "Class.forName(\"java.lang.Object\")"
    }

    override fun printReflectionNewArray(
        owner: ActionSequence,
        call: ReflectionNewArray
    ): List<String> {
        val elementType = call.asArray.component
        val (newArrayCall, actualType) = when {
            elementType.isPrimitive -> {
                "${reflectionUtils.newPrimitiveArrayMap[elementType.kexType.primitiveName]!!.name}(${call.length.stackName})" to call.asArray.asType
            }

            elementType is ClassType -> {
                "${reflectionUtils.newArray.name}(\"${elementType.klass.canonicalDesc}\", ${call.length.stackName})" to ASArray(
                    ASClass(
                        ctx.types.objectType
                    )
                )
            }

            else -> {
                val base = run {
                    var current = elementType
                    while (current is ArrayType) {
                        current = current.component
                    }
                    current
                }
                when {
                    base.isPrimitive -> {
                        "${reflectionUtils.newArray.name}(\"${elementType.canonicalDesc}\", ${call.length.stackName})" to ASArray(
                            ASClass(ctx.types.objectType)
                        )
                    }

                    else -> {
                        "${reflectionUtils.newObjectArray.name}(${getClass(elementType)}, ${call.length.stackName})" to ASArray(
                            ASClass(
                                ctx.types.objectType
                            )
                        )
                    }
                }
            }
        }
        actualTypes[owner] = actualType
        return listOf(
            "${printVarDeclaration(owner.name, actualType)} = ($actualType) $newArrayCall"
        )
    }

    override fun printReflectionSetField(
        owner: ActionSequence,
        call: ReflectionSetField
    ): List<String> {
        val setFieldMethod = call.field.type.kexType.primitiveName?.let { reflectionUtils.setPrimitiveFieldMap[it]!! }
            ?: reflectionUtils.setField
        return listOf("${setFieldMethod.name}(${owner.name}, ${owner.name}.getClass(), \"${call.field.name}\", ${call.value.stackName})")
    }

    override fun printReflectionSetStaticField(
        owner: ActionSequence,
        call: ReflectionSetStaticField
    ): List<String> {
        val setFieldMethod = call.field.type.kexType.primitiveName?.let { reflectionUtils.setPrimitiveFieldMap[it]!! }
            ?: reflectionUtils.setField
        return listOf("${setFieldMethod.name}(null, Class.forName(\"${call.field.klass.canonicalDesc}\"), \"${call.field.name}\", ${call.value.stackName})")
    }

    override fun printReflectionGetStaticField(
        owner: ActionSequence,
        call: ReflectionGetStaticField
    ): List<String> {
        val actualType = when (call.field.type) {
            is PrimitiveType -> call.field.type.asType
            else -> ASClass(ctx.types.objectType)
        }
        actualTypes[owner] = actualType
        val getFieldMethod = call.field.type.kexType.primitiveName?.let { reflectionUtils.getPrimitiveFieldMap[it]!! }
            ?: reflectionUtils.getField
        return listOf(
            "${
                printVarDeclaration(
                    owner.name,
                    actualType
                )
            } = ${getFieldMethod.name}(null, ${call.field.klass.asType.klassType}, \"${call.field.name}\")"
        )
    }

    override fun printReflectionGetField(owner: ActionSequence, call: ReflectionGetField): List<String> {
        val actualType = when (call.field.type) {
            is PrimitiveType -> call.field.type.asType
            else -> ASClass(ctx.types.objectType)
        }
        actualTypes[owner] = actualType
        val getFieldMethod = call.field.type.kexType.primitiveName?.let { reflectionUtils.getPrimitiveFieldMap[it]!! }
            ?: reflectionUtils.getField
        return listOf(
            "${
                printVarDeclaration(
                    owner.name,
                    actualType
                )
            } = ${getFieldMethod.name}(${call.owner.name}, ${call.field.klass.asType.klassType}, \"${call.field.name}\")"
        )
    }

    override fun printReflectionArrayWrite(
        owner: ActionSequence,
        call: ReflectionArrayWrite
    ): List<String> {
        val elementType = call.elementType
        val setElementMethod =
            elementType.kexType.primitiveName?.let { reflectionUtils.setPrimitiveElementMap[it]!! }
                ?: reflectionUtils.setElement
        return listOf("${setElementMethod.name}(${owner.name}, ${call.index.stackName}, ${call.value.stackName})")
    }

    override fun printMockNewInstance(owner: ActionSequence, call: MockNewInstance): List<String> {
        builder.import("org.mockito.Mockito")
        val actualType = ASClass(ctx.types.objectType)
        val kfgClass = call.klass
        val extraInterfaces = if (call.extraInterfaces.isEmpty()) "" else call.extraInterfaces.joinToString(
            prefix = ", Mockito.withSettings().extraInterfaces(",
            postfix = ")",
            separator = ", "
        ) {
            "Class.forName(\"${it.canonicalDesc}\")"
        }
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
                } = Mockito.mock(Class.forName(\"${kfgClass.canonicalDesc}\")$extraInterfaces)"
            } else {
                actualTypes[owner] = actualType
                "${
                    printVarDeclaration(
                        owner.name,
                        actualType
                    )
                } = Mockito.mock(Class.forName(\"${kfgClass.canonicalDesc}\")$extraInterfaces)"
            }
        )
    }

    private fun mockitoAnyFromType(type: Type): String = "Mockito." + when (type) {
        is PrimitiveType -> when (type) {
            is IntType -> "anyInt()"
            is BoolType -> "anyBoolean()"
            is ByteType -> "anyByte()"
            is CharType -> "anyChar()"
            is LongType -> "anyLong()"
            is ShortType -> "anyShort()"
            is DoubleType -> "anyDouble()"
            is FloatType -> "anyFloat()"
        }

        else -> "any()"
    }


    override fun printMockSetupMethod(owner: ActionSequence, call: MockSetupMethod): List<String> {
        if (call.returnValues.isEmpty()) {
            return emptyList()
        }

        call.returnValues.forEach { it.printAsJava() }
        val returns = call.returnValues.joinToString(separator = ", ") { value ->
            value.forceCastIfNull(call.method.returnType.asType)
        }

        val anys = call.method.argTypes.joinToString(separator = ", ") { type ->
            mockitoAnyFromType(type)
        }

        val methodName = call.method.name
        val instance = "(${owner.cast(call.method.klass.asType.asType)})"
        return listOf("Mockito.when($instance.$methodName($anys)).thenReturn($returns)")
    }

    override fun printMockSetField(owner: MockList, mockCall: MockSetField): List<String> {
        val setFieldMethod =
            mockCall.field.type.kexType.primitiveName?.let { reflectionUtils.setPrimitiveFieldMap[it]!! }
                ?: reflectionUtils.setField
        return listOf("${setFieldMethod.name}(${owner.name}, ${owner.name}.getClass(), \"${mockCall.field.name}\", ${mockCall.value.stackName})")
    }
}
