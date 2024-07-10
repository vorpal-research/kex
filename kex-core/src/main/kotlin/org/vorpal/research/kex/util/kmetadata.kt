package org.vorpal.research.kex.util

import kotlinx.metadata.*
import kotlinx.metadata.jvm.KotlinClassMetadata
import org.vorpal.research.kex.ktype.KexArray
import org.vorpal.research.kex.ktype.KexPointer
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kfg.ir.Method
import kotlin.reflect.KType


fun Class<*>.getKotlinMetadata(): KotlinClassMetadata {
    val annotation = this.getAnnotation(Metadata::class.java)
    return KotlinClassMetadata.readStrict(annotation)
}

private fun trimClassName(name: String) = buildString {
    val actualName = name.split(" ").last()
    val filtered = actualName.dropWhile { it == '[' }.removeSuffix(";")
    append(actualName.takeWhile { it == '[' })
    append(filtered.dropWhile { it == 'L' })
}

fun KexType.isElementNullable(kmType: KmType) = when (this) {
    is KexArray -> when {
        kmType.arguments.isEmpty() -> false
        else -> kmType.arguments.first().type?.isNullable ?: false
    }

    else -> false
}

fun KexType.isNonNullable(kmType: KmType) = when (this) {
    is KexPointer -> !kmType.isNullable
    else -> false
}

val KmType.trimmedName: String
    get() {
        return when (val classifier = this.classifier) {
            is KmClassifier.Class -> trimClassName(classifier.name)
            is KmClassifier.TypeAlias -> trimClassName(classifier.name)
            is KmClassifier.TypeParameter -> TODO("What should we do?")
        }
    }

infix fun KmFunction.eq(method: Method): Boolean {
    val parameters = this.valueParameters
    val name = this.name
    return name == method.name && parameters.zip(method.argTypes).fold(true) { acc, (kmType, methodType) ->
        acc && methodType.trimmedName == kmType.type.trimmedName
    }
}

fun KmClass.find(method: Method) = functions.find { it eq method }

fun KmClass.getKFunction(method: Method): KmFunction? {
    this.supertypes
    return this.find(method) // TODO: find on supertypes is not available due to this.supertypes has a List<KmType> type
}
