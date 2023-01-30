package org.vorpal.research.kex.test.crash;

public class CrashTrigger {

    public void foo(
            UnsignedInteger lowerX,
            UnsignedInteger lowerY,
            UnsignedInteger upperX,
            UnsignedInteger upperY,
            Move move,
            UnsignedInteger param
    ) {
        Point ll = new Point(lowerX, lowerY);
        Point ur = new Point(upperX, upperY);
        Square sq = new Square(ll, ur);
        if (move == Move.MOVE) {
            sq = sq.move(param);
        } else if (move == Move.SHRINK) {
            sq = sq.shrink(param);
        }
    }

    public void triggerNullPtr() {
        foo(
                new UnsignedInteger(0),
                new UnsignedInteger(0),
                new UnsignedInteger(2_000_000_000),
                new UnsignedInteger(2_000_000_000),
                Move.MOVE,
                null
        );
    }

    public void triggerAssert() {
        foo(
                new UnsignedInteger(1_000_000_000),
                new UnsignedInteger(1_000_000_000),
                new UnsignedInteger(2_000_000_000),
                new UnsignedInteger(2_000_000_000),
                Move.MOVE,
                new UnsignedInteger(1_000_000_000)
        );
    }

    public void triggerException() {
        foo(
                new UnsignedInteger(1_000_000_000),
                new UnsignedInteger(1_000_000_000),
                new UnsignedInteger(2_000_000_000),
                new UnsignedInteger(2_000_000_000),
                Move.SHRINK,
                new UnsignedInteger(0)
        );
    }
}
