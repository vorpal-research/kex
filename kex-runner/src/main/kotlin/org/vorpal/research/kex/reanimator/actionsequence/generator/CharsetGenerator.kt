package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.ktype.KexString
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.StaticFieldGetter
import org.vorpal.research.kthelper.logging.log

class CharsetGenerator(private val fallback: Generator) : Generator {
    companion object {
        private const val CHARSET_CLASS = "java/nio/charset/Charset"
        private const val CHARSETS_CLASS = "java/nio/charset/StandardCharsets"
        private const val DEFAULT_CHARSET = "US_ASCII"
        private val existingCharsets = setOf("US_ASCII", "ISO_8859_1", "UTF_8", "UTF_16BE", "UTF_16LE", "UTF_16")
    }
    override val context: GeneratorContext
        get() = fallback.context

    override fun supports(descriptor: Descriptor) = descriptor.type.toString() == CHARSET_CLASS && descriptor is ObjectDescriptor

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as? ObjectDescriptor ?: throw IllegalArgumentException()
        descriptor.reduce()

        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        val charsetClass = context.cm[CHARSET_CLASS]
        val charsetsClass = context.cm[CHARSETS_CLASS]

        val nameDescriptor = descriptor["name", KexString()] as? ObjectDescriptor
        val actualName = nameDescriptor?.asStringValue ?: DEFAULT_CHARSET
        actionSequence += if (actualName in existingCharsets) {
            StaticFieldGetter(charsetsClass.getField(actualName, charsetClass.asType))
        } else {
            log.warn("Could not generate charset with name $actualName, falling back to default $DEFAULT_CHARSET")
            StaticFieldGetter(charsetsClass.getField(DEFAULT_CHARSET, charsetClass.asType))
        }
        return actionSequence
    }
}
