package org.vorpal.research.kex.mocking

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.Config

enum class MockingRule {
    // Order is important! First rule applies first
    LAMBDA, ANY, UNIMPLEMENTED
}

fun Config.getMockMakers(ctx: ExecutionContext): List<MockMaker> =
    getMultipleStringValue("mock", "rule")
        .map { enumName -> MockingRule.valueOf(enumName.uppercase()) }
        .sortedBy { rule -> rule.ordinal }
        .map { rule -> createMocker(rule, ctx) }