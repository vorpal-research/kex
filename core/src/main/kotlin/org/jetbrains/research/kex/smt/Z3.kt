package org.jetbrains.research.kex.smt

@SMTExpr(solver = "Z3", importPackage = "com.microsoft.z3", context = "Context", expr = "Expr", sort = "Sort")
abstract class Z3SMTExpr

@SMTMemory(solver = "Z3", importPackage = "com.microsoft.z3", context = "Context", byteSize = 32)
abstract class Z3SMTMemory

@SMTExprFactory(solver = "Z3", importPackage = "com.microsoft.z3", context = "Context")
abstract class Z3SMTExprFactory