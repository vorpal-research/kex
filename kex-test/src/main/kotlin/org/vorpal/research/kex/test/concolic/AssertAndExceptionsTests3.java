package org.vorpal.research.kex.test.concolic;

public class AssertAndExceptionsTests3 {

    int innerValue1 = 10;
    Point innerValue2 = new Point (10, 10);

    public void argumentChange(Point arg) {
        arg.x = arg.x + 10;
        arg.y = arg.y - 10;
    }

    public void constInnerValChange(int x) {
        innerValue1 = innerValue1 + x;
    }

    public void innerValChange(Point point) {
        innerValue2.x = innerValue2.x + point.x;
        innerValue2.y = innerValue2.y + point.y;
    }
}
