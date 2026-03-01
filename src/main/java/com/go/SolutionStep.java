package com.go;

import java.util.List;

/**
 * One step of a puzzle solution: the player may play any of the allowed moves,
 * then the opponent (bot) plays the fixed reply if present.
 */
public record SolutionStep(List<int[]> playerMoves, int[] opponentMove) {
    public SolutionStep {
        if (playerMoves == null || playerMoves.isEmpty()) {
            throw new IllegalArgumentException("playerMoves must be non-empty");
        }
    }

    /** Single accepted move and optional opponent reply. */
    public static SolutionStep of(int[] playerMove, int[] opponentMove) {
        return new SolutionStep(List.of(playerMove), opponentMove);
    }

    /** Multiple accepted moves and optional opponent reply. */
    public static SolutionStep of(List<int[]> playerMoves, int[] opponentMove) {
        return new SolutionStep(List.copyOf(playerMoves), opponentMove);
    }
}
