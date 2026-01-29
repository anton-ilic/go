package com.go.api.dto;

import java.util.List;

public record BoardStateDto(
        int boardSize,
        List<StoneDto> stones
) {
    public record StoneDto(int x, int y, String color) {}
}

