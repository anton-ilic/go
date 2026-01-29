package com.go;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-memory game sessions built from puzzles.
 * This can later be backed by a persistent store for multiplayer.
 */
@Service
public class GameService {

    private final PuzzleRepository puzzleRepository;
    private final Map<UUID, GameSession> sessions = new ConcurrentHashMap<>();

    public GameService(PuzzleRepository puzzleRepository) {
        this.puzzleRepository = puzzleRepository;
    }

    public GameSession createSession(long puzzleId) {
        PuzzleRepository.PuzzleData puzzle = puzzleRepository.findById(puzzleId)
                .orElseThrow(() -> new IllegalArgumentException("Puzzle not found: " + puzzleId));

        Board board = new Board();
        Level level = new Level(board, puzzle.solution(), puzzle.initialWhite(), puzzle.initialBlack());
        GameSession session = new GameSession(level, puzzle.playerIsWhite());
        sessions.put(session.getId(), session);
        return session;
    }

    public GameSession getSession(UUID id) {
        GameSession session = sessions.get(id);
        if (session == null) {
            throw new IllegalArgumentException("Game session not found: " + id);
        }
        return session;
    }

    /**
     * Applies a player move to the game session using the Level logic.
     *
     * @return true if the move was correct according to the puzzle solution, false otherwise.
     */
    public boolean playMove(UUID sessionId, int x, int y) {
        GameSession session = getSession(sessionId);
        Level level = session.getLevel();
        boolean isWhite = session.isPlayerWhite();
        return level.playMove(x, y, isWhite);
    }
}

