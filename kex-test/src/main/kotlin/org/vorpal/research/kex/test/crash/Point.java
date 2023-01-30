package org.vorpal.research.kex.test.crash;

public class Point {
    private UnsignedInteger x;
    private UnsignedInteger y;

    public Point(UnsignedInteger x, UnsignedInteger y) {
        this.x = x;
        this.y = y;
    }

    public Point() {
        this(new UnsignedInteger(0), new UnsignedInteger(0));
    }

    public UnsignedInteger getX() {
        return x;
    }

    public UnsignedInteger getY() {
        return y;
    }

    public UnsignedInteger dist(Point other) {
        UnsignedInteger xDiff = x.sqr().sub(other.getX().sqr());
        UnsignedInteger yDiff = y.sqr().sub(other.getY().sqr());
        return xDiff.add(yDiff).sqrt();
    }

    public Point move(UnsignedInteger step) {
        return new Point(x.add(step), y.add(step));
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
