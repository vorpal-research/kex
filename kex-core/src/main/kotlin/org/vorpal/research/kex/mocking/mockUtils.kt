package org.vorpal.research.kex.mocking

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.Config
import org.vorpal.research.kex.ktype.KexRtManager.isKexRt
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kfg.type.objectType

enum class MockingRule {
    // Order is important! First rule applies first
    UNIMPLEMENTED, ANY, LAMBDA
}


fun Class.canMock(): Boolean =
    !isFinal && !isPrivate && !isKexRt && !(!isPublic && pkg.concreteName.startsWith("java"))

fun Method.canMock(types: TypeFactory): Boolean = when {
    name == "getClass" && argTypes.isEmpty() -> false
    name == "hashCode" && argTypes.isEmpty() -> false
    name == "equals" && argTypes == listOf(types.objectType) -> false
    isFinal || isPrivate -> false

    else -> true
}


fun Config.getMockMaker(ctx: ExecutionContext): MockMaker =
    getMultipleStringValue("mock", "rule")
        .map { enumName -> MockingRule.valueOf(enumName.uppercase()) }
        .sortedBy { rule -> rule.ordinal }
        .map { rule -> createMockMaker(rule, ctx) }
        .let { mockMakers -> composeMockMakers(mockMakers) }

val Config.isMockitoClassesWorkaroundEnabled: Boolean
    get() = getBooleanValue("mock", "mockitoClassesWorkaround", true)

val Config.isMockitoJava8WorkaroundEnabled: Boolean
    get() = getBooleanValue("mock", "java8WorkaroundEnabled", false)

val Config.logTypeFix: Boolean
    get() = getBooleanValue("mock", "logTypeFix", false)

val Config.logStackTraceTypeFix: Boolean
    get() = getBooleanValue("mock", "logStackTraceTypeFix", false)

val Config.isExpectMocks: Boolean
    get() = getBooleanValue("mock", "expectMocks", false)

val Config.isEasyRandomExcludeLambdas: Boolean
    get() = getBooleanValue("mock", "easyRandomExcludeLambdas", false)

val Config.isZeroCoverageEpsilon: Boolean
    get() = getBooleanValue("mock", "zeroCoverageEpsilon", false)


// debug purposes, normally should be false
@Suppress("unused")
val Config.isMockTest: Boolean
    get() = getBooleanValue("mock", "test", false).also { if (it) println("Test feature invoked!") }