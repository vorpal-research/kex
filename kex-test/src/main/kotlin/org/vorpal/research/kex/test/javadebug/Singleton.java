package org.vorpal.research.kex.test.javadebug;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;

@SuppressWarnings("ALL")
public class Singleton {

    private Singleton() {
    }

    public static final Singleton INSTANCE = new Singleton();

    public static void testSingleton(Singleton a) {
        if (a == Singleton.INSTANCE) {
            AssertIntrinsics.kexAssert(true);
        }
    }
}
