package org.vorpal.research.kex.smt.boolector

import org.vorpal.research.kex.smt.*

@SMTExpr(solver = "Boolector",
        importPackage = "org.vorpal.research.boolector",
        context = "Btor",
        expr = "BoolectorNode",
        sort = "BoolectorSort",
        function = "BoolectorFunction")
abstract class BoolectorSMTExpr

@SMTMemory(solver = "Boolector", importPackage = "org.vorpal.research.boolector", context = "Btor", byteSize = 32)
abstract class BoolectorSMTMemory

@SMTExprFactory(solver = "Boolector", importPackage = "org.vorpal.research.boolector", context = "Btor")
abstract class BoolectorSMTExprFactory

@SMTContext(solver = "Boolector", importPackage = "org.vorpal.research.boolector", context = "Btor")
abstract class BoolectorSMTContext

@SMTConverter(solver = "Boolector", importPackage = "org.vorpal.research.boolector")
abstract class BoolectorSMTConverter