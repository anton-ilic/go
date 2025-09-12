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

    private void showPopup(String message, Graphics2D g2) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Draw shadow
            g2.setColor(new Color(0, 0, 0, 100));
            int boxWidth = 400;
            int boxHeight = 180;
            int boxX = (getWidth() - boxWidth) / 2 + 3;
            int boxY = (getHeight() - boxHeight) / 2 + 3;
            if (puzzleSolved == true) {
                g2.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 20, 20);
            } else{
                g2.fillRoundRect(boxX, boxY, boxWidth, boxHeight + 30, 20, 20);
            }
            
            // Draw main popup
            g2.setColor(new Color(255, 255, 255, 240));
            boxX = (getWidth() - boxWidth) / 2;
            boxY = (getHeight() - boxHeight) / 2;
            if (puzzleSolved == true) {
                g2.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 20, 20);
            } else{
                g2.fillRoundRect(boxX, boxY, boxWidth, boxHeight + 30, 20, 20);
            }

            // Draw border
            g2.setColor(new Color(100, 100, 100, 200));
            g2.setStroke(new BasicStroke(2f));
            if (puzzleSolved == true) {
                g2.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 20, 20);
            } else{
                g2.drawRoundRect(boxX, boxY, boxWidth, boxHeight + 30, 20, 20);
            }

            g2.setColor(Color.BLACK);
            g2.setFont(new Font("Arial", Font.BOLD, 32));
            FontMetrics fm = g2.getFontMetrics();
            int msgX = boxX + (boxWidth - fm.stringWidth(message)) / 2;
            int msgY = boxY + 50;
            g2.drawString(message, msgX, msgY);

            g2.setFont(new Font("Arial", Font.PLAIN, 18));
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
        
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int cellWidth = getWidth() / Board.BOARD_SIZE;
        int cellHeight = getHeight() / Board.BOARD_SIZE;
        int stoneRadius = Math.min(cellWidth, cellHeight) / 2 - 2;

        // Draw board background
        g2d.setColor(new Color(220, 179, 92)); // Traditional Go board color
        g2d.fillRect(0, 0, getWidth(), getHeight());

        // Draw grid lines with better quality
        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.setStroke(new BasicStroke(1.5f));
        
        for (int i = 0; i <= Board.BOARD_SIZE; i++) {
            int x = i * cellWidth;
            int y = i * cellHeight;
            g2d.drawLine(x, 0, x, getHeight());
            g2d.drawLine(0, y, getWidth(), y);
        }

        // Draw star points (hoshi)
        g2d.setColor(Color.BLACK);
        int starRadius = 3;
        int[] starPoints = {2, 6}; // 9x9 board star points
        for (int x : starPoints) {
            for (int y : starPoints) {
                int centerX = x * cellWidth;
                int centerY = y * cellHeight;
                g2d.fillOval(centerX - starRadius, centerY - starRadius, starRadius * 2, starRadius * 2);
            }
        }

        // Draw stones with shadows and better appearance
        for (int x = 0; x < Board.BOARD_SIZE; x++) {
            for (int y = 0; y < Board.BOARD_SIZE; y++) {
                int stone = level.getStoneAt(x, y);
                if (stone == Board.WHITE || stone == Board.BLACK) {
                    int centerX = x * cellWidth + cellWidth / 2;
                    int centerY = (Board.BOARD_SIZE - 1 - y) * cellHeight + cellHeight / 2;
                    
                    // Draw shadow
                    g2d.setColor(stone == Board.WHITE ? new Color(200, 200, 200) : new Color(20, 20, 20));
                    g2d.fillOval(centerX - stoneRadius + 1, centerY - stoneRadius + 1, 
                               stoneRadius * 2, stoneRadius * 2);
                    
                    // Draw stone
                    g2d.setColor(stone == Board.WHITE ? Color.WHITE : new Color(45, 45, 45));
                    g2d.fillOval(centerX - stoneRadius, centerY - stoneRadius, 
                               stoneRadius * 2, stoneRadius * 2);
                    
                    // Add highlight for white stones
                    if (stone == Board.WHITE) {
                        g2d.setColor(new Color(255, 255, 255, 100));
                        g2d.fillOval(centerX - stoneRadius/2, centerY - stoneRadius/2, 
                                   stoneRadius, stoneRadius);
                    }
                }
            }
        }
        
        if (showMenu) {
            showPopup("Paused", g2d);
        }

        if (puzzleSolved) {
            showPopup("Puzzle Solved!", g2d);
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