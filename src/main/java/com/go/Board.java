package com.go;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Represents the go board.
 *
 * @author AI
 * @version 1.0
 */
public class Board {
    /**
     * Maps 0 to empty space, 1 maps to white, 2 maps to black.
     * in format [x, y] where 0,0 is the bottom left corner.
     * 
     */
    private int[][] layout;
    private int[][] previous_layout; //layout saving previous board state (prior to move)
    private int[][] previous_layout_temp; //copy layout

    private List<int[]> initialWhite;
    private List<int[]> initialBlack;
    public static final int WHITE = 1;
    public static final int BLACK = 2;
    public static final int EMPTY = 0;
    /** Default size when not specified (e.g. puzzles). */
    public static final int DEFAULT_BOARD_SIZE = 11;
    /** Supported sizes for online play. */
    public static final int[] SUPPORTED_SIZES = { 9, 11, 19 };

    private final int boardSize;
    private boolean moved;
    private boolean toggle = false; // toggle value, true: White, false: Black
    private int blackPrisoners; // white stones captured by black
    private int whitePrisoners; // black stones captured by white

    private final Deque<BoardStateSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<BoardStateSnapshot> redoStack = new ArrayDeque<>();

    public Board() {
        this(DEFAULT_BOARD_SIZE);
    }

    /** Creates a board of the given size (e.g. 9, 11, 19). */
    public Board(int boardSize) {
        this.boardSize = boardSize;
        this.layout = new int[boardSize][boardSize];
        this.previous_layout = new int[boardSize][boardSize];
        this.previous_layout_temp = new int[boardSize][boardSize];
        this.initialWhite = new ArrayList<>();
        this.initialBlack = new ArrayList<>();
        this.moved = false;
        this.blackPrisoners = 0;
        this.whitePrisoners = 0;
        emptyBoard();
    }

    public int getBoardSize() {
        return boardSize;
    }

    public void setIntialStones(List<int[]> whiteStones, List<int[]> blackStone) {
        this.initialBlack = blackStone;
        this.initialWhite = whiteStones;
        restart();
    }

    private void emptyBoard() {
        moved = false;
        toggle = false;
        blackPrisoners = 0;
        whitePrisoners = 0;
        emptyLayout(layout);
        emptyLayout(previous_layout);
        emptyLayout(previous_layout_temp);
    }

    private void emptyLayout(int[][] current){
        for (int i = 0; i < boardSize; i++) {
            for (int j = 0; j < boardSize; j++) {
                current[i][j] = EMPTY;
            }
        }
    }

    public void restart() {
        emptyBoard();

        for (int[] stone : initialWhite) {
            layout[stone[0]][stone[1]] = WHITE;
        }

        for (int[] stone : initialBlack) {
            layout[stone[0]][stone[1]] = BLACK;
        }
    }

    public int getStoneAt(int x, int y){
        return layout[x][y];
    }

    public boolean play(int x, int y, boolean isWhite) {
        if (layout[x][y] != EMPTY) {
            return false;
        }

        copy_layout(previous_layout_temp, layout);

        if (isWhite) {
            layout[x][y] = WHITE;
        } else {
            layout[x][y] = BLACK;
        }


        // Capture opponent groups and count prisoners.
        int capturedStones = 0;
        for (int[] neighbor : getNeighbors(x, y)) {
            int nx = neighbor[0], ny = neighbor[1];
            if (nx >= 0 && nx < boardSize && ny >= 0 && ny < boardSize) {
                if (layout[nx][ny] != EMPTY && layout[nx][ny] != layout[x][y]) {
                    capturedStones += captureGroup(nx, ny);
                }
            }
        }

        if (ko_violation() && moved ){ //checking LAYOUT vs STATE
            layout[x][y] = EMPTY;
            copy_layout(layout, previous_layout_temp);
            System.out.println("LAYOUT VIOLATION");
            return false;
        }

        // invalid move
        if (!hasLiberties(x, y)) {
            layout[x][y] = EMPTY;
            return false;
        }

        if (capturedStones > 0) {
            if (isWhite) {
                whitePrisoners += capturedStones;
            } else {
                blackPrisoners += capturedStones;
            }
        }

        copy_layout(previous_layout, previous_layout_temp);
        moved = true;
        return true;
    }

    public int getBlackPrisoners() {
        return blackPrisoners;
    }

    public int getWhitePrisoners() {
        return whitePrisoners;
    }

    /** Creates a snapshot of the current board state (for undo/redo). */
    public BoardStateSnapshot createStateSnapshot() {
        return new BoardStateSnapshot(
                layout,
                previous_layout,
                blackPrisoners,
                whitePrisoners,
                toggle
        );
    }

    /** Saves current state to undo stack. Call before applying a move. Returns the snapshot that was pushed. */
    public BoardStateSnapshot saveState() {
        BoardStateSnapshot s = createStateSnapshot();
        undoStack.addLast(s);
        return s;
    }

    /** Restores board from a snapshot (used by undo/redo). */
    public void restoreState(BoardStateSnapshot s) {
        copy_layout(layout, s.getLayout());
        copy_layout(previous_layout, s.getPreviousLayout());
        copy_layout(previous_layout_temp, s.getPreviousLayout());
        blackPrisoners = s.getBlackPrisoners();
        whitePrisoners = s.getWhitePrisoners();
        toggle = s.isTurnIsWhite();
        moved = true; // so ko check has a previous state
    }

    /** Undoes one move. Returns true if undo was performed. */
    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        BoardStateSnapshot current = createStateSnapshot();
        redoStack.addLast(current);
        BoardStateSnapshot prev = undoStack.removeLast();
        restoreState(prev);
        return true;
    }

    /** Redoes one move. Returns true if redo was performed. */
    public boolean redo() {
        if (redoStack.isEmpty()) return false;
        BoardStateSnapshot current = createStateSnapshot();
        undoStack.addLast(current);
        BoardStateSnapshot next = redoStack.removeLast();
        restoreState(next);
        return true;
    }

    /** Clears the redo stack. Call after a new move. */
    public void clearRedo() {
        redoStack.clear();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /** Removes the top of the undo stack without pushing current to redo (e.g. after an illegal move). */
    public void popUndoStack() {
        if (!undoStack.isEmpty()) {
            undoStack.removeLast();
        }
    }

    private boolean ko_violation(){
        for (int i = 0; i < boardSize; i++) {
            for (int j = 0; j < boardSize; j++) {
                if (previous_layout[i][j] != layout[i][j]){
                    return false;
                }
            }
        }
        return true;
    }

    private void copy_layout( int[][] victim,  int[][] target){
        for (int i = 0; i < boardSize; i++) {
            for (int j = 0; j < boardSize; j++) {
                victim[i][j] = target[i][j];
            }
        }
    }


    public boolean play(int x, int y){
        boolean returnValue = play(x, y, toggle);
        if(returnValue)
            toggle = !toggle;
        return returnValue;
    }

    public void print() {
        for (int i = boardSize - 1; i >= 0; i--) {
            for (int j = 0; j < boardSize; j++) {

                if (layout[j][i] == WHITE) {
                    System.out.print("W ");
                } else if (layout[j][i] == BLACK) {
                    System.out.print("B ");
                } else {
                    System.out.print("* ");
                }
            }
            System.out.println();
        }
    }

    private boolean hasLiberties(int x, int y) {
        int color = layout[x][y];
        boolean[][] visited = new boolean[boardSize][boardSize];
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[] { x, y });
        visited[x][y] = true;

        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int px = pos[0];
            int py = pos[1];

            // check neibours
            for (int[] neighbor : getNeighbors(px, py)) {
                int nx = neighbor[0], ny = neighbor[1];

                if (layout[nx][ny] == EMPTY) {
                    return true;
                }

                if (layout[nx][ny] == color && !visited[nx][ny]) {
                    queue.add(new int[] { nx, ny });
                    visited[nx][ny] = true;
                }
            }
        }

        return false;
    }

    private List<int[]> getNeighbors(int x, int y) {
        List<int[]> neighbors = new ArrayList<>();
        if (x - 1 >= 0) {
            neighbors.add(new int[] { x - 1, y });
        }

        if (x + 1 < boardSize) {
            neighbors.add(new int[] { x + 1, y });
        }

        if (y - 1 >= 0) {
            neighbors.add(new int[] { x, y - 1 });
        }

        if (y + 1 < boardSize) {
            neighbors.add(new int[] { x, y + 1 });
        }

        return neighbors;
    }

    private int captureGroup(int x, int y) {
        int color = layout[x][y];
        boolean[][] visited = new boolean[boardSize][boardSize];
        Queue<int[]> queue = new LinkedList<>(); // store FIFO nodes
        queue.add(new int[] { x, y });
        visited[x][y] = true;

        // Collect all stones in the group
        Set<int[]> group = new HashSet<>();
        group.add(new int[] { x, y });

        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int px = pos[0], py = pos[1];

            for (int[] neighbor : getNeighbors(px, py)) {
                int nx = neighbor[0], ny = neighbor[1];
                if (nx < 0 || nx >= boardSize || ny < 0 || ny >= boardSize) {
                    continue;
                }

                if (layout[nx][ny] == color && !visited[nx][ny]) {
                    queue.add(new int[] { nx, ny });
                    visited[nx][ny] = true;
                    group.add(new int[] { nx, ny });
                }
            }
        }

        // Remove the group if no liberties
        if (!hasLiberties(x, y)) {
            int removed = 0;
            for (int[] stone : group) {
                layout[stone[0]][stone[1]] = EMPTY;
                removed++;
            }
            return removed;
        }
        return 0;
    }
}
