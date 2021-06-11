package jcc1;

import java.lang.Throwable;
import java.lang.IllegalStateException;
import org.junit.Test;
import jcc1.Adder;

public class Adder_addConst_2077548648 {

    public <T> T unknown() {
        throw new IllegalStateException();
    }

    @Test
    public  void testlabelderoll0() throws Throwable {
        Adder term450 = new Adder();
        int[] term449 = new int[0];
        term450.addConst(0, (int[])term449);
    }

    @Test
    public  void testbbderoll0() throws Throwable {
        Adder term461 = new Adder();
        int[] term460 = new int[0];
        term461.addConst(0, (int[])term460);
    }

    @Test
    public  void testlabelderoll1() throws Throwable {
        Adder term472 = new Adder();
        int[] term471 = new int[2];
        term471[0] = 0;
        term472.addConst(0, (int[])term471);
    }

    @Test
    public  void testbbderoll3() throws Throwable {
        Adder term483 = new Adder();
        int[] term482 = new int[2];
        term482[0] = 0;
        term483.addConst(0, (int[])term482);
    }

};


