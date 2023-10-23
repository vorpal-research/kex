package org.vorpal.research.kex.test.concolic;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;


@SuppressWarnings("ALL")
public class UnimplementedInterfaceTests {
    public interface Unimplemented {
        int foo();
    }

    public void testUnimplemented(Unimplemented i) {
        if (i.foo() == 42) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

}
