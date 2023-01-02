package org.vorpal.research.kex.smt

annotation class SMTExpr(
        val solver: String,
        val importPackages: Array<String>,
        val context: String,
        val expr: String,
        val sort: String,
        val function: String,
        val generateString: Boolean = false
)

annotation class SMTMemory(
        val solver: String,
        val importPackages: Array<String>,
        val context: String,
        val byteSize: Int,
        val generateString: Boolean = false
)

annotation class SMTExprFactory(
        val solver: String,
        val importPackages: Array<String>,
        val context: String,
        val generateString: Boolean = false
)

annotation class SMTContext(
        val solver: String,
        val importPackages: Array<String>,
        val context: String,
        val generateString: Boolean = false
)

annotation class SMTConverter(
        val solver: String,
        val importPackages: Array<String>,
        val generateString: Boolean = false
)

annotation class AbstractSolver

annotation class Solver(
        val name: String
)

annotation class AbstractAsyncSolver

annotation class AsyncSolver(
        val name: String
)
