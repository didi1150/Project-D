package dev.core.minigame;

import java.util.List;

/**
 * Platform-neutral 3x3 Tic Tac Toe board. Turn ownership is managed by the
 * caller; this class only enforces legal cells and evaluates wins/draws.
 */
public final class TicTacToeBoard {

    public static final int SIZE = 9;

    private static final int[][] WIN_LINES = {
            { 0, 1, 2 }, { 3, 4, 5 }, { 6, 7, 8 },
            { 0, 3, 6 }, { 1, 4, 7 }, { 2, 5, 8 },
            { 0, 4, 8 }, { 2, 4, 6 }
    };

    private final Mark[] cells = new Mark[SIZE];

    public Mark get(int index) {
        if (index < 0 || index >= SIZE) {
            return null;
        }
        return cells[index];
    }

    public boolean isEmpty(int index) {
        return get(index) == null;
    }

    public boolean isLegalMove(int index) {
        return index >= 0 && index < SIZE && cells[index] == null && winner() == null;
    }

    public boolean place(int index, Mark mark) {
        if (mark == null || !isLegalMove(index)) {
            return false;
        }
        cells[index] = mark;
        return true;
    }

    public Mark winner() {
        for (int[] line : WIN_LINES) {
            Mark first = cells[line[0]];
            if (first != null && first == cells[line[1]] && first == cells[line[2]]) {
                return first;
            }
        }
        return null;
    }

    public List<Integer> winningLine() {
        for (int[] line : WIN_LINES) {
            Mark first = cells[line[0]];
            if (first != null && first == cells[line[1]] && first == cells[line[2]]) {
                return List.of(line[0], line[1], line[2]);
            }
        }
        return List.of();
    }

    public boolean isFull() {
        for (Mark cell : cells) {
            if (cell == null) {
                return false;
            }
        }
        return true;
    }

    public boolean isGameOver() {
        return winner() != null || isFull();
    }

    public void clear() {
        for (int i = 0; i < cells.length; i++) {
            cells[i] = null;
        }
    }

    public enum Mark {
        RED, BLUE
    }
}
