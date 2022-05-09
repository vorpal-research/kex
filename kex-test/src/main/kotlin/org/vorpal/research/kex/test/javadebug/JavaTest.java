package org.vorpal.research.kex.test.javadebug;

import java.util.List;

public class JavaTest {
    public static void testList(List<Point> points) {
        if (points.get(0).x == 10) {
            throw new IllegalStateException();
        }
    }
}