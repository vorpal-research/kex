package org.jetbrains.research.kex.libsl

import ru.spbstu.insys.libsl.parser.*
import java.io.File


class LibslDescriptor(libraryPath: String) {
    val library: LibraryDecl

    val functionsByAutomaton = mutableMapOf<Automaton, MutableList<FunctionDecl>>()
    val automatonByQualifiedName = mutableMapOf<String, Automaton>()
    val statesMap = mutableMapOf<Pair<Automaton, String>, Int>()

    init {
        val parser = ModelParser()
        val stream = File(libraryPath).inputStream()
        library = parser.parse(stream)

        processLibrary()
    }

    private fun processLibrary() {
        var i = 0
        for (automaton in library.automata) {
            val qualifiedName = (automaton.javaPackage?.name?.plus(".") ?: "") + automaton.name
            automatonByQualifiedName[qualifiedName] = automaton
            automaton.states.forEach { state ->
                statesMap[automaton to state.name] = ++i
            }
        }

        for (function in library.functions) {
            val automatonName = function.entity.type.typeName
            val automaton = library.automata.firstOrNull{ it.name.typeName == automatonName }
                ?: error("unknown automaton: $automatonName")
            functionsByAutomaton.putIfAbsent(automaton, mutableListOf(function))?.add(function)
        }
    }

}