package org.jetbrains.research.kex.test.debug;

class JavaDoublePoint {
    private double x;
    private double y;

    public JavaDoublePoint() {
        this.x = 0.0;
        this.y = 0.0;
    }

    public JavaDoublePoint(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double compute(double d) {
        return x + y + d;
    }
}