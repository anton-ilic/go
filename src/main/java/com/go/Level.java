package com.go;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Represents a specific go puzzle. 
 * Supports undo/redo of board state and solution index.
 */
public class Level {
    private Board board;
    private List<int[]> solution; //lists ordered set of moves to solve the puzzle, in format x,y, isWhite
    private int current;

    private static final class UndoEntry {
        final BoardStateSnapshot state;
        final int solutionIndex;

        UndoEntry(BoardStateSnapshot state, int solutionIndex) {
            this.state = state;
            this.solutionIndex = solutionIndex;
        }
    }

    private final Deque<UndoEntry> undoStack = new ArrayDeque<>();
    private final Deque<UndoEntry> redoStack = new ArrayDeque<>();

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
            saveState();
            boolean playerMoveApplied = board.play(x, y, isWhite);
            if (!playerMoveApplied) {
                popUndoStack();
                reset();
                return false;
            }
            current ++;

            if (current < solution.size()) {
                int[] opponentMove = solution.get(current);
                if (opponentMove == null || opponentMove.length < 2 || !board.play(opponentMove[0], opponentMove[1], !isWhite)) {
                    undo(); // revert the player move we just applied
                    return false;
                }
                current ++;
            }

            clearRedo();
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

    private void saveState() {
        undoStack.addLast(new UndoEntry(board.createStateSnapshot(), current));
    }

    private void popUndoStack() {
        if (!undoStack.isEmpty()) {
            undoStack.removeLast();
        }
    }

    private void clearRedo() {
        redoStack.clear();
    }

    /** Undo the last correct move (and any opponent reply). Returns true if undo was performed. */
    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        UndoEntry entry = undoStack.removeLast();
        redoStack.addLast(new UndoEntry(board.createStateSnapshot(), current));
        board.restoreState(entry.state);
        current = entry.solutionIndex;
        return true;
    }

    /** Redo a previously undone move sequence. Returns true if redo was performed. */
    public boolean redo() {
        if (redoStack.isEmpty()) return false;
        UndoEntry entry = redoStack.removeLast();
        undoStack.addLast(new UndoEntry(board.createStateSnapshot(), current));
        board.restoreState(entry.state);
        current = entry.solutionIndex;
        return true;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }
}
