package com.zetdc.diagrammatics.models;

/**
 * Enumeration of available shape types for the diagramming application
 */
public enum ShapeType {
    SELECT("Select"),
    RECTANGLE("Rectangle"),
    CIRCLE("Circle"),
    LINE("Line"),
    TEXT("Text");
    
    private final String displayName;
    
    ShapeType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}


