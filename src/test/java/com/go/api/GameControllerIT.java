package com.go.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.go.PuzzleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GameControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PuzzleRepository puzzleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createGameFromFirstPuzzleIfPresent() throws Exception {
        var summaries = puzzleRepository.findAll();
        if (summaries.isEmpty()) {
            return;
        }

        long puzzleId = summaries.get(0).id();

        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"puzzleId\":" + puzzleId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId", notNullValue()))
                .andExpect(jsonPath("$.state.board", notNullValue()));
    }

    @Test
    void playMoveOnCreatedGame() throws Exception {
        var summaries = puzzleRepository.findAll();
        if (summaries.isEmpty()) {
            return;
        }

        long puzzleId = summaries.get(0).id();

        MvcResult createResult = mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"puzzleId\":" + puzzleId + "}"))
                .andExpect(status().isOk())
                .andReturn();

        String json = createResult.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(json);
        String gameId = root.get("gameId").asText();

        mockMvc.perform(post("/api/games/{gameId}/moves", gameId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":0,\"y\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId", notNullValue()))
                .andExpect(jsonPath("$.state.board", notNullValue()));
    }
}

