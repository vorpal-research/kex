package org.vorpal.research.kex.test.concolic;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;

public class StringConcolicTests {
    public void testStringCharAt(String s) {
        if (s.charAt(0) == '0') {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    public void testStringLength(String s) {
        if (s.length() == 14) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    public void testStringEquals(String s) {
        if (s.equals("mystring")) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    public void testStringConcat(String a) {
        if ((a.concat("string")).equals("mystring")) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }
}
