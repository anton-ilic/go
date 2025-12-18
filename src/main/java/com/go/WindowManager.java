package com.go;

import java.awt.*;
import javax.swing.JFrame;

public class WindowManager {
    private static final int WINDOW_WIDTH = 1000;
    private static final int WINDOW_HEIGHT = 800;
    private static Point windowLocation = null;
    
    public static void configureWindow(JFrame frame) {
        frame.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        
        if (windowLocation != null) {
            frame.setLocation(windowLocation);
        } else {
            frame.setLocationRelativeTo(null);
            windowLocation = frame.getLocation();
        }
    }
    
    public static void saveLocation(JFrame frame) {
        windowLocation = frame.getLocation();
    }
    
    public static int getWindowWidth() {
        return WINDOW_WIDTH;
    }
    
    public static int getWindowHeight() {
        return WINDOW_HEIGHT;
    }
}

