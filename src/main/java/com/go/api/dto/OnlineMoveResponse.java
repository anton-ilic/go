package com.go.api.dto;

public record OnlineMoveResponse(
        String status,
        String message,
        OnlineGameStateDto state
) {}

