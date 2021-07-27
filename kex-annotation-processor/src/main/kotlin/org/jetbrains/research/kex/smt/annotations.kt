package org.jetbrains.research.kex.smt

annotation class SMTExpr(
        val solver: String,
        val importPackage: String,
        val context: String,
        val expr: String,
        val sort: String,
        val function: String,
        val generateString: Boolean = false
)

annotation class SMTMemory(
        val solver: String,
        val importPackage: String,
        val context: String,
        val byteSize: Int
)

annotation class SMTExprFactory(
        val solver: String,
        val importPackage: String,
        val context: String
)

annotation class SMTContext(
        val solver: String,
        val importPackage: String,
        val context: String
)

annotation class SMTConverter(
        val solver: String,
        val importPackage: String
)

annotation class AbstractSolver

annotation class Solver(
        val name: String
)