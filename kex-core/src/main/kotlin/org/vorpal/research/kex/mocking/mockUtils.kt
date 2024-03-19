package org.vorpal.research.kex.mocking

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.Config
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.MockDescriptor
import org.vorpal.research.kfg.ir.Class

enum class MockingRule {
    // Order is important! First rule applies first
    ANY, UNIMPLEMENTED, LAMBDA
}


fun Config.getMockMaker(ctx: ExecutionContext): MockMaker =
    getMultipleStringValue("mock", "rule")
        .map { enumName -> MockingRule.valueOf(enumName.uppercase()) }
        .sortedBy { rule -> rule.ordinal }
        .map { rule -> createMockMaker(rule, ctx) }
        .let { mockMakers -> CompositeMockMaker(mockMakers) }