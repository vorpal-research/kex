@file:Suppress("unused")

package org.vorpal.research.kex.trace.symbolic.protocol

import kotlinx.serialization.Serializable


@Serializable
sealed class ControllerCommand

@Serializable
data class PortCommand(val port: Int) : ControllerCommand()

@Serializable
data class ExitCommand(val message: String) : ControllerCommand()
