package com.go.api.dto;

public record JoinOnlineGameResponse(
        String gameId,
        String roomCode,
        String playerId,
        String color,
        OnlineGameStateDto state
) {}

