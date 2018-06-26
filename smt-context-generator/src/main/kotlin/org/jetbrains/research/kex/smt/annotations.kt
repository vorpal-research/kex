package org.jetbrains.research.kex.smt

annotation class SMTExpr(
        val solver: String,
        val importPackage: String,
        val context: String,
        val expr: String,
        val sort: String
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