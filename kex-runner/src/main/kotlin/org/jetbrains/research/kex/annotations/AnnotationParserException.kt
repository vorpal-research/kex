package org.jetbrains.research.kex.annotations

class AnnotationParserException : RuntimeException {
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(message: String) : super(message)
}
