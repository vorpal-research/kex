package jcc1;

import java.lang.Throwable;
import java.lang.IllegalStateException;
import org.junit.Test;
import jcc1.Adder;

public class Adder_sum_539242992 {

    public <T> T unknown() {
        throw new IllegalStateException();
    }

    @Test
    public  void testbb0() throws Throwable {
        Adder term398 = new Adder();
        int[] term397 = new int[0];
        term398.sum((int[])term397);
    }

    @Test
    public  void testlabelderoll0() throws Throwable {
        Adder term409 = new Adder();
        int[] term408 = new int[0];
        term409.sum((int[])term408);
    }

    @Test
    public  void testlabelderoll2() throws Throwable {
        Adder term420 = new Adder();
        int[] term419 = new int[2];
        term420.sum((int[])term419);
    }

    @Test
    public  void testlabelderoll3() throws Throwable {
        Adder term431 = new Adder();
        int[] term430 = new int[2];
        term431.sum((int[])term430);
    }

};


