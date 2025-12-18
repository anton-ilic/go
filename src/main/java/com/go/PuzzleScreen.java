package com.go;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Random;

public class PuzzleScreen extends JFrame {
    public PuzzleScreen() {
        setTitle("Solve Puzzle");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        WindowManager.configureWindow(this);
        setLayout(new BorderLayout());

        // Load puzzles from DB
        List<PuzzleLoader.PuzzleData> puzzles = PuzzleLoader.loadAllPuzzles();
        if (puzzles.isEmpty()) {
            System.out.println("ERROR: no puzzles found in db");
            System.exit(1);
        }

        // Pick a random puzzle
        Random rand = new Random();
        PuzzleLoader.PuzzleData puzzle = puzzles.get(rand.nextInt(puzzles.size()));
        Level level = puzzle.level;
        boolean playerToMoveIsWhite = puzzle.playerIsWhite;

        // Set up panel
        PuzzlePanel puzzlePanel = new PuzzlePanel(level, playerToMoveIsWhite);
        add(puzzlePanel, BorderLayout.CENTER);

        setVisible(true);
        puzzlePanel.requestFocusInWindow();

        puzzlePanel.setOnRetry(() -> {
            WindowManager.saveLocation(this);
            dispose();
            new PuzzleScreen();
        });

        puzzlePanel.setOnNewPuzzle(() -> {
            WindowManager.saveLocation(this);
            dispose();
            new PuzzleScreen();
        });

        puzzlePanel.setOnBackToMenu(() -> {
            WindowManager.saveLocation(this);
            dispose();
            new StartScreen();
        });

    }
}
