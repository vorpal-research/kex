package org.vorpal.research.kex.test.concolic;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;

@SuppressWarnings("ALL")
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

// excluded for now, because solver is having trouble
//    public void testStringConcat(String a) {
//        if ((a.concat("string")).equals("mystring")) {
//            AssertIntrinsics.kexAssert(true);
//        } else {
//            AssertIntrinsics.kexAssert(true);
//        }
//    }

//    public String testStringConcat(String x) {
//        if (x.charAt(0) == '0') throw new IllegalStateException();
//        String y = x.concat("\n");
//        if (y.charAt(0) == '1') throw new IllegalStateException();
//        String z = x.concat("\t");
//        if (z.charAt(1) == '2') throw new IllegalStateException();
//        return z;
//    }
}
