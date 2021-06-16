package jcc1;

public class Multiplexer {

    public int multiplyElements(int[] a) {
        int result = 1;
        for (int j : a) {
            result *= j;
        }
        return result;
    }

    public void multiplyConst(int c, int[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] *= c;
        }
    }

    public void introduce() {
        System.out.printf("Hello, I am %s.%n", getClass());
    }
}
