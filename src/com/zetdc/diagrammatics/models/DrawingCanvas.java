package com.zetdc.diagrammatics.models;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Custom canvas for drawing shapes and handling user interactions
 */
public class DrawingCanvas extends JPanel {
    
    private ShapeType currentTool = ShapeType.SELECT;
    
    // Drawing state
    private double startX, startY;
    private boolean isDrawing = false;
    private DrawableShape currentShape = null;
    
    // Shape management
    private List<DrawableShape> shapes = new ArrayList<>();
    private List<DrawableShape> selectedShapes = new ArrayList<>();
    
    // Undo/Redo functionality
    private Stack<List<DrawableShape>> undoStack = new Stack<>();
    private Stack<List<DrawableShape>> redoStack = new Stack<>();
    
    // Drawing properties
    private Color fillColor = new Color(173, 216, 230);
    private Color strokeColor = Color.BLACK;
    private int strokeWidth = 2;
    
    public DrawingCanvas() {
        super();
        setupEventHandlers();
        saveState(); // Initial state for undo
    }
    
    private void setupEventHandlers() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                startX = e.getX();
                startY = e.getY();
                isDrawing = true;
                
                switch (currentTool) {
                    case SELECT:
                        handleSelectPressed(e);
                        break;
                    case RECTANGLE:
                        createRectangle();
                        break;
                    case CIRCLE:
                        createCircle();
                        break;
                    case LINE:
                        createLine();
                        break;
                    case TEXT:
                        createText();
                        break;
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (isDrawing && currentShape != null) {
                    // Finalize the shape
                    if (currentTool == ShapeType.RECTANGLE || currentTool == ShapeType.CIRCLE) {
                        // Only add if the shape has some size
                        if (Math.abs(currentShape.getEndX() - currentShape.getStartX()) > 5 || 
                            Math.abs(currentShape.getEndY() - currentShape.getStartY()) > 5) {
                            shapes.add(currentShape);
                            saveState();
                        }
                    } else if (currentTool == ShapeType.LINE) {
                        if (Math.abs(currentShape.getEndX() - currentShape.getStartX()) > 5 || 
                            Math.abs(currentShape.getEndY() - currentShape.getStartY()) > 5) {
                            shapes.add(currentShape);
                            saveState();
                        }
                    }
                }
                
                isDrawing = false;
                currentShape = null;
                repaint();
            }
        });
        
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (!isDrawing || currentShape == null) {
                    return;
                }
                
                double currentX = e.getX();
                double currentY = e.getY();
                
                switch (currentTool) {
                    case RECTANGLE:
                    case CIRCLE:
                        updateShapeBounds(currentX, currentY);
                        break;
                    case LINE:
                        updateLineEnd(currentX, currentY);
                        break;
                }
                
                repaint();
            }
        });
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw all shapes
        for (DrawableShape shape : shapes) {
            shape.draw(g2d);
        }
        
        // Draw current shape being created
        if (currentShape != null) {
            currentShape.draw(g2d);
        }
        
        // Draw selection indicators
        g2d.setColor(Color.BLUE);
        g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{5.0f}, 0.0f));
        for (DrawableShape shape : selectedShapes) {
            drawSelectionIndicator(g2d, shape);
        }
    }
    
    private void drawSelectionIndicator(Graphics2D g2d, DrawableShape shape) {
        double x = Math.min(shape.getStartX(), shape.getEndX());
        double y = Math.min(shape.getStartY(), shape.getEndY());
        double width = Math.abs(shape.getEndX() - shape.getStartX());
        double height = Math.abs(shape.getEndY() - shape.getStartY());
        
        g2d.drawRect((int)(x - 2), (int)(y - 2), (int)(width + 4), (int)(height + 4));
    }
    
    private void handleSelectPressed(MouseEvent e) {
        // Clear previous selection
        selectedShapes.clear();
        
        // Find shapes at the click point
        for (DrawableShape shape : shapes) {
            if (shape.contains(e.getX(), e.getY())) {
                selectedShapes.add(shape);
            }
        }
        
        repaint();
    }
    
    private void createRectangle() {
        currentShape = new DrawableShape(ShapeType.RECTANGLE, startX, startY, startX, startY);
        currentShape.setFillColor(fillColor);
        currentShape.setStrokeColor(strokeColor);
        currentShape.setStrokeWidth(strokeWidth);
    }
    
    private void createCircle() {
        currentShape = new DrawableShape(ShapeType.CIRCLE, startX, startY, startX, startY);
        currentShape.setFillColor(fillColor);
        currentShape.setStrokeColor(strokeColor);
        currentShape.setStrokeWidth(strokeWidth);
    }
    
    private void createLine() {
        currentShape = new DrawableShape(ShapeType.LINE, startX, startY, startX, startY);
        currentShape.setStrokeColor(strokeColor);
        currentShape.setStrokeWidth(strokeWidth);
    }
    
    private void createText() {
        // For text, we'll create a simple text input dialog
        String text = JOptionPane.showInputDialog(this, "Enter text:", "Text Input", JOptionPane.QUESTION_MESSAGE);
        if (text != null && !text.trim().isEmpty()) {
            currentShape = new DrawableShape(ShapeType.TEXT, startX, startY, startX + 100, startY + 20);
            currentShape.setFillColor(fillColor);
            currentShape.setStrokeColor(strokeColor);
            currentShape.setStrokeWidth(strokeWidth);
            currentShape.setText(text);
            
            shapes.add(currentShape);
            saveState();
            currentShape = null;
            repaint();
        }
    }
    
    private void updateShapeBounds(double currentX, double currentY) {
        if (currentShape != null) {
            currentShape.setEndX(currentX);
            currentShape.setEndY(currentY);
        }
    }
    
    private void updateLineEnd(double currentX, double currentY) {
        if (currentShape != null) {
            currentShape.setEndX(currentX);
            currentShape.setEndY(currentY);
        }
    }
    
    // Public methods for controller interaction
    public void setCurrentTool(ShapeType tool) {
        this.currentTool = tool;
        selectedShapes.clear();
        repaint();
    }
    
    public void setFillColor(Color color) {
        this.fillColor = color;
    }
    
    public void setStrokeColor(Color color) {
        this.strokeColor = color;
    }
    
    public void setStrokeWidth(int width) {
        this.strokeWidth = width;
    }
    
    public void deleteSelectedShapes() {
        if (!selectedShapes.isEmpty()) {
            shapes.removeAll(selectedShapes);
            selectedShapes.clear();
            saveState();
            repaint();
        }
    }
    
    public void clear() {
        shapes.clear();
        selectedShapes.clear();
        saveState();
        repaint();
    }
    
    public void undo() {
        if (!undoStack.isEmpty()) {
            redoStack.push(new ArrayList<>(shapes));
            shapes = new ArrayList<>(undoStack.pop());
            selectedShapes.clear();
            repaint();
        }
    }
    
    public void redo() {
        if (!redoStack.isEmpty()) {
            undoStack.push(new ArrayList<>(shapes));
            shapes = new ArrayList<>(redoStack.pop());
            selectedShapes.clear();
            repaint();
        }
    }
    
    private void saveState() {
        undoStack.push(new ArrayList<>(shapes));
        redoStack.clear(); // Clear redo stack when new action is performed
    }
    
    public List<DrawableShape> getShapes() {
        return new ArrayList<>(shapes);
    }
    
    public void loadShapes(List<DrawableShape> newShapes) {
        shapes.clear();
        selectedShapes.clear();
        shapes.addAll(newShapes);
        saveState();
        repaint();
    }
}