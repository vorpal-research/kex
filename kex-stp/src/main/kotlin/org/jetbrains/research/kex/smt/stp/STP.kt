package org.jetbrains.research.kex.smt.stp

import org.jetbrains.research.kex.smt.*

@SMTExpr(solver = "STP", importPackage = "org.zhekehz.stpjava", context = "ValidityChecker", expr = "Expr", sort = "Sort", function = "FunctionExpr")
abstract class STPSMTExpr

@SMTMemory(solver = "STP", importPackage = "org.zhekehz.stpjava", context = "ValidityChecker", byteSize = 32)
abstract class STPSMTMemory

@SMTExprFactory(solver = "STP", importPackage = "org.zhekehz.stpjava", context = "ValidityChecker")
abstract class STPSMTExprFactory

@SMTContext(solver = "STP", importPackage = "org.zhekehz.stpjava", context = "ValidityChecker")
abstract class STPSMTContext

@SMTConverter(solver = "STP", importPackage = "org.zhekehz.stpjava")
abstract class STPSMTConverter
