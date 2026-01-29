package com.go.api.dto;

public record OnlineMoveRequest(
        String playerId,
        int x,
        int y
) {}

