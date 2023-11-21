package org.vorpal.research.kex.test.concolic.mock;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;


@SuppressWarnings("ALL")
public class MockWithFieldsTests {
    abstract class AbstractToMock {
        ToMock field;

        abstract int foo();
    }

    public void testAbstractToMock(AbstractToMock a) {
        ToMock b = a.field;
        if (b.foo() == 42) {
            if (a.foo() == 33) {
                AssertIntrinsics.kexAssert(true);
            } else {
                AssertIntrinsics.kexAssert(true);
            }
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }


    public class Impl {
        ToMock field;
    }

    public void testMockIsField(Impl a) {
        ToMock mock = a.field;
        if (mock.foo() == 42) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    public void testArrayOfMocks(ToMock[][] array) {
        for (int i = 0; i < 1; i++) {
            for (int j = 0; j < 1; j++) {
                AssertIntrinsics.kexAssert(array[i][j].foo() == 10 * i + j);
            }
        }
    }

}
