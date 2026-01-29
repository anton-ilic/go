package com.go.api.dto;

public record JoinOnlineGameResponse(
        String gameId,
        String playerId,
        String color,
        OnlineGameStateDto state
) {}

