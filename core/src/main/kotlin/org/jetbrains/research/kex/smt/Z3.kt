package org.jetbrains.research.kex.smt

import org.jetbrains.research.kex.smtinstance.SMTExpr

@SMTExpr(solver = "Z3", solverImport = "com.microsoft.z3", context = "Context", expr = "Expr", sort = "Sort")
abstract class Z3SMTExpr
