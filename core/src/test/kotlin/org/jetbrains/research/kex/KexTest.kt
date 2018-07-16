package org.jetbrains.research.kex

import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.Package
import org.junit.Assert.*
import java.io.File
import java.util.jar.JarFile

abstract class KexTest : Loggable {
    val packageName = "org/jetbrains/research/kex/test"

    init {
        val rootDir = System.getProperty("root.dir")
        val jarPath = "$rootDir/kex-test/target/kex-test-0.1-jar-with-dependencies.jar"
        val jarFile = JarFile(jarPath)
        val `package` = Package("$packageName/*")
        CM.parseJar(jarFile, `package`)
    }
}