package com.go.api.dto;

public record CreateGameResponse(
        String gameId,
        String playerColor,
        GameStateDto state
) {}

