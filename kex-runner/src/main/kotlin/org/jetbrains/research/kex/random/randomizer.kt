package org.jetbrains.research.kex.random

import com.abdullin.kthelper.tryOrNull
import java.lang.reflect.Type

abstract class RandomizerError(msg: String) : Exception(msg)
class GenerationException(msg: String) : RandomizerError(msg)
class UnknownTypeException(msg: String) : RandomizerError(msg)

interface Randomizer {
    /**
     * @return generated object or throws #RandomizerError
     */
    fun next(type: Type): Any?

    /**
     * @return generated object or #null if any exception has occurred
     */
    fun nextOrNull(type: Type) = tryOrNull { next(type) }
}


