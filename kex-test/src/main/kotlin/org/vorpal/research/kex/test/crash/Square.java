package org.vorpal.research.kex.test.crash;

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
        UnsignedInteger newX = upperRight.getX().sub(lowerLeft.getX()).div(step);
        UnsignedInteger newY = upperRight.getY().sub(lowerLeft.getY()).div(step);
        return new Square(lowerLeft, new Point(newX, newY));
    }

    @Override
    public String toString() {
        return "[" + lowerLeft + ", " + upperRight + "]";
    }
}
