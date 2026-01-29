package com.go;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single puzzle game session backed by a Board and Level.
 */
public class GameSession {

    private final UUID id;
    private final Level level;
    private final boolean playerIsWhite;
    private final Instant createdAt;

    public GameSession(Level level, boolean playerIsWhite) {
        this.id = UUID.randomUUID();
        this.level = level;
        this.playerIsWhite = playerIsWhite;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Level getLevel() {
        return level;
    }

    public boolean isPlayerWhite() {
        return playerIsWhite;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

