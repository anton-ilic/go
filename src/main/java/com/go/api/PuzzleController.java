package com.go.api;

import com.go.Board;
import com.go.PuzzleRepository;
import com.go.PuzzleService;
import com.go.api.dto.BoardStateDto;
import com.go.api.dto.PuzzleDto;
import com.go.api.dto.PuzzleSummaryDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/puzzles")
public class PuzzleController {

    private final PuzzleService puzzleService;

    public PuzzleController(PuzzleService puzzleService) {
        this.puzzleService = puzzleService;
    }

    @GetMapping
    public List<PuzzleSummaryDto> listPuzzles() {
        return puzzleService.listPuzzles().stream()
                .map(s -> new PuzzleSummaryDto(s.id(), s.name(), s.difficulty()))
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PuzzleDto> getPuzzle(@PathVariable("id") long id) {
        try {
            PuzzleRepository.PuzzleData puzzle = puzzleService.getPuzzle(id);
            PuzzleDto dto = new PuzzleDto(
                    puzzle.id(),
                    puzzle.name(),
                    puzzle.difficulty(),
                    puzzle.playerIsWhite(),
                    puzzle.notes(),
                    buildInitialBoardState(puzzle)
            );
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private BoardStateDto buildInitialBoardState(PuzzleRepository.PuzzleData puzzle) {
        int size = Board.DEFAULT_BOARD_SIZE;
        List<BoardStateDto.StoneDto> stones = new ArrayList<>();

        for (int[] pos : puzzle.initialWhite()) {
            stones.add(new BoardStateDto.StoneDto(pos[0], pos[1], "WHITE"));
        }
        for (int[] pos : puzzle.initialBlack()) {
            stones.add(new BoardStateDto.StoneDto(pos[0], pos[1], "BLACK"));
        }

        return new BoardStateDto(size, stones);
    }
}

