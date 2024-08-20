package org.vorpal.research.kex.test.nullability;

public class BoxedTypeNulls {
    int function(Integer x) {
        if (x == null) {
            return 1;
        }
        return 2;
    }
}
