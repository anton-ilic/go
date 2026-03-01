package com.go;

import java.util.List;
import java.util.Optional;

/**
 * Repository abstraction for loading puzzles from storage.
 * Implementations can use SQLite, Postgres, Supabase, etc.
 */
public interface PuzzleRepository {

    List<PuzzleSummary> findAll();

    Optional<PuzzleData> findById(long id);

    /**
     * Lightweight summary information for listing puzzles.
     */
    record PuzzleSummary(long id, String name, int difficulty) {}

    /**
     * Full puzzle data used to construct a Level/Board.
     */
    record PuzzleData(
            long id,
            String name,
            int difficulty,
            List<int[]> initialWhite,
            List<int[]> initialBlack,
            List<SolutionStep> solution,
            boolean playerIsWhite,
            String notes
    ) {}
}

