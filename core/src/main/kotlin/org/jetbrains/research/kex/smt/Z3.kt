package org.jetbrains.research.kex.smt

import org.jetbrains.research.kex.smtinstance.SMTExpr
import org.jetbrains.research.kex.smtinstance.SMTMemory

@SMTExpr(solver = "Z3", solverImport = "com.microsoft.z3", context = "Context", expr = "Expr", sort = "Sort")
abstract class Z3SMTExpr

@SMTMemory(solver = "Z3", solverImport = "com.microsoft.z3", context = "Context", byteSize = 32)
abstract class Z3SMTMemory