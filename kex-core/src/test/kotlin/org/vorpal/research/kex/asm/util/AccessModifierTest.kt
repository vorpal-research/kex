package org.vorpal.research.kex.asm.util

import org.vorpal.research.kex.KexTest
import org.vorpal.research.kfg.Package
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessModifierTest : KexTest("access-modifier") {

    @Test
    fun checkAccessModifiers() {
        val public = AccessModifier.Public
        val pkg = AccessModifier.Package(`package`.concretePackage)
        val pkg2 = AccessModifier.Package(Package.parse("${packageName}.debug"))
        val protected = AccessModifier.Protected(cm.getByPackage(pkg.pkg).random())
        val protected2 = AccessModifier.Protected(cm.getByPackage(pkg2.pkg).random())
        val private = AccessModifier.Private

        // check public accesses
        assertFalse(public.canAccess(private))
        assertFalse(public.canAccess(pkg))
        assertFalse(public.canAccess(pkg2))
        assertFalse(public.canAccess(protected))
        assertFalse(public.canAccess(protected2))
        assertTrue(public.canAccess(public))

        // check package accesses
        assertFalse(pkg.canAccess(private))
        assertTrue(pkg.canAccess(pkg))
        assertFalse(pkg.canAccess(pkg2))
        assertTrue(pkg.canAccess(protected))
        assertFalse(pkg.canAccess(protected2))
        assertTrue(pkg.canAccess(public))

        assertFalse(pkg2.canAccess(private))
        assertFalse(pkg2.canAccess(pkg))
        assertTrue(pkg2.canAccess(pkg2))
        assertFalse(pkg2.canAccess(protected))
        assertTrue(pkg2.canAccess(protected2))
        assertTrue(pkg2.canAccess(public))

        // check protected accesses
        assertFalse(protected.canAccess(private))
        assertTrue(protected.canAccess(pkg))
        assertFalse(protected.canAccess(pkg2))
        assertTrue(protected.canAccess(protected))
        assertFalse(protected.canAccess(protected2))
        assertTrue(protected.canAccess(public))

        assertFalse(protected2.canAccess(private))
        assertFalse(protected2.canAccess(pkg))
        assertTrue(protected2.canAccess(pkg2))
        assertFalse(pkg2.canAccess(protected))
        assertTrue(pkg2.canAccess(protected2))
        assertTrue(pkg2.canAccess(public))

        // check private accesses
        assertTrue(private.canAccess(private))
        assertTrue(private.canAccess(pkg))
        assertTrue(private.canAccess(pkg2))
        assertTrue(private.canAccess(protected))
        assertTrue(private.canAccess(protected2))
        assertTrue(private.canAccess(public))
    }
}