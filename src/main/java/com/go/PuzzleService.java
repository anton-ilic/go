package com.go;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PuzzleService {

    private final PuzzleRepository puzzleRepository;

    public PuzzleService(PuzzleRepository puzzleRepository) {
        this.puzzleRepository = puzzleRepository;
    }

    public List<PuzzleRepository.PuzzleSummary> listPuzzles() {
        return puzzleRepository.findAll();
    }

    public PuzzleRepository.PuzzleData getPuzzle(long id) {
        return puzzleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Puzzle not found: " + id));
    }
}

