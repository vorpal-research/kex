package org.vorpal.research.kex.mocking

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.Config
import org.vorpal.research.kex.ktype.KexRtManager.isKexRt
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kfg.type.objectType
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.logging.warn

enum class MockingRule {
    // Order is important! First rule applies first
    UNIMPLEMENTED, ANY, LAMBDA
}


fun Config.getMockMaker(ctx: ExecutionContext): MockMaker =
    getMultipleStringValue("mock", "rule")
        .map { enumName -> MockingRule.valueOf(enumName.uppercase()) }
        .sortedBy { rule -> rule.ordinal }
        .map { rule -> createMockMaker(rule, ctx) }
        .let { mockMakers -> composeMockMakers(mockMakers) }


fun Class.canMock(): Boolean =
    !isFinal && !isPrivate && !isKexRt && !(!isPublic && pkg.concreteName.startsWith("java"))

fun Method.canMock(types: TypeFactory): Boolean = when {
    name == "getClass" && argTypes.isEmpty() -> false
    name == "hashCode" && argTypes.isEmpty() -> false
    name == "equals" && argTypes == listOf(types.objectType) -> false
    isFinal || isPrivate -> false

    else -> true
}