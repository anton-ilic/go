package com.go.api.dto;

/**
 * Optional settings when creating a room. All fields are optional; defaults apply when null/absent.
 */
public record CreateRoomRequest(
        Integer boardSize,   // 9, 11, or 19; default 11
        Double komi,         // e.g. 6.5; placeholder for future scoring
        String startingColor // "BLACK" or "WHITE"; placeholder for future
) {}
