package com.go.api;

import com.go.PuzzleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PuzzleControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PuzzleRepository puzzleRepository;

    @Test
    void listPuzzlesReturnsOk() throws Exception {
        mockMvc.perform(get("/api/puzzles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getFirstPuzzleIfPresent() throws Exception {
        var summaries = puzzleRepository.findAll();
        if (summaries.isEmpty()) {
            return; // nothing to assert against
        }

        long id = summaries.get(0).id();

        mockMvc.perform(get("/api/puzzles/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) id)))
                .andExpect(jsonPath("$.initialBoard", notNullValue()));
    }
}

