package org.vorpal.research.kex.smt.ksmt

import org.vorpal.research.kex.smt.*


const val generateStrings = false

@SMTExpr(
    solver = "KSMT",
    importPackages = ["org.ksmt", "org.ksmt.decl", "org.ksmt.sort", "org.ksmt.expr"],
    context = "KContext",
    expr = "KAst",
    sort = "KSort",
    function = "KFuncDecl<*>",
    generateString = generateStrings
)
abstract class KSMTSMTExpr

@SMTMemory(
    solver = "KSMT",
    importPackages = ["org.ksmt", "org.ksmt.decl", "org.ksmt.sort", "org.ksmt.expr"],
    context = "KContext",
    byteSize = 32,
    generateString = generateStrings
)
abstract class KSMTSMTMemory

@SMTExprFactory(
    solver = "KSMT",
    importPackages = ["org.ksmt", "org.ksmt.decl", "org.ksmt.sort", "org.ksmt.expr"],
    context = "KContext",
    generateString = generateStrings
)
abstract class KSMTSMTExprFactory

@SMTContext(
    solver = "KSMT",
    importPackages = ["org.ksmt", "org.ksmt.decl", "org.ksmt.sort", "org.ksmt.expr"],
    context = "KContext",
    generateString = generateStrings
)
abstract class KSMTSMTContext

@SMTConverter(
    solver = "KSMT",
    importPackages = ["org.ksmt", "org.ksmt.decl", "org.ksmt.sort", "org.ksmt.expr"],
    generateString = generateStrings
)
abstract class KSMTSMTConverter
