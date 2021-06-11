package jcc1;

import java.lang.Throwable;
import java.lang.IllegalStateException;
import org.junit.Test;
import jcc1.Multiplexer;

public class Multiplexer_introduce_525471621 {

    public <T> T unknown() {
        throw new IllegalStateException();
    }

    @Test
    public  void testbb3() throws Throwable {
        Multiplexer term624 = new Multiplexer();
        term624.introduce();
    }

};


