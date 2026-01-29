package com.go;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PuzzleServiceTest {

    @Test
    void listPuzzlesDelegatesToRepository() {
        PuzzleRepository.PuzzleSummary summary =
                new PuzzleRepository.PuzzleSummary(1L, "Test", 1);

        PuzzleRepository repo = new InMemoryPuzzleRepository(
                List.of(summary),
                Collections.emptyList()
        );

        PuzzleService service = new PuzzleService(repo);

        List<PuzzleRepository.PuzzleSummary> result = service.listPuzzles();

        assertEquals(1, result.size());
        assertSame(summary, result.get(0));
    }

    @Test
    void getPuzzleReturnsPuzzleWhenPresent() {
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
                Collections.emptyList(),
                List.of(data)
        );

        PuzzleService service = new PuzzleService(repo);

        PuzzleRepository.PuzzleData result = service.getPuzzle(1L);

        assertSame(data, result);
    }

    @Test
    void getPuzzleThrowsWhenMissing() {
        PuzzleRepository repo = new InMemoryPuzzleRepository(
                Collections.emptyList(),
                Collections.emptyList()
        );

        PuzzleService service = new PuzzleService(repo);

        assertThrows(IllegalArgumentException.class, () -> service.getPuzzle(99L));
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

