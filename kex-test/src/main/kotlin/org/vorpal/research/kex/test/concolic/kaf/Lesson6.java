package org.vorpal.research.kex.test.concolic.kaf;

import java.util.ArrayList;
import java.util.List;

public class Lesson6 {
    public static List<Integer> computeDeviceCells(
            int cells,
            String commands,
            int limit
    ) {
        if (cells < 1) return null;
        if (cells > 5) return null;
        if (commands == null) return null;

        List<Integer> cellsList = new ArrayList<>();
        for (int i = 0; i < cells; ++i) {
            cellsList.add(0);
        }

        if (commands.equals("")) return null;

        int numberOfCommand = 0;
        int numberOfCell = cells / 2;

        for (int i = 0; i <= limit; i++) {
            switch (commands.charAt(numberOfCommand)) {
                case '+': {
                    int current = cellsList.get(numberOfCell);
                    cellsList.set(numberOfCell, ++current);
                    break;
                }
                case '-': {
                    int current = cellsList.get(numberOfCell);
                    cellsList.set(numberOfCell, --current);
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
                    if (cellsList.get(numberOfCell) == 0)
                        numberOfCommand += 5;
                    break;
                }
                case ']': {
                    if (cellsList.get(numberOfCell) == 0)
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
