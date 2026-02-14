package com.go;
import java.util.List;
/**
 * Represents a specific go puzzle. 
 *
 * @author AI
 * @version 1.0
 */
public class Level {
    private Board board;
    private List<int[]> solution; //lists ordered set of moves to solve the puzzle, in format x,y, isWhite
    private int current;

    public Level(Board board, List<int[]> solution) {
        this.board = board;
        this.solution = solution;
        this.current = 0; 
    }

    public Level(Board board, List<int[]> solution, List<int[]> initialWhiteStone, List<int[]> initialBlackStones) {
        this.board = board;
        this.solution = solution;
        this.current = 0; 

        board.setIntialStones(initialWhiteStone, initialBlackStones);

    }

    /**
     * Checks if current move is the correct move. If so, it can be played, else, level resets.
     * Plays move for current color, and subsequent follow up move for next.
     * 
     * @param x
     * @param y
     * @param isWhite
     * @return
     */
    public boolean playMove(int x, int y, boolean isWhite){
        if (isSolved()) {
            // Ignore extra clicks after completion to avoid indexing past solution.
            return true;
        }

        if (solution == null || solution.isEmpty() || current < 0 || current >= solution.size()) {
            reset();
            return false;
        }

        int[] expectedMove = solution.get(current);
        if (expectedMove == null || expectedMove.length < 2) {
            reset();
            return false;
        }

        if(x == expectedMove[0] && y == expectedMove[1]){
            boolean playerMoveApplied = board.play(x, y, isWhite);
            if (!playerMoveApplied) {
                reset();
                return false;
            }
            current ++;

            if (current < solution.size()) {
                int[] opponentMove = solution.get(current);
                if (opponentMove == null || opponentMove.length < 2 || !board.play(opponentMove[0], opponentMove[1], !isWhite)) {
                    reset();
                    return false;
                }
                current ++;
            }

            if(isSolved())
                System.out.println("SOLVED"); 

            return true;
        }

        //reset the level. 
        reset();

        return false;
    }

    public void reset() {
        board.restart();
        current = 0; 
    }

    public void print(){
        board.print();
    }

    public boolean isSolved(){
        return current >= solution.size();
    }

    public int getStoneAt(int x, int y){
        return board.getStoneAt(x, y);
    }

}
