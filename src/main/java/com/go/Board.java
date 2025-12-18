package com.go;

import java.util.ArrayList;
import java.util.Arrays;
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
    public static final int BOARD_SIZE = 11;
    private boolean moved;
    private boolean toggle = true; //toggle value, true: White, false: Black

    public Board() {
        this.layout = new int[BOARD_SIZE][BOARD_SIZE];
        this.previous_layout = new int[BOARD_SIZE][BOARD_SIZE];
        this.previous_layout_temp = new int[BOARD_SIZE][BOARD_SIZE];
        this.initialWhite = new ArrayList<>();
        this.initialBlack = new ArrayList<>();
        this.moved = false;
        emptyBoard();
    }

    public void setIntialStones(List<int[]> whiteStones, List<int[]> blackStone) {
        this.initialBlack = blackStone;
        this.initialWhite = whiteStones;
        restart();
    }

    private void emptyBoard() {
        moved = false;
        emptyLayout(layout);
        emptyLayout(previous_layout);
    }

    private void emptyLayout(int[][] current){
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
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


        // Capture opponent groups.
        for (int[] neighbor : getNeighbors(x, y)) {
            int nx = neighbor[0], ny = neighbor[1];
            if (nx >= 0 && nx < BOARD_SIZE && ny >= 0 && ny < BOARD_SIZE) {
                if (layout[nx][ny] != EMPTY && layout[nx][ny] != layout[x][y]) {
                    captureGroup(nx, ny);
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

        copy_layout(previous_layout, previous_layout_temp);
        moved = true;
        return true;
    }

    private boolean ko_violation(){
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (previous_layout[i][j] != layout[i][j]){
                    return false;
                }
            }
        }
        return true;
    }

    private void copy_layout( int[][] victim,  int[][] target){
        System.out.println("SETTING");
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                victim[i][j] = target[i][j];
            }
        }
    }


    public boolean play(int x, int y){
        boolean returnValue = play(x, y, toggle);
        if(returnValue)
            toggle = !toggle;
        moved = true;
        return returnValue;
    }

    public void print() {
        for (int i = BOARD_SIZE - 1; i >= 0; i--) {
            for (int j = 0; j < BOARD_SIZE; j++) {

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
        boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];
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

        if (x + 1 < BOARD_SIZE) {
            neighbors.add(new int[] { x + 1, y });
        }

        if (y - 1 >= 0) {
            neighbors.add(new int[] { x, y - 1 });
        }

        if (y + 1 < BOARD_SIZE) {
            neighbors.add(new int[] { x, y + 1 });
        }

        return neighbors;
    }

    private void captureGroup(int x, int y) {
        int color = layout[x][y];
        boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];
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
                if (nx < 0 || nx >= BOARD_SIZE || ny < 0 || ny >= BOARD_SIZE) {
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
            for (int[] stone : group) {
                layout[stone[0]][stone[1]] = EMPTY;
            }
        }
    }
}
