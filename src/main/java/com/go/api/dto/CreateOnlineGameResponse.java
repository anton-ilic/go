package com.go.api.dto;

public record CreateOnlineGameResponse(
        String gameId,
        String playerId,
        String color,
        OnlineGameStateDto state
) {}

