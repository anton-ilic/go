package com.go;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;

public class StartScreen extends JFrame implements KeyListener {
    private BufferedImage boardImage;
    
    public StartScreen() {
        setTitle("Go Puzzles");
        setSize(1000, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        addKeyListener(this);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);

        // Load the Go board image
        try {
            boardImage = ImageIO.read(getClass().getResource("/example-empty.jpeg"));
        } catch (IOException e) {
            System.out.println("Could not load board image: " + e.getMessage());
            boardImage = null;
        }

        // Main panel with custom painting
        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                
                drawGoBoard(g2d);
                drawText(g2d);
            }
        };
        mainPanel.setLayout(new BorderLayout());
        add(mainPanel);

        setVisible(true);
    }

    private void drawGoBoard(Graphics2D g2d) {
        if (boardImage != null) {
            int imgWidth = boardImage.getWidth();
            int imgHeight = boardImage.getHeight();
            int screenWidth = getWidth();
            int screenHeight = getHeight();
            
            // Calculate scale to fit height while maintaining aspect ratio
            double scale = (double) screenHeight / imgHeight;
            int scaledWidth = (int) (imgWidth * scale);
            int scaledHeight = screenHeight;
            
            // Center the board horizontally
            int boardX = (screenWidth - scaledWidth) / 2;
            int boardY = 0;
            
            // Fill background with wood texture color first
            g2d.setColor(new Color(222, 184, 135));
            g2d.fillRect(0, 0, screenWidth, screenHeight);
            
            // Draw the board image at correct aspect ratio
            g2d.drawImage(boardImage, boardX, boardY, scaledWidth, scaledHeight, null);
            
            // Extend wood texture on the sides by tiling from edges
            if (boardX > 0) {
                int tileWidth = 50; // Width of edge to tile
                
                // Left side - tile from left edge of image
                for (int x = 0; x < boardX; x += tileWidth) {
                    int tileW = Math.min(tileWidth, boardX - x);
                    g2d.drawImage(boardImage, x, 0, x + tileW, screenHeight,
                                0, 0, tileW, imgHeight, null);
                }
                
                // Right side - tile from right edge of image
                int rightStart = boardX + scaledWidth;
                for (int x = rightStart; x < screenWidth; x += tileWidth) {
                    int tileW = Math.min(tileWidth, screenWidth - x);
                    int srcX = imgWidth - tileW;
                    g2d.drawImage(boardImage, x, 0, x + tileW, screenHeight,
                                srcX, 0, imgWidth, imgHeight, null);
                }
            }
        } else {
            // Fallback: draw a simple board background
            g2d.setColor(new Color(222, 184, 135));
            g2d.fillRect(0, 0, getWidth(), getHeight());
        }
    }


    private void drawText(Graphics2D g2d) {
        int centerX = getWidth() / 2;
        
        // Calculate where the board ends (board fills height, so it ends at getHeight())
        // Place text in the bottom wood area
        int textStartY = getHeight() - 180; // Start text in bottom wood area
        
        // Draw title on the board (at top)
        g2d.setFont(new Font("Arial", Font.BOLD, 56));
        g2d.setColor(new Color(30, 30, 30));
        String title = "Go Puzzles";
        FontMetrics fm = g2d.getFontMetrics();
        int titleX = centerX - fm.stringWidth(title) / 2;
        int titleY = 80;
        
        // Title shadow for better visibility
        g2d.setColor(new Color(0, 0, 0, 100));
        g2d.drawString(title, titleX + 2, titleY + 2);
        g2d.setColor(new Color(30, 30, 30));
        g2d.drawString(title, titleX, titleY);
        
        // Draw subtitle on the board
        g2d.setFont(new Font("Arial", Font.ITALIC, 20));
        g2d.setColor(new Color(60, 60, 60));
        String subtitle = "Let's GO play Go!";
        FontMetrics subFm = g2d.getFontMetrics();
        int subX = centerX - subFm.stringWidth(subtitle) / 2;
        int subY = titleY + 40;
        g2d.drawString(subtitle, subX, subY);
        
        // Draw commands in the bottom wood area
        String[] commands = {
            "Click G to play the Go game",
            "Click P to play a Go puzzle", 
            "Click E to exit"
        };
        
        g2d.setFont(new Font("Arial", Font.BOLD, 28));
        FontMetrics cmdFm = g2d.getFontMetrics();
        int spacing = 45;
        
        for (int i = 0; i < commands.length; i++) {
            String command = commands[i];
            int x = centerX - cmdFm.stringWidth(command) / 2;
            int y = textStartY + i * spacing;
            
            // Text shadow for better visibility on wood
            g2d.setColor(new Color(0, 0, 0, 120));
            g2d.drawString(command, x + 2, y + 2);
            
            // Main text
            g2d.setColor(new Color(50, 50, 50));
            g2d.drawString(command, x, y);
        }
        
        // Draw instruction text below commands
        g2d.setFont(new Font("Arial", Font.ITALIC, 18));
        g2d.setColor(new Color(80, 80, 80));
        String instruction = "Use keyboard shortcuts to navigate";
        FontMetrics instFm = g2d.getFontMetrics();
        int instX = centerX - instFm.stringWidth(instruction) / 2;
        int instY = textStartY + commands.length * spacing + 30;
        
        // Instruction shadow
        g2d.setColor(new Color(0, 0, 0, 100));
        g2d.drawString(instruction, instX + 1, instY + 1);
        g2d.setColor(new Color(80, 80, 80));
        g2d.drawString(instruction, instX, instY);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        char key = Character.toLowerCase(e.getKeyChar());
        switch (key) {
            case 'g':
                dispose();
                new GameScreen();
                break;
            case 'p':
                dispose();
                new PuzzleScreen();
                break;
            case 'e':
                System.exit(0);
                break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void keyTyped(KeyEvent e) {}
}
