package com.go;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Chinese rules scoring: stones on board + surrounded empty points (territory).
 * Komi is added to White's score.
 * Supports optional manual territory marks and dead-stone marks for scoring disputes.
 */
public final class ChineseScoring {

    private ChineseScoring() {}

    public record ScoreResult(double black, double white) {}

    /**
     * Computes Chinese score for the current board state.
     * Black score = stones on board + territory. White score = stones on board + territory + komi.
     */
    public static ScoreResult compute(Board board, double komi) {
        return compute(board, komi, null, null);
    }

    /**
     * Computes Chinese score with optional manual marks.
     * territoryMarks: key "x,y" -> "BLACK" or "WHITE" for empty points (overrides flood-fill).
     * deadStones: keys "x,y" for stones considered dead (not counted; point treated as empty for territory).
     */
    public static ScoreResult compute(Board board, double komi,
                                      Map<String, String> territoryMarks,
                                      Set<String> deadStones) {
        int size = board.getBoardSize();

        int blackStones = 0;
        int whiteStones = 0;
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                String key = x + "," + y;
                if (deadStones != null && deadStones.contains(key)) continue;
                int s = board.getStoneAt(x, y);
                if (s == Board.BLACK) blackStones++;
                else if (s == Board.WHITE) whiteStones++;
            }
        }

        double blackTerritory = 0;
        double whiteTerritory = 0;

        if (territoryMarks != null && !territoryMarks.isEmpty()) {
            for (Map.Entry<String, String> e : territoryMarks.entrySet()) {
                String[] parts = e.getKey().split(",", 2);
                if (parts.length != 2) continue;
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                if (x < 0 || x >= size || y < 0 || y >= size) continue;
                int stone = board.getStoneAt(x, y);
                if (stone != Board.EMPTY && (deadStones == null || !deadStones.contains(e.getKey())))
                    continue;
                String color = e.getValue();
                if ("BLACK".equals(color)) blackTerritory += 1;
                else if ("WHITE".equals(color)) whiteTerritory += 1;
            }
        }

        boolean[][] visited = new boolean[size][size];
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                if (!isEmptyOrDead(board, x, y, deadStones) || visited[x][y]) continue;
                if (territoryMarks != null && territoryMarks.containsKey(x + "," + y)) continue;
                TerritoryResult tr = floodTerritory(board, x, y, visited, deadStones);
                if (tr.touchesBlack && !tr.touchesWhite) blackTerritory += tr.size;
                else if (tr.touchesWhite && !tr.touchesBlack) whiteTerritory += tr.size;
            }
        }

        double black = blackStones + blackTerritory;
        double white = whiteStones + whiteTerritory + komi;
        return new ScoreResult(black, white);
    }

    private static boolean isEmptyOrDead(Board board, int x, int y, Set<String> deadStones) {
        int s = board.getStoneAt(x, y);
        if (s == Board.EMPTY) return true;
        return deadStones != null && deadStones.contains(x + "," + y);
    }

    private static class TerritoryResult {
        int size;
        boolean touchesBlack;
        boolean touchesWhite;
    }

    private static TerritoryResult floodTerritory(Board board, int startX, int startY, boolean[][] visited, Set<String> deadStones) {
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
                boolean emptyOrDead = isEmptyOrDead(board, nx, ny, deadStones);
                if (!emptyOrDead) {
                    int stone = board.getStoneAt(nx, ny);
                    if (stone == Board.BLACK) r.touchesBlack = true;
                    else if (stone == Board.WHITE) r.touchesWhite = true;
                } else {
                    if (!seen.contains(nx + "," + ny)) {
                        seen.add(nx + "," + ny);
                        visited[nx][ny] = true;
                        q.add(new int[] { nx, ny });
                    }
                }
            }
        }
        return r;
    }
}
