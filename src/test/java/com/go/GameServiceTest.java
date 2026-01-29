package com.go;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameServiceTest {

    @Test
    void createSessionBuildsLevelFromPuzzle() {
        PuzzleRepository.PuzzleData data =
                new PuzzleRepository.PuzzleData(
                        1L,
                        "Test",
                        1,
                        List.of(new int[]{0, 0}),
                        List.of(),
                        List.of(),
                        true,
                        "notes"
                );

        PuzzleRepository repo = new InMemoryPuzzleRepository(
                List.of(new PuzzleRepository.PuzzleSummary(1L, "Test", 1)),
                List.of(data)
        );

        GameService service = new GameService(repo);

        GameSession session = service.createSession(1L);

        assertNotNull(session.getId());
        assertEquals(true, session.isPlayerWhite());
    }

    @Test
    void createSessionThrowsForMissingPuzzle() {
        PuzzleRepository repo = new InMemoryPuzzleRepository(
                Collections.emptyList(),
                Collections.emptyList()
        );

        GameService service = new GameService(repo);

        assertThrows(IllegalArgumentException.class, () -> service.createSession(99L));
    }

    private static class InMemoryPuzzleRepository implements PuzzleRepository {

        private final List<PuzzleSummary> summaries;
        private final List<PuzzleData> puzzles;

        private InMemoryPuzzleRepository(List<PuzzleSummary> summaries, List<PuzzleData> puzzles) {
            this.summaries = summaries;
            this.puzzles = puzzles;
        }

        @Override
        public List<PuzzleSummary> findAll() {
            return summaries;
        }

        @Override
        public Optional<PuzzleData> findById(long id) {
            return puzzles.stream().filter(p -> p.id() == id).findFirst();
        }
    }
}

