package com.zetdc.diagrammatics.utils;

import javax.swing.*;
import java.awt.*;

/**
 * Touch support utilities for TV and laptop touch screens.
 * Applies touch-friendly UI settings: larger scrollbars, tap targets, etc.
 */
public final class TouchSupport {
    
    private TouchSupport() {}
    
    /**
     * Apply touch-friendly UI defaults. Call early in application startup.
     */
    public static void applyTouchFriendlyUI() {
        if (!TvMode.TOUCH_ENABLED) return;
        
        try {
            // Thicker scrollbars for touch (easier to grab)
            UIManager.put("ScrollBar.width", Integer.valueOf(TvMode.SCROLLBAR_WIDTH));
        } catch (Exception e) {
            System.out.println("Touch UI settings: " + e.getMessage());
        }
    }
    
    /**
     * Configure a JScrollPane for touch-friendly scrolling.
     */
    public static void configureTouchScrollPane(JScrollPane scrollPane) {
        if (!TvMode.TOUCH_ENABLED) return;
        
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(24);
        scrollPane.getVerticalScrollBar().setBlockIncrement(120);
        scrollPane.getHorizontalScrollBar().setBlockIncrement(120);
    }
}
