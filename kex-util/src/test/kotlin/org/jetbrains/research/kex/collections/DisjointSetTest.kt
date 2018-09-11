package org.jetbrains.research.kex.collections

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisjointSetTest {

    @Test
    fun find() {
        val ds = DisjointSet<Int>()

        val first = ds.emplace(1)
        val second = ds.emplace(2)
        val third = ds.emplace(3)
        val fourth = ds.emplace(4)

        val root = ds.join(first, second)

        val x1 = ds.emplace(7)
        val x2 = ds.emplace(8)
        val x3 = ds.emplace(10)
        val x4 = ds.emplace(11)
        val x5 = ds.join(x1, x2)
        val x6 = ds.join(x3, x4)
        val xroot = ds.join(x5, x6)

        val nr = ds.join(root, xroot)

        assertEquals(ds.find(nr), nr)
        assertEquals(ds.find(first), nr)
        assertEquals(ds.find(root), nr)

        assertEquals(ds.find(third), third)
        assertEquals(ds.find(fourth), fourth)

        val jn = ds.join(third, fourth)
        assertEquals(ds.find(third), jn)
        assertEquals(ds.find(fourth), jn)
    }

    @Test
    fun findUnsafe() {
        val ds = DisjointSet<Int>()
        assertEquals(ds.findUnsafe(null), null)
    }

    @Test
    fun join() {
        val ds = DisjointSet<Int>()

        val first = ds.emplace(1)
        val second = ds.emplace(2)
        val third = ds.emplace(3)

        assertEquals(ds.join(first, first), first)

        val root = ds.join(first, second)
        assertTrue(first.isRoot() || second.isRoot())
        assertTrue(first == root || second == root)
        val nonroot = if (first == root) second else first
        assertEquals(root.rank, 1)
        assertEquals(nonroot.rank, 0)
        assertFalse(nonroot.isRoot())
        assertEquals(nonroot.getRoot(), root)

        val sroot = ds.join(root, third)
        assertEquals(sroot, root)
        assertEquals(root.rank, 1)
        assertEquals(third.getRoot(), sroot)

        val x1 = ds.emplace(7)
        val x2 = ds.emplace(8)
        val x3 = ds.emplace(10)
        val x4 = ds.emplace(11)
        val x5 = ds.join(x1, x2)
        val x6 = ds.join(x3, x4)
        val xroot = ds.join(x5, x6)
        assertEquals(xroot.rank, 2)
        assertEquals(x1.getRoot(), xroot)

        val nr = ds.join(root, xroot)
        assertEquals(nr, xroot)
        assertFalse(root.isRoot())
        assertEquals(root.getRoot(), xroot)
        assertEquals(first.getRoot(), xroot)
    }

    @Test
    fun emplace() {
        val ds = DisjointSet<Int>()

        val element = ds.emplace(1)

        assertEquals(element.rank, 0)
        assertTrue(element.isRoot())
        assertEquals(element, element.getRoot())
    }
}