package org.jetbrains.research.kex.smt.z3

import org.jetbrains.research.kex.smt.*

const val generateStrings = false

@SMTExpr(
    solver = "Z3",
    importPackage = "com.microsoft.z3",
    context = "Context",
    expr = "Expr<*>",
    sort = "Sort",
    function = "FuncDecl<*>",
    generateString = generateStrings
)
abstract class Z3SMTExpr

@SMTMemory(
    solver = "Z3",
    importPackage = "com.microsoft.z3",
    context = "Context",
    byteSize = 32,
    generateString = generateStrings
)
abstract class Z3SMTMemory

@SMTExprFactory(
    solver = "Z3",
    importPackage = "com.microsoft.z3",
    context = "Context",
    generateString = generateStrings
)
abstract class Z3SMTExprFactory

@SMTContext(
    solver = "Z3",
    importPackage = "com.microsoft.z3",
    context = "Context",
    generateString = generateStrings
)
abstract class Z3SMTContext

@SMTConverter(
    solver = "Z3",
    importPackage = "com.microsoft.z3",
    generateString = generateStrings
)
abstract class Z3SMTConverter