package jcc1;

import java.lang.Throwable;
import java.lang.IllegalStateException;
import org.junit.Test;
import jcc1.Adder;

public class Adder_didYouCoverMe_1857067664 {

    public <T> T unknown() {
        throw new IllegalStateException();
    }

    @Test
    public  void testentry0() throws Throwable {
        Adder term492 = new Adder();
        term492.didYouCoverMe(false);
    }

    @Test
    public  void testifelse0() throws Throwable {
        Adder term504 = new Adder();
        term504.didYouCoverMe(true);
    }

};


