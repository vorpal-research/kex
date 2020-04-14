package org.jetbrains.research.kex.smt.z3

import org.jetbrains.research.kex.smt.*

@SMTExpr(solver = "Z3", importPackage = "com.microsoft.z3", context = "Context", expr = "Expr", sort = "Sort", function = "FuncDecl")
abstract class Z3SMTExpr

@SMTMemory(solver = "Z3", importPackage = "com.microsoft.z3", context = "Context", byteSize = 32)
abstract class Z3SMTMemory

@SMTExprFactory(solver = "Z3", importPackage = "com.microsoft.z3", context = "Context")
abstract class Z3SMTExprFactory

@SMTContext(solver = "Z3", importPackage = "com.microsoft.z3", context = "Context")
abstract class Z3SMTContext

@SMTConverter(solver = "Z3", importPackage = "com.microsoft.z3")
abstract class Z3SMTConverter