package com.go.api.dto;

public record OnlineGameStateDto(
        BoardStateDto board,
        String currentTurn,
        String status,
        String blackPlayerName,
        String whitePlayerName
) {}

