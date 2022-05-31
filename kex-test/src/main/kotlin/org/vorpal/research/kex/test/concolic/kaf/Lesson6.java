package org.vorpal.research.kex.test.concolic.kaf;

public class Lesson6 {
    public static int[] computeDeviceCells(
            int cells,
            String commands,
            int limit
    ) {
        if (cells < 1) return null;
        if (cells > 5) return null;
        if (commands == null) return null;
        if (commands.length() > 10) return null;
        if (limit > 10) return null;

        int[] cellsList = new int[limit];

        if (commands.equals("")) return null;

        int numberOfCommand = 0;
        int numberOfCell = cells / 2;

        for (int i = 0; i <= limit; i++) {
            switch (commands.charAt(numberOfCommand)) {
                case '+': {
                    int current = cellsList[numberOfCell];
                    cellsList[numberOfCell] = ++current;
                    break;
                }
                case '-': {
                    int current = cellsList[numberOfCell];
                    cellsList[numberOfCell] = --current;
                    break;
                }
                case '>': {
                    numberOfCell++;
                    break;
                }
                case '<': {
                    numberOfCell--;
                    break;
                }
                case '[': {
                    if (cellsList[numberOfCell] == 0)
                        numberOfCommand += 5;
                    break;
                }
                case ']': {
                    if (cellsList[numberOfCell] == 0)
                        numberOfCommand -= 5;
                    break;
                }
            }
            numberOfCommand++;
            if (numberOfCell >= cells || numberOfCell < 0)
                throw new IllegalStateException("The maximum value has been reached");
            if (numberOfCommand >= commands.length()) break;
        }

        return cellsList;
    }
}
