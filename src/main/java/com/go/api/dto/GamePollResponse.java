package com.go.api.dto;

/**
 * Response payload for polling the current game state from the server.
 * Designed for lightweight client-side polling in multiplayer scenarios.
 */
public record GamePollResponse(
        String gameId,
        GameStateDto state,
        boolean canUndo,
        boolean canRedo
) {}

