package com.go;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Represents a specific go puzzle. 
 * Supports undo/redo of board state and solution index.
 */
public class Level {
    private Board board;
    /** Ordered steps: at each step the player may play any of the allowed moves; then the bot plays the fixed reply. */
    private List<SolutionStep> steps;
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

    public Level(Board board, List<SolutionStep> steps) {
        this.board = board;
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
        this.current = 0;
    }

    public Level(Board board, List<SolutionStep> steps, List<int[]> initialWhiteStone, List<int[]> initialBlackStones) {
        this.board = board;
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
        this.current = 0;

        board.setIntialStones(initialWhiteStone, initialBlackStones);
    }

    /**
     * Checks if the move matches one of the allowed moves for the current step. If so, plays it
     * and then the bot's reply (if any). Otherwise resets the puzzle.
     */
    public boolean playMove(int x, int y, boolean isWhite){
        if (isSolved()) {
            return true;
        }

        if (steps == null || steps.isEmpty() || current < 0 || current >= steps.size()) {
            reset();
            return false;
        }

        SolutionStep step = steps.get(current);
        if (!isAllowedPlayerMove(step, x, y)) {
            reset();
            return false;
        }

        saveState();
        boolean playerMoveApplied = board.play(x, y, isWhite);
        if (!playerMoveApplied) {
            popUndoStack();
            reset();
            return false;
        }

        int[] opponentMove = step.opponentMove();
        if (opponentMove != null && opponentMove.length >= 2) {
            if (!board.play(opponentMove[0], opponentMove[1], !isWhite)) {
                undo();
                return false;
            }
        }

        current++;
        clearRedo();
        return true;
    }

    private boolean isAllowedPlayerMove(SolutionStep step, int x, int y) {
        for (int[] move : step.playerMoves()) {
            if (move != null && move.length >= 2 && move[0] == x && move[1] == y) {
                return true;
            }
        }
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
        return current >= steps.size();
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
