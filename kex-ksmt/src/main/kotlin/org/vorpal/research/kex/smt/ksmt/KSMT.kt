package org.vorpal.research.kex.smt.ksmt

import org.vorpal.research.kex.smt.SMTContext
import org.vorpal.research.kex.smt.SMTConverter
import org.vorpal.research.kex.smt.SMTExpr
import org.vorpal.research.kex.smt.SMTExprFactory
import org.vorpal.research.kex.smt.SMTMemory


const val generateStrings = false

@SMTExpr(
    solver = "KSMT",
    importPackages = ["io.ksmt", "io.ksmt.decl", "io.ksmt.sort", "io.ksmt.expr"],
    context = "KContext",
    expr = "KAst",
    sort = "KSort",
    function = "KFuncDecl<*>",
    generateString = generateStrings
)
abstract class KSMTSMTExpr

@SMTMemory(
    solver = "KSMT",
    importPackages = ["io.ksmt", "io.ksmt.decl", "io.ksmt.sort", "io.ksmt.expr"],
    context = "KContext",
    byteSize = 32,
    generateString = generateStrings
)
abstract class KSMTSMTMemory

@SMTExprFactory(
    solver = "KSMT",
    importPackages = ["io.ksmt", "io.ksmt.decl", "io.ksmt.sort", "io.ksmt.expr"],
    context = "KContext",
    contextInitializer = "simplificationMode = KContext.SimplificationMode.NO_SIMPLIFY",
    generateString = generateStrings
)
abstract class KSMTSMTExprFactory

@SMTContext(
    solver = "KSMT",
    importPackages = ["io.ksmt", "io.ksmt.decl", "io.ksmt.sort", "io.ksmt.expr"],
    context = "KContext",
    generateString = generateStrings
)
abstract class KSMTSMTContext

@SMTConverter(
    solver = "KSMT",
    importPackages = ["io.ksmt", "io.ksmt.decl", "io.ksmt.sort", "io.ksmt.expr"],
    generateString = generateStrings
)
abstract class KSMTSMTConverter
