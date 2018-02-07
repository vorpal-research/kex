package org.jetbrains.research.kex.driver

import org.jetbrains.research.kex.config.GlobalConfig
import soot.G
import soot.PackManager
import soot.Scene
import soot.SootClass
import soot.options.Options

class SootDriver private constructor() {
    private val config = GlobalConfig.instance

    fun setup(jarPackage: String, mainClass: String) {
        G.reset();
        Options.v().set_via_shimple(true)
        Options.v().set_keep_line_number(true)
        Options.v().set_keep_offset(true)
        Options.v().set_app(true)
        Options.v().set_whole_program(true)
        Options.v().set_allow_phantom_refs(true)
        Options.v().parse(arrayOf("-f", "S"))   // output format Shimple
        Scene.v().loadBasicClasses()

        val cp = config.getMultipleStringValue("soot-classpath").joinToString(":")
        Scene.v().sootClassPath = "$cp:$jarPackage"
        val mainClass = Scene.v().loadClassAndSupport(mainClass)
        mainClass.setApplicationClass()
        Scene.v().loadNecessaryClasses()
        Scene.v().loadDynamicClasses()
        PackManager.v().runPacks()
    }

    fun getClass(name: String): SootClass = Scene.v().getSootClass(name)
    fun getClasses(): Collection<SootClass> = Scene.v().classes

    private object Holder { val INSTANCE = SootDriver() }

    companion object {
        val instance: SootDriver by lazy { Holder.INSTANCE }
    }
}