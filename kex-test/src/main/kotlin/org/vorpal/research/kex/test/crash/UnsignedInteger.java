package org.vorpal.research.kex.test.crash;

public class UnsignedInteger {
    private long value = 0;

    public UnsignedInteger() {
        this.value = 0;
    }

    public UnsignedInteger(int value) {
        this.value = value;
    }

    private UnsignedInteger(long value) {
        this.value = value;
    }

    public UnsignedInteger add(UnsignedInteger other) {
        long newValue = value + other.value;
        assert newValue <= (long) Integer.MAX_VALUE;
        return new UnsignedInteger(newValue);
    }

    public UnsignedInteger sub(UnsignedInteger other) {
        long newValue = value - other.value;
        assert newValue >= 0;
        return new UnsignedInteger(newValue);
    }

    public UnsignedInteger mul(UnsignedInteger other) {
        long newValue = value * other.value;
        assert newValue <= (long) Integer.MAX_VALUE;
        return new UnsignedInteger(newValue);
    }

    public UnsignedInteger div(UnsignedInteger other) {
        if (other.value == 0L) {
            throw new ArithmeticException("Division by zero");
        }
        return new UnsignedInteger(value / other.value);
    }

    public UnsignedInteger sqrt() {
        return new UnsignedInteger((long) Math.sqrt((double) value));
    }

    public UnsignedInteger sqr() {
        return this.mul(this);
    }

    public int intValue() {
        return (int) value;
    }

    public long longValue() {
        return value;
    }

    public boolean in(int[] array) {
        int element = array[intValue()];
        int[] newArray = new int[element];
        return newArray.length < intValue();
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
