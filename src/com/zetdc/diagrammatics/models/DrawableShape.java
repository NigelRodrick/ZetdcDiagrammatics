package com.zetdc.diagrammatics.models;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/**
 * Represents a drawable shape on the canvas
 */
public class DrawableShape {
    
    private ShapeType type;
    private double startX, startY, endX, endY;
    private Color fillColor;
    private Color strokeColor;
    private int strokeWidth;
    private String text = "";
    
    public DrawableShape(ShapeType type, double startX, double startY, double endX, double endY) {
        this.type = type;
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
    }
    
    public void draw(Graphics2D g2d) {
        g2d.setStroke(new BasicStroke(strokeWidth));
        
        double x = Math.min(startX, endX);
        double y = Math.min(startY, endY);
        double width = Math.abs(endX - startX);
        double height = Math.abs(endY - startY);
        
        switch (type) {
            case RECTANGLE:
                if (fillColor != null) {
                    g2d.setColor(fillColor);
                    g2d.fill(new Rectangle2D.Double(x, y, width, height));
                }
                if (strokeColor != null) {
                    g2d.setColor(strokeColor);
                    g2d.draw(new Rectangle2D.Double(x, y, width, height));
                }
                break;
                
            case CIRCLE:
                if (fillColor != null) {
                    g2d.setColor(fillColor);
                    g2d.fill(new Ellipse2D.Double(x, y, width, height));
                }
                if (strokeColor != null) {
                    g2d.setColor(strokeColor);
                    g2d.draw(new Ellipse2D.Double(x, y, width, height));
                }
                break;
                
            case LINE:
                if (strokeColor != null) {
                    g2d.setColor(strokeColor);
                    g2d.drawLine((int)startX, (int)startY, (int)endX, (int)endY);
                }
                break;
                
            case TEXT:
                if (fillColor != null) {
                    g2d.setColor(fillColor);
                } else {
                    g2d.setColor(Color.BLACK);
                }
                g2d.setFont(new Font("Arial", Font.PLAIN, 14));
                FontMetrics fm = g2d.getFontMetrics();
                int textHeight = fm.getAscent();
                g2d.drawString(text, (int)x, (int)y + textHeight);
                break;
        }
    }
    
    public boolean contains(double x, double y) {
        double minX = Math.min(startX, endX);
        double maxX = Math.max(startX, endX);
        double minY = Math.min(startY, endY);
        double maxY = Math.max(startY, endY);
        
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }
    
    // Getters and setters
    public ShapeType getType() {
        return type;
    }
    
    public void setType(ShapeType type) {
        this.type = type;
    }
    
    public double getStartX() {
        return startX;
    }
    
    public void setStartX(double startX) {
        this.startX = startX;
    }
    
    public double getStartY() {
        return startY;
    }
    
    public void setStartY(double startY) {
        this.startY = startY;
    }
    
    public double getEndX() {
        return endX;
    }
    
    public void setEndX(double endX) {
        this.endX = endX;
    }
    
    public double getEndY() {
        return endY;
    }
    
    public void setEndY(double endY) {
        this.endY = endY;
    }
    
    public Color getFillColor() {
        return fillColor;
    }
    
    public void setFillColor(Color fillColor) {
        this.fillColor = fillColor;
    }
    
    public Color getStrokeColor() {
        return strokeColor;
    }
    
    public void setStrokeColor(Color strokeColor) {
        this.strokeColor = strokeColor;
    }
    
    public int getStrokeWidth() {
        return strokeWidth;
    }
    
    public void setStrokeWidth(int strokeWidth) {
        this.strokeWidth = strokeWidth;
    }
    
    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
    }
}