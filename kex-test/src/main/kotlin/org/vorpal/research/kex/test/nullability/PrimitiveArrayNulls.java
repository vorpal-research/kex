package org.vorpal.research.kex.test.nullability;

public class PrimitiveArrayNulls {
    int function(int[] x) {
        if (x == null) {
            return -1;
        }
        return 0;
    }
}
