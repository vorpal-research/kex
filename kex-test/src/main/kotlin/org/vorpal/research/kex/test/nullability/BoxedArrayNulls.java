package org.vorpal.research.kex.test.nullability;

public class BoxedArrayNulls {
    int function(Integer[] x) {
        if (x == null) {
            return -1;
        }
        for (int i = 0; i < x.length; i++) {
            if (x[i] == null) {
                return i;
            }
        }
        return x.length;
    }
}
