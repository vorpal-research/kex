package org.vorpal.research.kex.test.nullability;

import java.util.List;

public class ListNulls {
    int function(List<Integer> x) {
        if (x == null) {
            return -1;
        }
        for (int i = 0; i < x.size(); i++) {
            if (x.get(i) == null) {
                return i;
            }
        }
        return x.size();
    }
}
