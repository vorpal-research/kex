package jcc1;

import java.lang.Throwable;
import java.lang.IllegalStateException;
import org.junit.Test;
import jcc1.Adder;

public class Adder_introduce_180228044 {

    public <T> T unknown() {
        throw new IllegalStateException();
    }

    @Test
    public  void testbb3() throws Throwable {
        Adder term520 = new Adder();
        term520.introduce();
    }

};


