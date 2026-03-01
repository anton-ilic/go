package com.go;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Chinese rules scoring: stones on board + surrounded empty points (territory).
 * Komi is added to White's score.
 */
public final class ChineseScoring {

    private ChineseScoring() {}

    public record ScoreResult(double black, double white) {}

    /**
     * Computes Chinese score for the current board state.
     * Black score = stones on board + territory. White score = stones on board + territory + komi.
     */
    public static ScoreResult compute(Board board, double komi) {
        int size = board.getBoardSize();
        int blackStones = 0;
        int whiteStones = 0;
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                int s = board.getStoneAt(x, y);
                if (s == Board.BLACK) blackStones++;
                else if (s == Board.WHITE) whiteStones++;
            }
        }

        boolean[][] visited = new boolean[size][size];
        double blackTerritory = 0;
        double whiteTerritory = 0;

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                if (board.getStoneAt(x, y) != Board.EMPTY || visited[x][y]) continue;
                TerritoryResult tr = floodTerritory(board, x, y, visited);
                if (tr.touchesBlack && !tr.touchesWhite) blackTerritory += tr.size;
                else if (tr.touchesWhite && !tr.touchesBlack) whiteTerritory += tr.size;
            }
        }

        double black = blackStones + blackTerritory;
        double white = whiteStones + whiteTerritory + komi;
        return new ScoreResult(black, white);
    }

    private static class TerritoryResult {
        int size;
        boolean touchesBlack;
        boolean touchesWhite;
    }

    private static TerritoryResult floodTerritory(Board board, int startX, int startY, boolean[][] visited) {
        int size = board.getBoardSize();
        TerritoryResult r = new TerritoryResult();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[] { startX, startY });
        visited[startX][startY] = true;
        Set<String> seen = new HashSet<>();
        seen.add(startX + "," + startY);

        while (!q.isEmpty()) {
            int[] cell = q.poll();
            int x = cell[0], y = cell[1];
            r.size++;

            for (int[] n : new int[][] { { x - 1, y }, { x + 1, y }, { x, y - 1 }, { x, y + 1 } }) {
                int nx = n[0], ny = n[1];
                if (nx < 0 || nx >= size || ny < 0 || ny >= size) continue;
                int stone = board.getStoneAt(nx, ny);
                if (stone == Board.BLACK) r.touchesBlack = true;
                else if (stone == Board.WHITE) r.touchesWhite = true;
                else if (!seen.contains(nx + "," + ny)) {
                    seen.add(nx + "," + ny);
                    visited[nx][ny] = true;
                    q.add(new int[] { nx, ny });
                }
            }
        }
        return r;
    }
}
