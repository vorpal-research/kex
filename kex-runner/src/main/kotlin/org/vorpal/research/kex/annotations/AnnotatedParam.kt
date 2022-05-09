package org.vorpal.research.kex.annotations

interface AnnotatedParam {
    val type: String
    val annotations: List<AnnotationInfo>
}
