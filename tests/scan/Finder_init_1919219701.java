package scan;

import java.lang.Throwable;
import java.lang.IllegalStateException;
import org.junit.Test;
import scan.Finder;

public class Finder_init_1919219701 {

    public <T> T unknown() {
        throw new IllegalStateException();
    }

    @Test
    public  void testbb0() throws Throwable {
        Finder term396 = new Finder();
    }

};


