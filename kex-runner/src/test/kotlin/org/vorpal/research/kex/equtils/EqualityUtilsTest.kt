package org.vorpal.research.kex.equtils

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.junit.AfterClass
import org.junit.Test
import org.vorpal.research.kex.KexRunnerTest
import org.vorpal.research.kex.compile.CompilerHelper
import org.vorpal.research.kex.config.RuntimeConfig
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.reanimator.codegen.javagen.EqualityUtilsPrinter
import org.vorpal.research.kex.util.compiledCodeDirectory
import java.lang.reflect.Method
import java.net.URLClassLoader
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

@ExperimentalSerializationApi
@InternalSerializationApi
class EqualityUtilsTest: KexRunnerTest("equality-utils") {

    companion object {
        @AfterClass
        @JvmStatic fun cleanup() {
            val generatedFilePath = Path(
                System.getProperty("user.dir"),
                "src/test/kotlin/org/vorpal/research/kex/equtils/EqualityUtils.java"
            )
            generatedFilePath.deleteIfExists()
        }
    }

    @BeforeTest
    fun setup() {
        val prevTestcaseDir = RuntimeConfig.getStringValue("testGen", "testsDir")

        val path = Path(System.getProperty("user.dir"), "src/test/kotlin/")
        RuntimeConfig.setValue("testGen", "testsDir", path)
        EqualityUtilsPrinter.equalityUtils("org.vorpal.research.kex.equtils")
        val compilerHelper = CompilerHelper(analysisContext)
        compilerHelper.compileFile(path.resolve("org/vorpal/research/kex/equtils/EqualityUtils.java"))

        prevTestcaseDir?.let { RuntimeConfig.setValue("testGen", "testsDir", it) }
        // TODO: null value not restored
    }

    private fun customEquals(t1: Any?, t2: Any?): Boolean {
        val urlClassLoader = URLClassLoader(arrayOf(kexConfig.compiledCodeDirectory.toUri().toURL()))
        val klass = urlClassLoader.loadClass("org.vorpal.research.kex.equtils.EqualityUtils")
        val argTypes = Array(2) { Class.forName("java.lang.Object") }
        val method: Method = klass.getDeclaredMethod("customEquals", *argTypes)
        method.isAccessible = true
        val args = arrayOf(t1, t2)
        return method.invoke(null, *args) as Boolean
    }

    @Test
    fun constCompare() {
        assert(customEquals(17, 17))
        assert(!customEquals(17, 13))
        val i1 = 10
        val i2 = 10
        val i3 = 13
        assert(customEquals(i1, i2))
        assert(!customEquals(i1, i3))
        val s1 = "I am string!"
        val s2 = "I am string!"
        val s3 = "I am also string!"
        assert(customEquals(s1, s2))
        assert(!customEquals(s1, s3))
    }


    internal open class SimpleClassWithData {
        var data = 10
    }


    internal open class SimpleClassWithDataAccessor : SimpleClassWithData() {
        var data2 = 15
    }


    internal class SimpleClassSecondAccessor : SimpleClassWithDataAccessor()


    internal class SimpleSimilarClassWithData {
        var data = 10
    }


    @Test
    fun testDifferentClasses() {
        assert(!customEquals(SimpleClassWithData(), SimpleSimilarClassWithData()))
        assert(!customEquals(SimpleClassWithData(), SimpleClassWithDataAccessor()))
        assert(customEquals(SimpleClassSecondAccessor(), SimpleClassSecondAccessor()))
    }


    class ClassWithRefToThemselves {
        var intData = 10
        var stringData = "I am just Data!"
        var ref1: ClassWithRefToThemselves? = null
        var ref2: ClassWithRefToThemselves? = null

        constructor()
        constructor(refData: ClassWithRefToThemselves?) {
            ref1 = refData
        }

        constructor(refData1: ClassWithRefToThemselves?, refData2: ClassWithRefToThemselves?) {
            ref1 = refData1
            ref2 = refData2
        }

        constructor(
            iData: Int, sData: String, refData1: ClassWithRefToThemselves?,
            refData2: ClassWithRefToThemselves?
        ) {
            intData = iData
            stringData = sData
            ref1 = refData1
            ref2 = refData2
        }
    }


    @Test
    fun nullTest() {
        val t1 = ClassWithRefToThemselves()
        assert(customEquals(null, null))
        assert(!customEquals(null, t1))
        val t2 = ClassWithRefToThemselves(t1)
        assert(!customEquals(t1, t2))
    }

    @Test
    fun circlesTest1() {
        val t11 = ClassWithRefToThemselves()
        t11.ref1 = t11
        val t21 = ClassWithRefToThemselves()
        val t22 = ClassWithRefToThemselves(t21)
        t21.ref1 = t22
        assert(!customEquals(t11, t21))
        val t12 = ClassWithRefToThemselves(t11)
        t11.ref1 = t12
        assert(customEquals(t11, t21))
    }

    @Test
    fun circlesTest2() {
        val t1 = ClassWithRefToThemselves()
        val t2 = ClassWithRefToThemselves(t1)
        t1.ref1 = t2
        assert(customEquals(t1, t2))
        val t3 = ClassWithRefToThemselves()
        t1.ref1 = t2
        t1.ref2 = t3
        t2.ref1 = t3
        t2.ref2 = t1
        t3.ref1 = t1
        t3.ref2 = t2
        assert(customEquals(t1, t2))
        t2.ref1 = t1
        t2.ref2 = t3
        t3.ref1 = t3
        t3.ref2 = t3
        assert(customEquals(t1, t2))
        t3.ref1 = t2
        t3.ref2 = t2
        assert(!customEquals(t1, t2))
    }

    @Test
    fun repeatsTest() {
        val t1 = ClassWithRefToThemselves()
        val t2 = ClassWithRefToThemselves()
        val t3 = ClassWithRefToThemselves()
        val a1 = ClassWithRefToThemselves(t1, t1)
        val a2 = ClassWithRefToThemselves(t2, t3)
        assert(customEquals(a1, a2))
    }
}