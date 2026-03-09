package com.zetdc.diagrammatics.utils;

import java.awt.*;

/**
 * TV interface mode constants for 10-foot UI.
 * Optimized for viewing from a distance (e.g., TV display) with:
 * - Large fonts for readability
 * - Big touch targets for remote/touch interaction
 * - High contrast
 * - Simplified layout
 */
public final class TvMode {
    
    /**
     * Enable TV mode for all UI components.
     * Controlled via system property "zetdc.tvMode":
     * - true  (default) for TV display / 10-foot UI
     * - false for standard laptop/desktop UI
     */
    public static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty("zetdc.tvMode", "true"));
    
    /** Enable touch support for both TV and laptops. Touch = tap, long-press, pinch-zoom. */
    public static final boolean TOUCH_ENABLED = true;
    
    // Font sizes (TV: large for distance viewing)
    public static final int FONT_LABEL = ENABLED ? 24 : 12;
    public static final int FONT_BUTTON = ENABLED ? 22 : 12;
    public static final int FONT_MENU = ENABLED ? 20 : 12;
    public static final int FONT_TITLE = ENABLED ? 28 : 14;
    public static final int FONT_STATUS = ENABLED ? 20 : 11;
    public static final int FONT_INPUT = ENABLED ? 24 : 12;
    public static final int FONT_LIST = ENABLED ? 22 : 12;
    
    // Component sizes
    public static final int BUTTON_HEIGHT = ENABLED ? 56 : 28;
    public static final int BUTTON_MIN_WIDTH = ENABLED ? 140 : 80;
    public static final int INPUT_HEIGHT = ENABLED ? 48 : 24;
    public static final int LIST_ROW_HEIGHT = ENABLED ? 56 : 20;
    public static final int SCROLLBAR_WIDTH = ENABLED ? 24 : 16;
    public static final int PADDING = ENABLED ? 16 : 8;
    public static final int SPACING = ENABLED ? 12 : 6;
    
    // Panel dimensions
    public static final int LEFT_PANEL_WIDTH = ENABLED ? 320 : 250;
    public static final int MIN_WINDOW_WIDTH = ENABLED ? 1280 : 800;
    public static final int MIN_WINDOW_HEIGHT = ENABLED ? 720 : 600;
    
    // Splash screen
    public static final int SPLASH_WIDTH = ENABLED ? 800 : 400;
    public static final int SPLASH_HEIGHT = ENABLED ? 500 : 300;
    public static final int SPLASH_FONT = ENABLED ? 28 : 14;
    
    private TvMode() {}
    
    public static Font getLabelFont() {
        return new Font("Arial", Font.PLAIN, FONT_LABEL);
    }
    
    public static Font getButtonFont() {
        return new Font("Arial", Font.PLAIN, FONT_BUTTON);
    }
    
    public static Font getMenuFont() {
        return new Font("Arial", Font.PLAIN, FONT_MENU);
    }
    
    public static Font getTitleFont() {
        return new Font("Arial", Font.BOLD, FONT_TITLE);
    }
    
    public static Font getStatusFont() {
        return new Font("Arial", Font.PLAIN, FONT_STATUS);
    }
    
    public static Font getInputFont() {
        return new Font("Arial", Font.PLAIN, FONT_INPUT);
    }
    
    public static Font getListFont() {
        return new Font("Arial", Font.PLAIN, FONT_LIST);
    }
    
    public static void applyButtonStyle(javax.swing.JButton btn) {
        if (ENABLED) {
            btn.setFont(getButtonFont());
            btn.setPreferredSize(new Dimension(BUTTON_MIN_WIDTH, BUTTON_HEIGHT));
            btn.setMinimumSize(new Dimension(BUTTON_MIN_WIDTH, BUTTON_HEIGHT));
        }
    }
    
    public static void applyToggleButtonStyle(javax.swing.JToggleButton btn) {
        if (ENABLED) {
            btn.setFont(getButtonFont());
            btn.setPreferredSize(new Dimension(BUTTON_MIN_WIDTH, BUTTON_HEIGHT));
            btn.setMinimumSize(new Dimension(BUTTON_MIN_WIDTH, BUTTON_HEIGHT));
        }
    }
}
