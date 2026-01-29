package com.go.api.dto;

public record PuzzleDto(
        long id,
        String name,
        int difficulty,
        boolean playerIsWhite,
        String notes,
        BoardStateDto initialBoard
) {}

