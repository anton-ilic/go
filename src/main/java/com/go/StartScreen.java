package com.go;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class StartScreen extends JFrame implements KeyListener {
    public StartScreen() {
        setTitle("Go Puzzles");
        setSize(1000, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        addKeyListener(this);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);

        // Main panel with custom painting
        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                
                drawGoBoard(g2d);
                drawStoneBaskets(g2d);
            }
        };
        mainPanel.setLayout(new BorderLayout());
        add(mainPanel);

        // Title panel
        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        
        JLabel titleLabel = new JLabel("Go Puzzles");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 48));
        titleLabel.setForeground(new Color(50, 50, 50));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(40, 0, 20, 0));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titlePanel.add(titleLabel);
        
        JLabel subtitleLabel = new JLabel("Can you solve the puzzle that Sun Tzu could not?");
        subtitleLabel.setFont(new Font("Arial", Font.ITALIC, 16));
        subtitleLabel.setForeground(new Color(80, 80, 80));
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titlePanel.add(subtitleLabel);
        
        mainPanel.add(titlePanel, BorderLayout.NORTH);

        // Commands panel
        JPanel commandsPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                drawCommands(g2d);
            }
        };
        commandsPanel.setOpaque(false);
        commandsPanel.setPreferredSize(new Dimension(600, 200));
        mainPanel.add(commandsPanel, BorderLayout.CENTER);

        setVisible(true);
    }

    private void drawGoBoard(Graphics2D g2d) {
        // Fill entire screen with board background
        g2d.setColor(new Color(222, 184, 135)); // Wooden board color
        g2d.fillRect(0, 0, getWidth(), getHeight());
        
        // Grid lines covering entire screen
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(1.5f));
        int cellSize = 50; // Larger cells for full screen
        
        // Vertical lines
        for (int i = 0; i <= getWidth() / cellSize; i++) {
            int x = i * cellSize;
            g2d.drawLine(x, 0, x, getHeight());
        }
        
        // Horizontal lines
        for (int i = 0; i <= getHeight() / cellSize; i++) {
            int y = i * cellSize;
            g2d.drawLine(0, y, getWidth(), y);
        }
        
        // Star points (hoshi) scattered across the board
        g2d.setColor(Color.BLACK);
        int[] starPositions = {2, 5, 8, 11, 14, 17}; // More star points for full screen
        for (int row : starPositions) {
            for (int col : starPositions) {
                int x = col * cellSize;
                int y = row * cellSize;
                if (x < getWidth() && y < getHeight()) {
                    g2d.fillOval(x - 4, y - 4, 8, 8);
                }
            }
        }
    }

    private void drawStoneBaskets(Graphics2D g2d) {
        int bowlSize = 120; // Larger circular bowls
        
        // Black stone bowl (bottom left)
        int blackBowlX = 60;
        int blackBowlY = getHeight() - 140;
        drawCircularStoneBowl(g2d, blackBowlX, blackBowlY, bowlSize, true);
        
        // White stone bowl (bottom right, higher)
        int whiteBowlX = getWidth() - 180;
        int whiteBowlY = getHeight() - 180; // Higher than black bowl
        drawCircularStoneBowl(g2d, whiteBowlX, whiteBowlY, bowlSize, false);
    }

    private void drawCircularStoneBowl(Graphics2D g2d, int x, int y, int size, boolean isBlack) {
        int radius = size / 2;
        int centerX = x + radius;
        int centerY = y + radius;
        //shaodw
        g2d.setColor(new Color(0, 0, 0, 100));
        g2d.fillOval(x + 5, y + 5, size, size);
        
        GradientPaint bowlGradient = new GradientPaint(centerX - radius, centerY - radius, new Color(160, 82, 45), 
                                                      centerX + radius, centerY + radius, new Color(139, 69, 19));
        g2d.setPaint(bowlGradient);
        g2d.fillOval(x, y, size, size);
        
        // Bowl rim
        g2d.setColor(new Color(101, 67, 33));
        g2d.setStroke(new BasicStroke(4f));
        g2d.drawOval(x, y, size, size);
        
        // Inner highlight
        g2d.setColor(new Color(255, 255, 255, 80));
        g2d.setStroke(new BasicStroke(3f));
        g2d.drawOval(x + 3, y + 3, size - 6, size - 6);
        
        // Draw overlapping stones in the bowl
        drawOverlappingStonesInBowl(g2d, centerX, centerY, radius, isBlack);
    }

    private void drawOverlappingStonesInBowl(Graphics2D g2d, int centerX, int centerY, int radius, boolean isBlack) {
        int stoneSize = 20; 
        int maxStones = 100;
        
        for (int i = 0; i < maxStones; i++) {
            // Generate random position within the circular bowl
            double angle = Math.random() * 2 * Math.PI;
            double distance = Math.random() * (radius - stoneSize/2 - 10); // Keep stones within bowl
            int stoneX = centerX + (int)(distance * Math.cos(angle)) - stoneSize/2;
            int stoneY = centerY + (int)(distance * Math.sin(angle)) - stoneSize/2;
            
            // Add some random offset for natural stacking
            stoneX += (int)(Math.random() * 8) - 4;
            stoneY += (int)(Math.random() * 8) - 4;
            
            // Stone shadow (offset for depth)
            g2d.setColor(new Color(0, 0, 0, 150));
            g2d.fillOval(stoneX + 2, stoneY + 2, stoneSize, stoneSize);
            
            // Stone
            if (isBlack) {
                g2d.setColor(new Color(45, 45, 45));
                g2d.fillOval(stoneX, stoneY, stoneSize, stoneSize);
                // Black stone highlight
                g2d.setColor(new Color(255, 255, 255, 80));
                g2d.fillOval(stoneX + 2, stoneY + 2, stoneSize / 3, stoneSize / 3);
            } else {
                g2d.setColor(Color.WHITE);
                g2d.fillOval(stoneX, stoneY, stoneSize, stoneSize);
                // White stone highlight
                g2d.setColor(new Color(255, 255, 255, 200));
                g2d.fillOval(stoneX + 3, stoneY + 3, stoneSize / 2, stoneSize / 2);
            }
        }
        
        // Add some extra stones that appear to be stacked/overflowing
        for (int i = 0; i < 15; i++) {
            double angle = Math.random() * 2 * Math.PI;
            double distance = radius - stoneSize/2 + (int)(Math.random() * 15); // Some stones slightly outside
            int stoneX = centerX + (int)(distance * Math.cos(angle)) - stoneSize/2;
            int stoneY = centerY + (int)(distance * Math.sin(angle)) - stoneSize/2;
            
            // Stone shadow
            g2d.setColor(new Color(0, 0, 0, 150));
            g2d.fillOval(stoneX + 2, stoneY + 2, stoneSize, stoneSize);
            
            // Stone
            if (isBlack) {
                g2d.setColor(new Color(45, 45, 45));
                g2d.fillOval(stoneX, stoneY, stoneSize, stoneSize);
                g2d.setColor(new Color(255, 255, 255, 80));
                g2d.fillOval(stoneX + 2, stoneY + 2, stoneSize / 3, stoneSize / 3);
            } else {
                g2d.setColor(Color.WHITE);
                g2d.fillOval(stoneX, stoneY, stoneSize, stoneSize);
                g2d.setColor(new Color(255, 255, 255, 200));
                g2d.fillOval(stoneX + 3, stoneY + 3, stoneSize / 2, stoneSize / 2);
            }
        }
    }

    private void drawCommands(Graphics2D g2d) {
        String[] commands = {
            "Click G to play the Go game",
            "Click P to play a Go puzzle", 
            "Click E to exit"
        };
        
        int centerX = getWidth() / 2;
        int startY = 80; // Move commands lower within the box
        int spacing = 40;
        
        // Calculate the size needed for the big box
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        FontMetrics fm = g2d.getFontMetrics();
        int maxWidth = 0;
        for (String command : commands) {
            int width = fm.stringWidth(command);
            if (width > maxWidth) {
                maxWidth = width;
            }
        }
        
        // Draw one big box for all commands
        int padding = 30;
        int boxWidth = maxWidth + padding * 2;
        int boxHeight = (commands.length * spacing) + padding + 40; // Extra height for lower positioning
        int boxX = centerX - boxWidth / 2;
        int boxY = startY - padding - 20; // Start box higher to accommodate lower text
        
        // Draw shadow
        g2d.setColor(new Color(0, 0, 0, 80));
        g2d.fillRoundRect(boxX + 4, boxY + 4, boxWidth, boxHeight, 20, 20);
        
        // Draw main box background
        g2d.setColor(new Color(255, 255, 255, 240));
        g2d.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 20, 20);
        
        // Draw border
        g2d.setColor(new Color(100, 149, 237, 200));
        g2d.setStroke(new BasicStroke(4f));
        g2d.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 20, 20);
        
        // Draw inner highlight
        g2d.setColor(new Color(255, 255, 255, 100));
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawRoundRect(boxX + 2, boxY + 2, boxWidth - 4, boxHeight - 4, 18, 18);
        
        // Draw all commands inside the big box
        for (int i = 0; i < commands.length; i++) {
            String command = commands[i];
            int x = centerX - fm.stringWidth(command) / 2;
            int y = startY + i * spacing;
            
            // Draw text
            g2d.setColor(new Color(50, 50, 50));
            g2d.drawString(command, x, y);
        }
        
        // Draw instruction text below the big box
        g2d.setFont(new Font("Arial", Font.ITALIC, 16));
        g2d.setColor(new Color(100, 100, 100));
        String instruction = "Use keyboard shortcuts to navigate";
        FontMetrics instFm = g2d.getFontMetrics();
        int instX = centerX - instFm.stringWidth(instruction) / 2;
        int instY = boxY + boxHeight + 30;
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
