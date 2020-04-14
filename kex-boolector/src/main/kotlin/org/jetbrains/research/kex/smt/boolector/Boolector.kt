package org.jetbrains.research.kex.smt.boolector

import org.jetbrains.research.kex.smt.*

@SMTExpr(solver = "Boolector",
        importPackage = "org.jetbrains.research.boolector",
        context = "Btor",
        expr = "BoolectorNode",
        sort = "BoolectorSort",
        function = "BoolectorFunction")
abstract class BoolectorSMTExpr

@SMTMemory(solver = "Boolector", importPackage = "org.jetbrains.research.boolector", context = "Btor", byteSize = 32)
abstract class BoolectorSMTMemory

@SMTExprFactory(solver = "Boolector", importPackage = "org.jetbrains.research.boolector", context = "Btor")
abstract class BoolectorSMTExprFactory

@SMTContext(solver = "Boolector", importPackage = "org.jetbrains.research.boolector", context = "Btor")
abstract class BoolectorSMTContext

@SMTConverter(solver = "Boolector", importPackage = "org.jetbrains.research.boolector")
abstract class BoolectorSMTConverter