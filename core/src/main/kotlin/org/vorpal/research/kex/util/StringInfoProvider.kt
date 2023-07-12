package org.vorpal.research.kex.util

import org.vorpal.research.kex.ktype.KexArray
import org.vorpal.research.kex.ktype.KexByte
import org.vorpal.research.kex.ktype.KexChar
import org.vorpal.research.kex.ktype.asArray
import org.vorpal.research.kfg.charSequence
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.type.objectType
import org.vorpal.research.kfg.type.stringType
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

@Suppress("unused")
abstract class StringInfoContext {
    private val jvmVersion = getJvmVersion()

    val valueArrayName: String = "value"

    val valueArrayType: KexArray = when {
        jvmVersion == 8 -> KexChar.asArray()
        jvmVersion > 8 -> KexByte.asArray()
        else -> unreachable { log.error("Unsupported JVM version: $jvmVersion") }
    }

    val Class.emptyInit
        get() = getCtor()
    val Class.copyInit
        get() = getCtor(cm.type.stringType)
    val Class.charArrayInit
        get() = getCtor(cm.type.charType.asArray)
    val Class.charArrayWOffsetInit
        get() = getCtor(cm.type.charType.asArray, cm.type.intType, cm.type.intType)

    val Class.byteArrayInit
        get() = getCtor(cm.type.byteType.asArray)

    val Class.byteArrayWOffsetInit
        get() = getCtor(cm.type.byteType.asArray, cm.type.intType, cm.type.intType)

    val Class.length
        get() = getMethod("length", cm.type.intType)
    val Class.isEmpty
        get() = getMethod("isEmpty", cm.type.boolType)
    val Class.charAt
        get() = getMethod("charAt", cm.type.charType, cm.type.intType)
    val Class.equals
        get() = getMethod("equals", cm.type.boolType, cm.type.objectType)
    val Class.startsWith
        get() = getMethod("startsWith", cm.type.boolType, cm.type.stringType)
    val Class.startsWithOffset
        get() = getMethod("startsWith", cm.type.boolType, cm.type.stringType, cm.type.intType)
    val Class.endsWith
        get() = getMethod("endsWith", cm.type.boolType, cm.type.stringType)
    val Class.indexOf
        get() = getMethod("indexOf", cm.type.intType, cm.type.intType)
    val Class.indexOfWOffset
        get() = getMethod("indexOf", cm.type.intType, cm.type.intType, cm.type.intType)
    val Class.stringIndexOf
        get() = getMethod("indexOf", cm.type.intType, cm.type.stringType)
    val Class.stringIndexOfWOffset
        get() = getMethod("indexOf", cm.type.intType, cm.type.stringType, cm.type.intType)
    val Class.substring
        get() = getMethod("substring", cm.type.stringType, cm.type.intType)
    val Class.substringWLength
        get() = getMethod("substring", cm.type.stringType, cm.type.intType, cm.type.intType)
    val Class.subSequence
        get() = getMethod("subSequence", cm.charSequence.asType, cm.type.intType, cm.type.intType)
    val Class.concat
        get() = getMethod("concat", cm.type.stringType, cm.type.stringType)
    val Class.contains
        get() = getMethod("contains", cm.type.boolType, cm.charSequence.asType)
    val Class.toString
        get() = getMethod("toString", cm.type.stringType)
    val Class.compareTo
        get() = getMethod("compareTo", cm.type.intType, cm.type.stringType)

    val Class.toCharArray
        get() = getMethod("toCharArray", cm.type.charType.asArray)
}
