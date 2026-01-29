package com.go.api.dto;

public record MoveResponse(
        String gameId,
        String status,
        String message,
        GameStateDto state
) {}

