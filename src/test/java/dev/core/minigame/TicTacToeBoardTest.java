package dev.core.minigame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.core.minigame.TicTacToeBoard.Mark;

class TicTacToeBoardTest {

    @Test
    void detectsRowWin() {
        TicTacToeBoard board = new TicTacToeBoard();
        board.place(0, Mark.RED);
        board.place(1, Mark.RED);
        board.place(2, Mark.RED);

        assertEquals(Mark.RED, board.winner());
        assertEquals(3, board.winningLine().size());
    }

    @Test
    void detectsColumnAndDiagonalWins() {
        TicTacToeBoard column = new TicTacToeBoard();
        column.place(1, Mark.BLUE);
        column.place(4, Mark.BLUE);
        column.place(7, Mark.BLUE);
        assertEquals(Mark.BLUE, column.winner());

        TicTacToeBoard diagonal = new TicTacToeBoard();
        diagonal.place(0, Mark.RED);
        diagonal.place(4, Mark.RED);
        diagonal.place(8, Mark.RED);
        assertEquals(Mark.RED, diagonal.winner());
    }

    @Test
    void fullBoardWithoutLineIsDraw() {
        TicTacToeBoard board = new TicTacToeBoard();
        int[] moves = { 0, 1, 2, 4, 3, 5, 7, 6, 8 };
        Mark[] marks = { Mark.RED, Mark.BLUE, Mark.RED, Mark.BLUE, Mark.RED, Mark.BLUE, Mark.RED, Mark.BLUE,
                Mark.RED };
        for (int i = 0; i < moves.length; i++) {
            assertTrue(board.place(moves[i], marks[i]));
        }

        assertTrue(board.isFull());
        assertTrue(board.isGameOver());
        assertNull(board.winner());
        assertTrue(board.winningLine().isEmpty());
    }

    @Test
    void occupiedCellAndMovesAfterWinAreRejected() {
        TicTacToeBoard board = new TicTacToeBoard();
        assertTrue(board.place(4, Mark.RED));
        assertFalse(board.place(4, Mark.BLUE));

        board.place(0, Mark.RED);
        board.place(1, Mark.RED);
        board.place(2, Mark.RED);
        assertEquals(Mark.RED, board.winner());
        assertFalse(board.place(3, Mark.RED));
    }
}
