package org.vorpal.research.kex.test.concolic.mock;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;
import org.vorpal.research.kex.test.concolic.TestEnum;


@SuppressWarnings("ALL")
public class MockEnumTests {

    interface MockWithEnum {
        TestEnum foo();
    }

    public void testMockAndEnum(MockWithEnum mock) {
        if (mock.foo() == TestEnum.B) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }
}
