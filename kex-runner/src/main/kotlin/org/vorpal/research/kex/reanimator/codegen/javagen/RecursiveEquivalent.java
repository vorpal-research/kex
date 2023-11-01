package org.vorpal.research.kex.reanimator.codegen.javagen;

import java.util.*;;
import java.lang.reflect.Field;

public class RecursiveEquivalent {

    public static boolean equalsBoolean(boolean t1, boolean t2) {
        return t1 == t2;
    }

    public static boolean equalsByte(byte t1, byte t2) {
        return t1 == t2;
    }

    public static boolean equalsChar(char t1, char t2) {
        return t1 == t2;
    }

    public static boolean equalsShort(short t1, short t2) {
        return t1 == t2;
    }

    public static boolean equalsInt(int t1, int t2) {
        return t1 == t2;
    }

    public static boolean equalsLong(long t1, long t2) {
        return t1 == t2;
    }

    public static boolean equalsDouble(double t1, double t2) {
        return t1 == t2;
    }

    public static boolean equalsFloat(float t1, float t2) {
        return t1 == t2;
    }

    public static boolean equalsBooleanArray(boolean[] t1, boolean[] t2) {
        if (t1.length != t2.length) return false;
        for (int i=0; i<t1.length; i++) {
            if (t1[i] != t2[i]) return false;
        }

        return true;
    }

    public static boolean equalsByteArray(byte[] t1, byte[] t2) {
        if (t1.length != t2.length) return false;
        for (int i=0; i<t1.length; i++) {
            if (t1[i] != t2[i]) return false;
        }

        return true;
    }

    public static boolean equalsCharArray(char[] t1, char[] t2) {
        if (t1.length != t2.length) return false;
        for (int i=0; i<t1.length; i++) {
            if (t1[i] != t2[i]) return false;
        }

        return true;
    }

    public static boolean equalsShortArray(short[] t1, short[] t2) {
        if (t1.length != t2.length) return false;
        for (int i=0; i<t1.length; i++) {
            if (t1[i] != t2[i]) return false;
        }

        return true;
    }

    public static boolean equalsIntArray(int[] t1, int[] t2) {
        if (t1.length != t2.length) return false;
        for (int i=0; i<t1.length; i++) {
            if (t1[i] != t2[i]) return false;
        }

        return true;
    }

    public static boolean equalsLongArray(long[] t1, long[] t2) {
        if (t1.length != t2.length) return false;
        for (int i=0; i<t1.length; i++) {
            if (t1[i] != t2[i]) return false;
        }

        return true;
    }

    public static boolean equalsDoubleArray(double[] t1, double[] t2) {
        if (t1.length != t2.length) return false;
        for (int i=0; i<t1.length; i++) {
            if (t1[i] != t2[i]) return false;
        }

        return true;
    }

    public static boolean equalsFloatArray(float[] t1, float[] t2) {
        if (t1.length != t2.length) return false;
        for (int i=0; i<t1.length; i++) {
            if (t1[i] != t2[i]) return false;
        }

        return true;
    }

    public static boolean equalsObject(Object t1, Object t2, Map<Object, Object> visitedFirstToSecond, Map<Object, Object> visitedSecondToFirst) {
        if (visitedFirstToSecond.containsKey(t1) || visitedSecondToFirst.containsKey(t2)) {
            return visitedFirstToSecond.get(t1) == t2 && visitedSecondToFirst.get(t2) == t1;
        }

        visitedFirstToSecond.put(t1, t2);
        visitedSecondToFirst.put(t2, t1);
        Map<String, Object> t1Fields = getAllFields(t1.getClass(), t1);
        Map<String, Object> t2Fields = getAllFields(t2.getClass(), t2);
        for (Map.Entry<String, Object> entry : t1Fields.entrySet()) {
            if (!t2Fields.containsKey(entry.getKey())) return false;
            if (!equalsAll(entry.getValue(), t2Fields.get(entry.getKey()), visitedFirstToSecond, visitedSecondToFirst)) return false;
        }

        visitedFirstToSecond.remove(t1);
        visitedSecondToFirst.remove(t2);
        return true;
    }

    public static boolean equalsAll(Object t1, Object t2, Map<Object, Object> visitedFirstToSecond, Map<Object, Object> visitedSecondToFirst) {
        if (t1 == null && t2 == null) return true;
        if (t1 == null || t2 == null) return false;
        if (t1.getClass() != t2.getClass()) return false;
        Class clazz = t1.getClass();
        if (clazz == Boolean.class) {
            return equalsBoolean((Boolean) t1, (Boolean) t2);
        }

        if (clazz == Byte.class) {
            return equalsByte((Byte) t1, (Byte) t2);
        }

        if (clazz == Character.class) {
            return equalsChar((Character) t1, (Character) t2);
        }

        if (clazz == Short.class) {
            return equalsShort((Short) t1, (Short) t2);
        }

        if (clazz == Integer.class) {
            return equalsInt((Integer) t1, (Integer) t2);
        }

        if (clazz == Long.class) {
            return equalsLong((Long) t1, (Long) t2);
        }

        if (clazz == Double.class) {
            return equalsDouble((Double) t1, (Double) t2);
        }

        if (clazz == Float.class) {
            return equalsFloat((Float) t1, (Float) t2);
        }

        if (clazz == boolean[].class) {
            return equalsBooleanArray((boolean[]) t1, (boolean[]) t2);
        }

        if (clazz == byte[].class) {
            return equalsByteArray((byte[]) t1, (byte[]) t2);
        }

        if (clazz == char[].class) {
            return equalsCharArray((char[]) t1, (char[]) t2);
        }

        if (clazz == short[].class) {
            return equalsShortArray((short[]) t1, (short[]) t2);
        }

        if (clazz == int[].class) {
            return equalsIntArray((int[]) t1, (int[]) t2);
        }

        if (clazz == long[].class) {
            return equalsLongArray((long[]) t1, (long[]) t2);
        }

        if (clazz == double[].class) {
            return equalsDoubleArray((double[]) t1, (double[]) t2);
        }

        if (clazz == float[].class) {
            return equalsFloatArray((float[]) t1, (float[]) t2);
        }

        return equalsObject(t1, t2, visitedFirstToSecond, visitedSecondToFirst);
    }

    public static Map<String, Object> getAllFields(Class clazz, Object obj) {
        if (clazz == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new HashMap<>(getAllFields(clazz.getSuperclass(), obj));
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                result.put(field.getName(), field.get(obj));
            }
            catch (IllegalAccessException e) {
            }

        }

        return result;
    }

    public static boolean customEquals(Object t1, Object t2) {
        return equalsAll(t1, t2, new HashMap<>(), new HashMap<>());
    }

};


