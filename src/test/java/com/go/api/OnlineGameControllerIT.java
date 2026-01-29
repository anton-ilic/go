package com.go.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OnlineGameControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createJoinAndPlayMoves() throws Exception {
        // Create game
        MvcResult createResult = mockMvc.perform(post("/api/online-games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerName\":\"Alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId", notNullValue()))
                .andExpect(jsonPath("$.playerId", notNullValue()))
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String gameId = createJson.get("gameId").asText();
        String blackId = createJson.get("playerId").asText();

        // Join as white
        MvcResult joinResult = mockMvc.perform(post("/api/online-games/{gameId}/join", gameId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerName\":\"Bob\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId", notNullValue()))
                .andReturn();

        JsonNode joinJson = objectMapper.readTree(joinResult.getResponse().getContentAsString());
        String whiteId = joinJson.get("playerId").asText();

        // Fetch state as black
        mockMvc.perform(get("/api/online-games/{gameId}", gameId)
                        .param("playerId", blackId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.board", notNullValue()));

        // Black plays a move
        mockMvc.perform(post("/api/online-games/{gameId}/moves", gameId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + blackId + "\",\"x\":0,\"y\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.board", notNullValue()));

        // White attempts move
        mockMvc.perform(post("/api/online-games/{gameId}/moves", gameId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + whiteId + "\",\"x\":1,\"y\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.board", notNullValue()));
    }
}

