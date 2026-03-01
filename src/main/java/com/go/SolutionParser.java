package com.go;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parses puzzle solution JSON into a list of steps.
 * Supports legacy [[x,y],[x,y],...] and new [{"playerMoves":[[x,y],...],"opponentMove":[x,y]}, ...].
 */
public final class SolutionParser {

    private static final Gson GSON = new Gson();

    private SolutionParser() {}

    public static List<SolutionStep> parse(String solutionJson) {
        JsonArray arr = GSON.fromJson(solutionJson, JsonArray.class);
        if (arr == null || arr.isEmpty()) {
            throw new IllegalArgumentException("Solution must be a non-empty array");
        }
        JsonElement first = arr.get(0);
        if (first.isJsonArray()) {
            return parseLegacy(arr);
        }
        if (first.isJsonObject()) {
            return parseSteps(arr);
        }
        throw new IllegalArgumentException("Solution must be array of moves or steps");
    }

    private static List<SolutionStep> parseLegacy(JsonArray arr) {
        int[][] flat = GSON.fromJson(arr, int[][].class);
        List<int[]> list = Arrays.asList(flat);
        List<SolutionStep> steps = new ArrayList<>();
        for (int i = 0; i < list.size(); i += 2) {
            int[] playerMove = list.get(i);
            int[] opponentMove = (i + 1 < list.size()) ? list.get(i + 1) : null;
            steps.add(new SolutionStep(List.of(playerMove), opponentMove));
        }
        return steps;
    }

    private static List<SolutionStep> parseSteps(JsonArray arr) {
        List<SolutionStep> steps = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject ob = el.getAsJsonObject();
            JsonArray pm = ob.getAsJsonArray("playerMoves");
            if (pm == null || pm.size() == 0) {
                throw new IllegalArgumentException("Each step must have non-empty playerMoves");
            }
            int[][] playerMoves = GSON.fromJson(pm, int[][].class);
            JsonElement opp = ob.get("opponentMove");
            int[] opponentMove = (opp != null && !opp.isJsonNull()) ? GSON.fromJson(opp, int[].class) : null;
            steps.add(new SolutionStep(Arrays.asList(playerMoves), opponentMove));
        }
        return steps;
    }
}
