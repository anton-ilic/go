package com.go.api.dto;

public record CreateOnlineGameResponse(
        String gameId,
        String roomCode,
        String playerId,
        String color,
        OnlineGameStateDto state
) {}

