package org.vorpal.research.kex.annotations

enum class CallType {
    Constructor, Method
}

interface AnnotatedCall {
    val name: String
    val returnType: String
    val type: CallType
    val params: List<AnnotatedParam>
    val annotations: List<AnnotationInfo>
    val className: String
    val fullName get() = "$className.$name"
}
