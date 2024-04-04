package com.microsoft.z3

class ExtendedContext : Context() {
    private var trueExpr: BoolExpr? = null
    private var falseExpr: BoolExpr? = null
    private val bvSortCache = mutableMapOf<Int, BitVecSort>()
    private val bv32Sort get() = bvSortCache[32]
    private val bv64Sort get() = bvSortCache[64]
    private var array32to32Sort: Sort? = null
    private var array32to64Sort: Sort? = null
    private var array64to64Sort: Sort? = null

    init {

        trueExpr = null
        falseExpr = null
        array32to32Sort = null
        array32to64Sort = null
        array64to64Sort = null
        bvSortCache.clear()
    }

    override fun mkTrue(): BoolExpr {
        if (trueExpr == null) {
            trueExpr = super.mkTrue()
        }
        return trueExpr!!
    }

    override fun mkFalse(): BoolExpr {
        if (falseExpr == null) {
            falseExpr = super.mkFalse()
        }
        return falseExpr!!
    }

    @Suppress("UNCHECKED_CAST")
    override fun <D : Sort, R : Sort> mkArraySort(domain: D, range: R): ArraySort<D, R> = when {
        domain === bv32Sort && range === bv32Sort -> {
            if (array32to32Sort == null) {
                array32to32Sort = super.mkArraySort(bv32Sort, bv32Sort)
            }
            array32to32Sort!! as ArraySort<D, R>
        }

        domain === bv32Sort && range === bv64Sort -> {
            if (array32to64Sort == null) {
                array32to64Sort = super.mkArraySort(bv32Sort, bv64Sort)
            }
            array32to64Sort!! as ArraySort<D, R>
        }

        domain === bv64Sort && range === bv64Sort -> {
            if (array64to64Sort == null) {
                array64to64Sort = super.mkArraySort(bv64Sort, bv64Sort)
            }
            array64to64Sort!! as ArraySort<D, R>
        }

        else -> super.mkArraySort(domain, range)
    }

    override fun mkBitVecSort(width: Int): BitVecSort = bvSortCache.getOrPut(width) { super.mkBitVecSort(width) }
}
