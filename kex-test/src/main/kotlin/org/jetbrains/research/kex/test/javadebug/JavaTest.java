package org.jetbrains.research.kex.test.javadebug;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public abstract class JavaTest {

    public enum Testter {
        A(1),
        B(2),
        C(3);

        int a;
        private Testter(int v) {
            this.a = v;
        }
    }


    public void test(Field field) throws Throwable {
        Field mods = Field.class.getDeclaredField("modifiers");
        mods.setAccessible(true);
        int modifiers = mods.getInt(field);
        mods.setInt(field, modifiers & ~Modifier.FINAL);
    }

    public static void test(Testter t) {
        if (t == Testter.A) {
            System.out.println("a");
        }
    }
}