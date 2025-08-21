package com.go;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;


public class PuzzlePanel extends JPanel implements KeyListener {
    private final Level level;
    private final boolean isWhite;
    private boolean puzzleSolved = false;
    private Runnable onNewPuzzle, onRetry, onBackToMenu;
    private boolean showMenu = false;
    

    public PuzzlePanel(Level level, boolean isWhitePlayer) {
        this.level = level;
        this.isWhite = isWhitePlayer;

        setFocusable(true);
        addKeyListener(this);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                requestFocusInWindow(); // Ensure keyboard focus after click

                if (puzzleSolved) return;

                int cellWidth = getWidth() / Board.BOARD_SIZE;
                int cellHeight = getHeight() / Board.BOARD_SIZE;

                int x = e.getX() / cellWidth;
                int y = Board.BOARD_SIZE - 1 - (e.getY() / cellHeight);

                boolean correct = level.playMove(x, y, isWhite);
                repaint();

                if (correct && level.isSolved()) {
                    puzzleSolved = true;
                    repaint();
                }
            }
        });
    }

    private void showPopup(String message, Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(new Color(255, 255, 255, 230));
            int boxWidth = 400;
            int boxHeight = 180;
            int boxX = (getWidth() - boxWidth) / 2;
            int boxY = (getHeight() - boxHeight) / 2;
            if (puzzleSolved == true) {
                g2.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 20, 20);
            } else{
                g2.fillRoundRect(boxX, boxY, boxWidth, boxHeight + 30, 20, 20);
            }

            g2.setColor(Color.BLACK);
            g2.setFont(new Font("SansSerif", Font.BOLD, 30));
            FontMetrics fm = g2.getFontMetrics();
            int msgX = boxX + (boxWidth - fm.stringWidth(message)) / 2;
            int msgY = boxY + 50;
            g2.drawString(message, msgX, msgY);

            g2.setFont(new Font("SansSerif", Font.PLAIN, 18));
            g2.drawString("[N] - New Puzzle", boxX + 30, boxY + 90);
            g2.drawString("[R] - Retry", boxX + 30, boxY + 120);
            g2.drawString("[M] - Main Menu", boxX + 30, boxY + 150);
            if (!puzzleSolved)
                g2.drawString("[C] - Continue", boxX + 30, boxY + 180);
    }

    public void setOnRetry(Runnable r) {
        this.onRetry = r;
    }

    public void setOnNewPuzzle(Runnable r) {
        this.onNewPuzzle = r;
    }

    public void setOnBackToMenu(Runnable r) {
        this.onBackToMenu = r;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int cellWidth = getWidth() / Board.BOARD_SIZE;
        int cellHeight = getHeight() / Board.BOARD_SIZE;

        g.setColor(Color.BLACK);
        for (int i = 0; i <= Board.BOARD_SIZE; i++) {
            g.drawLine(i * cellWidth, 0, i * cellWidth, getHeight());
            g.drawLine(0, i * cellHeight, getWidth(), i * cellHeight);
        }

        for (int x = 0; x < Board.BOARD_SIZE; x++) {
            for (int y = 0; y < Board.BOARD_SIZE; y++) {
                int stone = level.getStoneAt(x, y);
                if (stone == Board.WHITE) {
                    g.setColor(Color.WHITE);
                    g.fillOval(x * cellWidth + cellWidth / 4, (Board.BOARD_SIZE - 1 - y) * cellHeight + cellHeight / 4,
                            cellWidth / 2, cellHeight / 2);
                } else if (stone == Board.BLACK) {
                    g.setColor(Color.BLACK);
                    g.fillOval(x * cellWidth + cellWidth / 4, (Board.BOARD_SIZE - 1 - y) * cellHeight + cellHeight / 4,
                            cellWidth / 2, cellHeight / 2);
                }
            }
        }
        
        if (showMenu) {
            showPopup("Paused", g);
        }

        if (puzzleSolved) { //TODO: Generalize this to a "menu" popup
            showPopup("Puzzle Solved!", g);
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        requestFocusInWindow();
    }

    @Override
    public void keyTyped(KeyEvent e) { }

    @Override
    public void keyReleased(KeyEvent e) { }

    @Override
    public void keyPressed(KeyEvent e) { 
        if (!puzzleSolved & !showMenu){ //if only want to handle keys when puzzle is solved OR in menu
            if (Character.toUpperCase(e.getKeyChar()) == 'M'){
                showMenu = true;
                repaint();
                return;
            } else{
                return; 
            }
        } 

        switch (Character.toUpperCase(e.getKeyChar())) { 
            case 'R':
                onRetry.run();
                break;
            case 'C':
                if (!puzzleSolved){
                    showMenu = false;
                    repaint();
                }
                repaint();
                break;
            case 'N':
                onNewPuzzle.run();
                break;
            case 'M':
                onBackToMenu.run();
                break;
            case 'E':
                System.out.println("Exiting gracefully");
                System.exit(0);
        }
    }
}