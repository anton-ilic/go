package com.go;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class GamePanel extends JPanel {
    private final Board board;

    public GamePanel(Board board) {
        this.board = board;

        // Add mouse listener for clicks
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int cellWidth = getWidth() / Board.BOARD_SIZE;
                int cellHeight = getHeight() / Board.BOARD_SIZE;

                // map clicks to coords
                int x = e.getX() / cellWidth;
                int y = Board.BOARD_SIZE - 1 - (e.getY() / cellHeight); // Flip y-axis (for 0,0 is bottom left)
                board.play(x, y); 
                board.print();
                System.out.println("");
                repaint();
            }
        });
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
        g2d.setColor(new Color(220, 179, 92));// wood like color
        g2d.fillRect(0, 0, getWidth(), getHeight());
        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.setStroke(new BasicStroke(1.5f));
        
        for (int i = 0; i <= Board.BOARD_SIZE; i++) {
            int x = i * cellWidth;
            int y = i * cellHeight;
            g2d.drawLine(x, 0, x, getHeight());
            g2d.drawLine(0, y, getWidth(), y);
        }

        // Draw star points 
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

        // Draw Stones
        for (int x = 0; x < Board.BOARD_SIZE; x++) {
            for (int y = 0; y < Board.BOARD_SIZE; y++) {
                int stone = board.getStoneAt(x, y);
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
    }
}
