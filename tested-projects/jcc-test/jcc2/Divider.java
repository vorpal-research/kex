package jcc2;

public class Divider {

    public double[] divideConst(int c, int[] a) {
        int length = a.length;
        double[] b = new double[length];
        for (int i = 0; i < length; i++) {
            b[i] = (double) a[i] / c;
        }
        return b;
    }

    public void intDivideConst(int c, int[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] /= c;
        }
    }

    public void introduce() {
        System.out.printf("Hello, I am %s.%n", getClass());
    }
}
