package org.vorpal.research.kex.reanimator.codegen.javagen;

public class Main {

    public static void main(String[] args) {

        RecursiveEquivalentTestSuit.constCompare();
        RecursiveEquivalentTestSuit.testDifferentClasses();
        RecursiveEquivalentTestSuit.nullTest();
        RecursiveEquivalentTestSuit.circlesTest1();
        RecursiveEquivalentTestSuit.circlesTest2();
        RecursiveEquivalentTestSuit.repeatsTest();
    }
}



class RecursiveEquivalentTestSuit {

    static void customAssert(boolean cond) {
        if (!cond) throw new IllegalStateException();
    }

    public static void constCompare() {
        customAssert(RecursiveEquivalent.customEquals(17, 17));
        customAssert(!RecursiveEquivalent.customEquals(17, 13));
        Integer i1 = 10, i2 = 10, i3 = 13;
        customAssert(RecursiveEquivalent.customEquals(i1, i2));
        customAssert(!RecursiveEquivalent.customEquals(i1, i3));
        String s1 = "I am string!", s2 = "I am string!", s3 = "I am also string!";
        customAssert(RecursiveEquivalent.customEquals(s1, s2));
        customAssert(!RecursiveEquivalent.customEquals(s1, s3));
    }

    static class SimpleClassWithData {
        int data = 10;
    }
    static class SimpleClassWithDataAccessor extends SimpleClassWithData {
        int data2 = 15;
    }

    static class SimpleClassSecondAccessor extends SimpleClassWithDataAccessor { }
    static class SimpleSimilarClassWithData {
        int data = 10;
    }

    public static void testDifferentClasses() {
        customAssert(!RecursiveEquivalent.customEquals(new SimpleClassWithData(), new SimpleSimilarClassWithData()));
        customAssert(!RecursiveEquivalent.customEquals(new SimpleClassWithData(), new SimpleClassWithDataAccessor()));
        customAssert(RecursiveEquivalent.customEquals(new SimpleClassSecondAccessor(), new SimpleClassSecondAccessor()));
    }

    public static class ClassWithRefToThemselves {
        int intData = 10;
        String stringData = "I am just Data!";
        ClassWithRefToThemselves ref1 = null;
        ClassWithRefToThemselves ref2 = null;

        public ClassWithRefToThemselves() {}

        public ClassWithRefToThemselves(ClassWithRefToThemselves refData) {
            ref1 = refData;
        }

        public ClassWithRefToThemselves(ClassWithRefToThemselves refData1, ClassWithRefToThemselves refData2) {
            ref1 = refData1;
            ref2 = refData2;
        }

        public ClassWithRefToThemselves(int iData, String sData, ClassWithRefToThemselves refData1,
                                        ClassWithRefToThemselves refData2) {
            intData = iData;
            stringData = sData;
            ref1 = refData1;
            ref2 = refData2;
        }
    }

    public static void nullTest() {
        ClassWithRefToThemselves t1 = new ClassWithRefToThemselves();
        customAssert(RecursiveEquivalent.customEquals(null, null));
        customAssert(!RecursiveEquivalent.customEquals(null, t1));

        ClassWithRefToThemselves t2 = new ClassWithRefToThemselves(t1);
        customAssert(!RecursiveEquivalent.customEquals(t1, t2));
    }

    public static void circlesTest1() {
        ClassWithRefToThemselves t11 = new ClassWithRefToThemselves();
        t11.ref1 = t11;

        ClassWithRefToThemselves t21 = new ClassWithRefToThemselves();
        ClassWithRefToThemselves t22 = new ClassWithRefToThemselves(t21);
        t21.ref1 = t22;

        customAssert(!RecursiveEquivalent.customEquals(t11, t21));
        ClassWithRefToThemselves t12 = new ClassWithRefToThemselves(t11);
        t11.ref1 = t12;

        customAssert(RecursiveEquivalent.customEquals(t11, t21));
    }

    public static void circlesTest2() {
        ClassWithRefToThemselves t1 = new ClassWithRefToThemselves();
        ClassWithRefToThemselves t2 = new ClassWithRefToThemselves(t1);
        t1.ref1 = t2;

        customAssert(RecursiveEquivalent.customEquals(t1, t2));
        ClassWithRefToThemselves t3 = new ClassWithRefToThemselves();

        t1.ref1 = t2;
        t1.ref2 = t3;
        t2.ref1 = t3;
        t2.ref2 = t1;
        t3.ref1 = t1;
        t3.ref2 = t2;
        customAssert(RecursiveEquivalent.customEquals(t1, t2));

        t2.ref1 = t1;
        t2.ref2 = t3;
        t3.ref1 = t3;
        t3.ref2 = t3;
        customAssert(RecursiveEquivalent.customEquals(t1, t2));

        t3.ref1 = t2;
        t3.ref2 = t2;
        customAssert(!RecursiveEquivalent.customEquals(t1, t2));
    }

    public static void repeatsTest() {
        ClassWithRefToThemselves t1 = new ClassWithRefToThemselves();
        ClassWithRefToThemselves t2 = new ClassWithRefToThemselves();
        ClassWithRefToThemselves t3 = new ClassWithRefToThemselves();

        ClassWithRefToThemselves a1 = new ClassWithRefToThemselves(t1, t1);
        ClassWithRefToThemselves a2 = new ClassWithRefToThemselves(t2, t3);

        customAssert(RecursiveEquivalent.customEquals(a1, a2));
    }

}



