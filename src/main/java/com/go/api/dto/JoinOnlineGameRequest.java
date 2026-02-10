package com.go.api.dto;

public record JoinOnlineGameRequest(
        String playerName,
        String preferredColor  // "BLACK" or "WHITE", null means auto-assign
) {}

