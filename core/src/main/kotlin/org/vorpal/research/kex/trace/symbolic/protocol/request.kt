package org.vorpal.research.kex.trace.symbolic.protocol

import kotlinx.serialization.Serializable


@Serializable
data class TestExecutionRequest(
    val klass: String,
    val testMethod: String,
    val setupMethod: String?
)
