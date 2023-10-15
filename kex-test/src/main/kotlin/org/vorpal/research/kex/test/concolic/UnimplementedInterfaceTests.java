package org.vorpal.research.kex.test.concolic;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;

interface Unimplemented {

    int foo();
}

@SuppressWarnings("ALL")
public class UnimplementedInterfaceTests {

    public void testUnimplemented(Unimplemented i) {
        if (i.foo() == 42) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

}
