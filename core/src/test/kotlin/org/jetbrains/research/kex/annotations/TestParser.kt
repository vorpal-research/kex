package org.jetbrains.research.kex.annotations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestParser {
    @Test
    fun test1() {
        val extra = ExternalAnnotationsLoader()
        extra.loadFrom(TestParser::class.java.getResource("annotations.1.xml"))
        val call = extra.getExactCall("java/io/Reader.read", "char[]", "int", "int")
        assertNotNull(call, "call \"java/io/Reader.read\" not loaded")
        assertEquals(1, call.annotations.size, "1 annotation for this method expected")
        val callAnnotation = call.annotations.first()
        assertEquals("org.jetbrains.annotations.Range", callAnnotation.name)
        val rangeAnnotation = callAnnotation as Range
        assertEquals(-1, rangeAnnotation.from)
        assertEquals(Int.MAX_VALUE.toLong(), rangeAnnotation.to)
        val annotations = call.params[0].annotations
        assertEquals(1, annotations.size)
        val annotation = annotations[0]
        assertEquals("org.jetbrains.annotations.NotNull", annotation.name)
        assertEquals(0, call.params[1].annotations.size)
        val callInit = extra.getExactCall("java/io/File.<init>", "java/lang/String")
        assertNotNull(callInit)
        assertEquals(emptyList(), callInit.annotations)
        assertEquals("java/io/File", callInit.returnType)
        assertEquals(1, callInit.params.size)
        assertEquals(1, callInit.params[0].annotations.size)
        val initAnnotation = callInit.params[0].annotations[0]
        assertEquals("org.jetbrains.annotations.NotNull", initAnnotation.name)
    }
}

