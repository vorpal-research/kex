package jcc1;

import java.lang.Throwable;
import java.lang.IllegalStateException;
import org.junit.Test;
import jcc1.Adder;

public class Adder_didYouCoverMe_1857167484 {

    public <T> T unknown() {
        throw new IllegalStateException();
    }

    @Test
    public  void testbb0() throws Throwable {
        Adder term496 = new Adder();
        term496.didYouCoverMe();
    }

};


