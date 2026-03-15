package com.go.api.dto;

public record GameStateDto(
        BoardStateDto board,
        boolean solved,
        int version
) {}

