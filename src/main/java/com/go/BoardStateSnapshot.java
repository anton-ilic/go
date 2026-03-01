package com.go;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.Arrays;

/**
 * Immutable snapshot of the board state (layout, previous layout for ko, prisoners, turn).
 * Used for undo/redo so we can restore state including after captures.
 */
public final class BoardStateSnapshot {

    private static final Gson GSON = new Gson();

    private final int[][] layout;
    private final int[][] previousLayout;
    private final int blackPrisoners;
    private final int whitePrisoners;
    private final boolean turnIsWhite;

    public BoardStateSnapshot(
            int[][] layout,
            int[][] previousLayout,
            int blackPrisoners,
            int whitePrisoners,
            boolean turnIsWhite) {
        this.layout = deepCopy(layout);
        this.previousLayout = deepCopy(previousLayout);
        this.blackPrisoners = blackPrisoners;
        this.whitePrisoners = whitePrisoners;
        this.turnIsWhite = turnIsWhite;
    }

    public int[][] getLayout() {
        return deepCopy(layout);
    }

    public int[][] getPreviousLayout() {
        return deepCopy(previousLayout);
    }

    public int getBlackPrisoners() {
        return blackPrisoners;
    }

    public int getWhitePrisoners() {
        return whitePrisoners;
    }

    public boolean isTurnIsWhite() {
        return turnIsWhite;
    }

    public String toJson() {
        return GSON.toJson(new JsonState(layout, previousLayout, blackPrisoners, whitePrisoners, turnIsWhite));
    }

    public static BoardStateSnapshot fromJson(String json) {
        JsonState j = GSON.fromJson(json, JsonState.class);
        return new BoardStateSnapshot(
                j.layout,
                j.previousLayout,
                j.blackPrisoners,
                j.whitePrisoners,
                j.turnIsWhite
        );
    }

    private static int[][] deepCopy(int[][] src) {
        if (src == null) return null;
        int[][] out = new int[src.length][];
        for (int i = 0; i < src.length; i++) {
            out[i] = src[i] != null ? Arrays.copyOf(src[i], src[i].length) : null;
        }
        return out;
    }

    private static class JsonState {
        @SerializedName("layout")
        int[][] layout;
        @SerializedName("previousLayout")
        int[][] previousLayout;
        @SerializedName("blackPrisoners")
        int blackPrisoners;
        @SerializedName("whitePrisoners")
        int whitePrisoners;
        @SerializedName("turnIsWhite")
        boolean turnIsWhite;

        @SuppressWarnings("unused") // used by Gson for deserialization
        JsonState() {}

        JsonState(int[][] layout, int[][] previousLayout, int blackPrisoners, int whitePrisoners, boolean turnIsWhite) {
            this.layout = layout;
            this.previousLayout = previousLayout;
            this.blackPrisoners = blackPrisoners;
            this.whitePrisoners = whitePrisoners;
            this.turnIsWhite = turnIsWhite;
        }
    }
}
