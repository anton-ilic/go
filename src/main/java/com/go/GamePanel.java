package com.go;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;

public class GamePanel extends JPanel {
    // Grid alignment constants - adjust these to fine-tune stone positioning
    private static final double GRID_RATIO = 0.90; // Grid occupies this % of image (0.0-1.0) - increased for 11x11 to reach edges
    private static final double OFFSET_X_RATIO = 0.0; // Horizontal offset as % of board width (positive = right)
    private static final double OFFSET_Y_RATIO = 0.0; // Vertical offset as % of board height (positive = down)
    
    private final Board board;
    private BufferedImage boardImage;
    private int boardX, boardY, boardWidth, boardHeight;
    private int cellSize;
    private int gridStartX, gridStartY;

    public GamePanel(Board board) {
        this.board = board;
        
        // Load the Go board image
        try {
            boardImage = ImageIO.read(getClass().getResource("/example-empty.jpeg"));
        } catch (IOException e) {
            System.out.println("Could not load board image: " + e.getMessage());
            boardImage = null;
        }

        // Add mouse listener for clicks
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (boardImage == null) {
                    // Recalculate grid if needed
                    repaint();
                    return;
                }
                
                // Recalculate grid positions (in case window was resized)
                calculateGridPositions();
                
                if (cellSize == 0) return;
                
                // Calculate which intersection was clicked
                int clickX = e.getX();
                int clickY = e.getY();
                
                // Find nearest intersection - allow clicks anywhere near the board
                int relativeX = clickX - gridStartX;
                int relativeY = clickY - gridStartY;
                
                // Calculate grid position (0-10) by finding nearest intersection
                // Use floor/round to handle edge cases better
                int gridX = (int) Math.round((float) relativeX / cellSize);
                int gridY = (int) Math.round((float) relativeY / cellSize);
                
                // Clamp to valid range (0 to BOARD_SIZE-1) - this ensures edges are included
                gridX = Math.max(0, Math.min(Board.BOARD_SIZE - 1, gridX));
                gridY = Math.max(0, Math.min(Board.BOARD_SIZE - 1, gridY));
                
                // Calculate actual intersection position
                int intersectionX = gridStartX + gridX * cellSize;
                int intersectionY = gridStartY + gridY * cellSize;
                
                // Check if click is close enough to an intersection
                // Use larger tolerance, especially for edges
                double distance = Math.sqrt(Math.pow(clickX - intersectionX, 2) + Math.pow(clickY - intersectionY, 2));
                double maxDistance = cellSize * 0.7; // 70% tolerance for easier clicking
                
                // For edge intersections, be very permissive to allow corner placement
                boolean isEdge = (gridX == 0 || gridX == Board.BOARD_SIZE - 1 || 
                                gridY == 0 || gridY == Board.BOARD_SIZE - 1);
                if (isEdge) {
                    maxDistance = cellSize * 1.0; // 100% tolerance for edges - very permissive
                }
                
                if (distance > maxDistance) {
                    return; // Too far from intersection
                }
                
                // Convert to board coordinates
                // Board uses (0,0) as bottom-left, screen uses (0,0) as top-left
                // gridY is screen Y (0 = top), need to convert to board Y (0 = bottom)
                int boardY = Board.BOARD_SIZE - 1 - gridY;
                
                board.play(gridX, boardY); 
                board.print();
                System.out.println("");
                repaint();
            }
        });
    }


    private void fillWoodTextureBackground(Graphics2D g2d) {
        if (boardImage == null) return;
        
        int imgWidth = boardImage.getWidth();
        int imgHeight = boardImage.getHeight();
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        
        // Sample wood texture from the edges of the board image
        int sampleWidth = Math.min(50, imgWidth / 4); // Sample width from edges
        int sampleHeight = Math.min(50, imgHeight / 4); // Sample height from edges
        
        // Fill left side - tile from left edge of image
        if (boardX > 0) {
            for (int x = 0; x < boardX; x += sampleWidth) {
                int tileW = Math.min(sampleWidth, boardX - x);
                g2d.drawImage(boardImage, x, 0, x + tileW, panelHeight,
                            0, 0, tileW, imgHeight, null);
            }
        }
        
        // Fill right side - tile from right edge of image
        int rightStart = boardX + boardWidth;
        if (rightStart < panelWidth) {
            for (int x = rightStart; x < panelWidth; x += sampleWidth) {
                int tileW = Math.min(sampleWidth, panelWidth - x);
                int srcX = imgWidth - tileW;
                g2d.drawImage(boardImage, x, 0, x + tileW, panelHeight,
                            srcX, 0, imgWidth, imgHeight, null);
            }
        }
        
        // Fill top area - tile from top edge of image
        if (boardY > 0) {
            for (int y = 0; y < boardY; y += sampleHeight) {
                int tileH = Math.min(sampleHeight, boardY - y);
                // Fill across the entire width
                for (int x = 0; x < panelWidth; x += sampleWidth) {
                    int tileW = Math.min(sampleWidth, panelWidth - x);
                    g2d.drawImage(boardImage, x, y, x + tileW, y + tileH,
                                0, 0, tileW, tileH, null);
                }
            }
        }
        
        // Fill bottom area - tile from bottom edge of image
        int bottomStart = boardY + boardHeight;
        if (bottomStart < panelHeight) {
            for (int y = bottomStart; y < panelHeight; y += sampleHeight) {
                int tileH = Math.min(sampleHeight, panelHeight - y);
                // Fill across the entire width
                for (int x = 0; x < panelWidth; x += sampleWidth) {
                    int tileW = Math.min(sampleWidth, panelWidth - x);
                    int srcY = imgHeight - tileH;
                    g2d.drawImage(boardImage, x, y, x + tileW, y + tileH,
                                0, srcY, tileW, imgHeight, null);
                }
            }
        }
    }

    private void calculateGridPositions() {
        if (boardImage == null) {
            cellSize = 0;
            return;
        }
        
        int imgWidth = boardImage.getWidth();
        int imgHeight = boardImage.getHeight();
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        
        // Scale to fit panel while maintaining aspect ratio
        double scaleX = (double) panelWidth / imgWidth;
        double scaleY = (double) panelHeight / imgHeight;
        double scale = Math.min(scaleX, scaleY);
        
        boardWidth = (int) (imgWidth * scale);
        boardHeight = (int) (imgHeight * scale);
        boardX = (panelWidth - boardWidth) / 2;
        boardY = (panelHeight - boardHeight) / 2;
        
        // Calculate grid positions for 11x11 board
        // For an 11x11 board, we have 11 intersections (0-10) with 10 cells between them
        // The grid should span nearly the full board to reach edge intersections
        
        // Use most of the board image to ensure edges are accessible
        int gridAreaWidth = (int) (boardWidth * GRID_RATIO);
        int gridAreaHeight = (int) (boardHeight * GRID_RATIO);
        
        // Calculate cell size based on 10 cells between 11 intersections
        // Use the smaller dimension to ensure square grid
        int gridDimension = Math.min(gridAreaWidth, gridAreaHeight);
        cellSize = gridDimension / (Board.BOARD_SIZE - 1);
        
        // Ensure cellSize is at least 1
        if (cellSize < 1) cellSize = 1;
        
        // Calculate total grid span (from first intersection at 0 to last at 10)
        int totalGridWidth = cellSize * (Board.BOARD_SIZE - 1);
        int totalGridHeight = cellSize * (Board.BOARD_SIZE - 1);
        
        // Position grid to span nearly the full board - start closer to edges
        // This ensures intersections 0 and 10 are near the actual board edges
        int marginX = (int) ((boardWidth - totalGridWidth) * 0.5); // 50% of remaining space as margin
        int marginY = (int) ((boardHeight - totalGridHeight) * 0.5);
        
        int centerOffsetX = (int) (boardWidth * OFFSET_X_RATIO);
        int centerOffsetY = (int) (boardHeight * OFFSET_Y_RATIO);
        
        // Add small downward offset (25% of stone diameter = quarter stone radius)
        // Stone radius is approximately cellSize * 0.48, so use half of that for the offset
        int stoneRadius = (int) (cellSize * 0.48);
        int gridOffsetY = stoneRadius / 2; // Move grid down by quarter a stone diameter
        
        gridStartX = boardX + marginX + centerOffsetX;
        gridStartY = boardY + marginY + centerOffsetY + gridOffsetY;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        if (boardImage != null) {
            // Calculate grid positions (includes board dimensions)
            calculateGridPositions();
            
            // Fill background with wood texture from board image edges
            fillWoodTextureBackground(g2d);
            
            // Draw the board image
            g2d.drawImage(boardImage, boardX, boardY, boardWidth, boardHeight, null);
            
            // Draw stones at intersection points
            drawStonesOnBoard(g2d);
        } else {
            // Fallback: draw simple board
            g2d.setColor(new Color(220, 179, 92));
            g2d.fillRect(0, 0, getWidth(), getHeight());
        }
    }
    
    private void drawStonesOnBoard(Graphics2D g2d) {
        if (cellSize == 0) return;
        
        // Stone size proportional to cell size - larger for better visibility
        // For 11x11, stones should be larger relative to cell size
        int stoneRadius = (int) (cellSize * 0.48);
        // Ensure minimum stone size for visibility
        if (stoneRadius < 6) stoneRadius = 6;
        
        for (int boardX = 0; boardX < Board.BOARD_SIZE; boardX++) {
            for (int boardY = 0; boardY < Board.BOARD_SIZE; boardY++) {
                int stone = board.getStoneAt(boardX, boardY);
                if (stone == Board.WHITE || stone == Board.BLACK) {
                    // Calculate intersection position (exact center of intersection)
                    // Board coordinates: (0,0) is bottom-left
                    // Screen coordinates: (0,0) is top-left
                    // So we need to flip the y-axis
                    int intersectionX = gridStartX + boardX * cellSize;
                    int intersectionY = gridStartY + (Board.BOARD_SIZE - 1 - boardY) * cellSize;
                    
                    // Draw stone centered on intersection
                    int stoneX = intersectionX - stoneRadius;
                    int stoneY = intersectionY - stoneRadius;
                    int stoneDiameter = stoneRadius * 2;
                    
                    // Draw shadow (offset slightly)
                    g2d.setColor(stone == Board.WHITE ? new Color(200, 200, 200, 150) : new Color(20, 20, 20, 150));
                    g2d.fillOval(stoneX + 2, stoneY + 2, stoneDiameter, stoneDiameter);
                    
                    // Draw stone
                    g2d.setColor(stone == Board.WHITE ? Color.WHITE : new Color(45, 45, 45));
                    g2d.fillOval(stoneX, stoneY, stoneDiameter, stoneDiameter);
                    
                    // Add highlight
                    if (stone == Board.WHITE) {
                        g2d.setColor(new Color(255, 255, 255, 150));
                        g2d.fillOval(stoneX + stoneRadius/2, stoneY + stoneRadius/2, 
                                   stoneRadius, stoneRadius);
                    } else {
                        // Black stone highlight
                        g2d.setColor(new Color(255, 255, 255, 60));
                        g2d.fillOval(stoneX + stoneRadius/3, stoneY + stoneRadius/3, 
                                   stoneRadius/2, stoneRadius/2);
                    }
                }
            }
        }
    }
}
