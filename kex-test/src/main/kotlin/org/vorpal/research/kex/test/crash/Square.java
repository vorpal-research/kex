package org.vorpal.research.kex.test.crash;

@SuppressWarnings("UnusedReturnValue")
public class Square {
    private final Point lowerLeft;
    private final Point upperRight;

    public Square(Point lowerLeft, Point upperRight) {
        this.lowerLeft = lowerLeft;
        this.upperRight = upperRight;
    }

    public Square move(UnsignedInteger step) {
        return new Square(lowerLeft.move(step), upperRight.move(step));
    }

    public Square shrink(UnsignedInteger step) {
        return new Square(lowerLeft, lowerLeft.shrunk(upperRight, step));
    }

    public boolean contained(int[] array) {
        return lowerLeft.getX().in(array) && upperRight.getY().in(array);
    }

    @Override
    public String toString() {
        return "[" + lowerLeft + ", " + upperRight + "]";
    }
}
