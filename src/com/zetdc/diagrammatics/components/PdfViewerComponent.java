package com.zetdc.diagrammatics.components;

import com.zetdc.diagrammatics.utils.PdfRenderer;
import com.zetdc.diagrammatics.utils.TvMode;
import com.zetdc.diagrammatics.utils.TouchSupport;
import com.zetdc.diagrammatics.utils.UserService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.image.BufferedImage;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.io.IOException;

/**
 * Component for displaying PDF pages as images
 */
public class PdfViewerComponent extends JPanel {

    /** Constant marker radius (pixels) for on-screen and exported PDF so all markers look the same size. */
    public static final int MARKER_RADIUS_PX = 8;
    
    private File currentPdfFile;
    private int currentPage = 1;
    private int totalPages = 1;
    private float scale = 1.0f;
    private BufferedImage currentPageImage;
    
    // Mouse interaction variables
    private boolean isMouseOver = false;
    private boolean isPanning = false;
    private Point lastMousePosition;
    private Point imageOffset = new Point(0, 0);
    /** When set, applied to imageOffset when the next page image load completes (e.g. after zoom on TV). */
    private Point pendingImageOffsetAfterLoad = null;

    // Touch screen support variables (enabled for TV and touch laptops)
    private boolean touchEnabled = TvMode.TOUCH_ENABLED;
    private Point touchStartPoint = null;
    private long touchStartTime = 0;
    private static final long LONG_PRESS_DURATION = 500; // milliseconds for context menu
    private static final long EDIT_MODE_DURATION = 1000; // 1 second for edit mode
    private javax.swing.Timer longPressTimer;
    private javax.swing.Timer editModeTimer;
    private Point firstTouchPoint = null;
    private Point secondTouchPoint = null;
    private float initialPinchDistance = 0;
    private float initialScale = 1.0f;
    private boolean isPinching = false;
    private boolean editModeActivated = false;
    
    
    // Hover tracking
    private int hoveredMarkerIndex = -1;
    private int hoveredLineIndex = -1;
    private int hoveredTextIndex = -1;
    private javax.swing.Timer hoverTimer;
    
    // Performance optimization variables
    private boolean isRendering = false;
    private javax.swing.Timer repaintTimer;
    private static final int REPAINT_DELAY = 16; // ~60 FPS
    private BufferedImage cachedPageImage = null;
    private float cachedScale = -1;
    private int cachedPage = -1;
    private javax.swing.Timer mouseMoveTimer;
    private static final int MOUSE_MOVE_DELAY = 10; // Debounce mouse moves
    private JPopupMenu hoverTooltip;
    private Point hoverPosition;
    
    private JLabel pageLabel;
    private ImageCanvas imageCanvas;
    private com.zetdc.diagrammatics.controllers.MainController mainController;
    private JSlider scaleSlider;
    private JButton prevPageButton;
    private JButton nextPageButton;
    private JButton zoomInButton;
    private JButton zoomOutButton;
    private JButton fitToWidthButton;
    private JButton fitToFullButton;
    private JToggleButton markerToggleButton;
    // No standalone delete button; deletion via context menu
    private JButton doneMovingButton;
    private JToggleButton lineToggleButton;
    private JToggleButton textToggleButton;
    private JButton editModeButton;  // Edit - prompts for credentials, then shows edit buttons
    private boolean editModeActive = false;
    private UserService userService;

    // Markers per page, stored in original PDF pixel coordinates (scale=1.0)
    private static class Marker {
        final Point2D.Float position; // in original PDF pixel coordinates
        String text;
        Color color; // marker color
        String createdBy; // username of creator
        float baseRadius; // logical radius in "zoom 1.0" units so markers scale nicely
        
        Marker(Point2D.Float position, String text) {
            this.position = position;
            this.text = text;
            this.color = new Color(255, 0, 0, 220); // default bright red color
            this.createdBy = null;
            this.baseRadius = 8f;
        }
        
        Marker(Point2D.Float position, String text, Color color) {
            this.position = position;
            this.text = text;
            this.color = color;
            this.createdBy = null;
            this.baseRadius = 8f;
        }

        Marker(Point2D.Float position, String text, Color color, String createdBy) {
            this.position = position;
            this.text = text;
            this.color = color;
            this.createdBy = createdBy;
            this.baseRadius = 8f;
        }
    }
    private final Map<Integer, List<Marker>> pageIndexToMarkers = new HashMap<>();
    private int selectedMarkerIndex = -1; // index in current page markers
    private boolean isDraggingMarker = false;
    private boolean isEditingMarkerPosition = false; // waiting for next click to set position
    private javax.swing.Timer wobbleTimer;
    private int wobblePhase = 0;
    private javax.swing.Timer resizeDebounceTimer;
    
    // Line types
    public enum LineType {
        POLYLINE, STRAIGHT_LINE, CIRCLE
    }
    
    // Lines
    private static class Line {
        final Point2D.Float start; // PDF coords
        final Point2D.Float end;   // PDF coords
        String text;               // optional details label
        LineType type;             // line type
        float radius;              // for circles
        String createdBy;          // username of creator
        
        Line(Point2D.Float start, Point2D.Float end) { 
            this.start = start; 
            this.end = end; 
            this.text = null; 
            this.type = LineType.POLYLINE;
            this.radius = 0;
            this.createdBy = null;
        }
        
        Line(Point2D.Float start, Point2D.Float end, LineType type) { 
            this.start = start; 
            this.end = end; 
            this.text = null; 
            this.type = type;
            this.radius = 0;
            this.createdBy = null;
        }
        
        // Circle constructor
        Line(Point2D.Float center, float radius) {
            this.start = center;
            this.end = center;
            this.text = null;
            this.type = LineType.CIRCLE;
            this.radius = radius;
            this.createdBy = null;
        }

        Line(Point2D.Float start, Point2D.Float end, LineType type, String createdBy, float radius) {
            this.start = start;
            this.end = end;
            this.text = null;
            this.type = type;
            this.radius = radius;
            this.createdBy = createdBy;
        }
    }
    private final Map<Integer, List<Line>> pageIndexToLines = new HashMap<>();
    // For interactive polyline creation: sequence of points; right-click to finalize
    private java.util.List<Point2D.Float> constructingPolylinePoints = null;
    // Line drawing state
    private LineType currentLineType = LineType.POLYLINE;
    private Point2D.Float straightLineStart = null;
    private Point2D.Float circleCenter = null;
    
    // Text annotations
    private static class TextAnnotation {
        final Point2D.Float position; // PDF coords
        String text;
        Color backgroundColor;
        String createdBy; // username of creator
        
        TextAnnotation(Point2D.Float position, String text) {
            this.position = position;
            this.text = text;
            this.backgroundColor = new Color(255, 255, 0, 180); // default yellow background
            this.createdBy = null;
        }

        TextAnnotation(Point2D.Float position, String text, String createdBy) {
            this.position = position;
            this.text = text;
            this.backgroundColor = new Color(255, 255, 0, 180); // default yellow background
            this.createdBy = createdBy;
        }
    }
    private final Map<Integer, List<TextAnnotation>> pageIndexToTexts = new HashMap<>();
    private int selectedTextIndex = -1; // index in current page texts
    private int selectedLineIndex = -1; // index in current page lines
    
    private String currentEditorUsername;
    // Touch-friendly polyline double-tap finalize support
    private long lastPolylineTapTime = 0L;

    public PdfViewerComponent(UserService userService) {
        this.userService = userService != null ? userService : new UserService();
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        setupTouchSupport();
        setupHoverTimer();
        setupPerformanceOptimizations();
        setEditButtonsVisible(false);
    }

    public String getCurrentEditorUsername() {
        return currentEditorUsername;
    }

    public boolean isEditModeActive() {
        return editModeActive;
    }

    /**
     * Delete whichever annotation is currently selected, if any.
     * Order of precedence: line, then marker, then text.
     */
    public void deleteCurrentSelection() {
        if (selectedLineIndex != -1) {
            deleteSelectedLine();
            return;
        }
        if (selectedMarkerIndex != -1) {
            deleteSelectedMarker();
            return;
        }
        if (selectedTextIndex != -1) {
            deleteSelectedText();
        }
    }
    
    public void setMainController(com.zetdc.diagrammatics.controllers.MainController controller) {
        this.mainController = controller;
    }
    
    /**
     * Convenience methods so the main window can directly open
     * the add-marker / add-line / add-text modes from a single
     * "Make Changes" button.
     */
    public void startAddMarkerMode() {
        if (!editModeActive) {
            JOptionPane.showMessageDialog(this,
                "You must enter Edit mode first (via the Edits menu).",
                "Edit Mode Required",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!markerToggleButton.isSelected()) {
            // Use doClick so all existing listeners run
            markerToggleButton.doClick();
        }
    }
    
    public void startAddLineMode() {
        if (!editModeActive) {
            JOptionPane.showMessageDialog(this,
                "You must enter Edit mode first (via the Edits menu).",
                "Edit Mode Required",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!lineToggleButton.isSelected()) {
            lineToggleButton.doClick();
        }
    }
    
    public void startAddTextMode() {
        if (!editModeActive) {
            JOptionPane.showMessageDialog(this,
                "You must enter Edit mode first (via the Edits menu).",
                "Edit Mode Required",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!textToggleButton.isSelected()) {
            textToggleButton.doClick();
        }
    }
    
    private void notifyChangesMade() {
        if (mainController != null) {
            mainController.markAsUnsaved();
        }
    }
    
    private void initializeComponents() {
        // Page navigation
        prevPageButton = new JButton("◀ Previous");
        nextPageButton = new JButton("Next ▶");
        pageLabel = new JLabel("Page 1 of 1");
        TvMode.applyButtonStyle(prevPageButton);
        TvMode.applyButtonStyle(nextPageButton);
        if (TvMode.ENABLED) pageLabel.setFont(TvMode.getLabelFont());
        
        // Zoom controls
        zoomInButton = new JButton("Zoom In");
        zoomOutButton = new JButton("Zoom Out");
        fitToWidthButton = new JButton("Fit to Width");
        fitToFullButton = new JButton("Fit to Full");
        TvMode.applyButtonStyle(zoomInButton);
        TvMode.applyButtonStyle(zoomOutButton);
        TvMode.applyButtonStyle(fitToWidthButton);
        TvMode.applyButtonStyle(fitToFullButton);

        // Marker controls
        markerToggleButton = new JToggleButton("Add Marker");
        TvMode.applyToggleButtonStyle(markerToggleButton);
        // No delete button; handled via context menu per marker
        doneMovingButton = new JButton("Done Moving");
        TvMode.applyButtonStyle(doneMovingButton);
        doneMovingButton.setVisible(false);
        lineToggleButton = new JToggleButton("Add Line");
        TvMode.applyToggleButtonStyle(lineToggleButton);
        // Add right-click context menu for line type selection
        lineToggleButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showLineTypeMenu(e.getX(), e.getY());
                }
            }
        });
        textToggleButton = new JToggleButton("Add Text");
        TvMode.applyToggleButtonStyle(textToggleButton);
        
        // Edit button - prompts for credentials, then shows Add Marker/Line/Text
        editModeButton = new JButton("Edit");
        TvMode.applyButtonStyle(editModeButton);
        
        // Scale slider - removed to give more space to visual view
        
        // Image display canvas
        imageCanvas = new ImageCanvas();
        imageCanvas.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        
        // Initial state
        updateButtonStates();
    }
    
    private void setupLayout() {
        setLayout(new BorderLayout());

        // Main content area – fill directly under menu bar for both TV and laptop
        JScrollPane scrollPane = new JScrollPane(imageCanvas);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        TouchSupport.configureTouchScrollPane(scrollPane);

        add(scrollPane, BorderLayout.CENTER);
    }
    
    private void setupEventHandlers() {
        zoomInButton.addActionListener(e -> zoomIn());
        zoomOutButton.addActionListener(e -> zoomOut());
        fitToWidthButton.addActionListener(e -> fitToWidth());
        fitToFullButton.addActionListener(e -> fitToFullVisibility());
        
        editModeButton.addActionListener(e -> toggleEditMode());

        markerToggleButton.addActionListener(e -> {
            // When in marker mode, disable panning cursor hint
            if (markerToggleButton.isSelected()) {
                imageCanvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
            } else {
                imageCanvas.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
            // Turning on marker mode turns off line and text modes
            if (markerToggleButton.isSelected()) {
                lineToggleButton.setSelected(false);
                textToggleButton.setSelected(false);
                constructingPolylinePoints = null;
                imageCanvas.repaint();
            }
        });

        // Done moving ends edit-position mode (kept for accessibility but hidden during edit mode)
        doneMovingButton.addActionListener(e -> stopEditPositionMode());

        lineToggleButton.addActionListener(e -> {
            if (lineToggleButton.isSelected()) {
                // Turn ON line mode
                markerToggleButton.setSelected(false);
                textToggleButton.setSelected(false);
                stopEditPositionMode();
                constructingPolylinePoints = null;
                imageCanvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
            } else {
                // Turn OFF line mode
                constructingPolylinePoints = null;
                straightLineStart = null; // Clear any pending straight line start point
                imageCanvas.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                imageCanvas.repaint();
            }
        });
        
        textToggleButton.addActionListener(e -> {
            if (textToggleButton.isSelected()) {
                markerToggleButton.setSelected(false);
                lineToggleButton.setSelected(false);
                stopEditPositionMode();
                constructingPolylinePoints = null;
                // Use crosshair cursor to indicate text placement mode
                imageCanvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
                pageLabel.setText("Click to add text annotation");
            } else {
                imageCanvas.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                pageLabel.setText("Page " + (currentPage + 1) + " of " + totalPages);
                imageCanvas.repaint();
            }
        });
        
        // Add mouse wheel zoom functionality
        imageCanvas.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                // Re-enable wheel-based zooming: scroll up = zoom in, scroll down = zoom out.
                // We keep left-drag panning disabled so markers stay visually stable.

                // Disable zoom while a polyline is being constructed,
                // to keep in-progress lines visually stable.
                if (lineToggleButton.isSelected() &&
                    currentLineType == LineType.POLYLINE &&
                    constructingPolylinePoints != null &&
                    !constructingPolylinePoints.isEmpty()) {
                    return;
                }

                // Only zoom when mouse is over the image
                if (isMouseOver) {
                    boolean zoomIn = e.getWheelRotation() < 0;
                    zoomAtCanvasPoint(zoomIn, e.getPoint());
                }
            }
        });
        
        // Add mouse listeners for hover detection and panning
        imageCanvas.addMouseListener(new MouseListener() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isMouseOver = true;
                if (markerToggleButton.isSelected() || lineToggleButton.isSelected() || textToggleButton.isSelected()) {
                    imageCanvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
                } else {
                    imageCanvas.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                isMouseOver = false;
                isPanning = false;
                imageCanvas.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                if (!isMouseOver) return;
                
                // Handle touch start for touch screen support
                handleTouchStart(e);

                // Right-click behavior
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    // If drawing a polyline, finalize on right-click
                    if (lineToggleButton.isSelected() && constructingPolylinePoints != null && !constructingPolylinePoints.isEmpty()) {
                        finalizePolyline();
                        throttledRepaint();
                        return;
                    }
                    if (isEditingMarkerPosition) {
                        showMarkerContextMenu(e.getX(), e.getY());
                        return;
                    }
                    int hitIndex = hitTestMarker(e.getPoint());
                    if (hitIndex != -1) {
                        selectMarker(hitIndex);
                        showMarkerContextMenu(e.getX(), e.getY());
                        return;
                    }
                    
                    int textHitIndex = hitTestText(e.getPoint());
                    if (textHitIndex != -1) {
                        selectText(textHitIndex);
                        showTextContextMenu(e.getX(), e.getY());
                        return;
                    }
                }

                if (SwingUtilities.isLeftMouseButton(e)) {
                    // Double-click on existing annotations: open edit/delete context menus
                    if (e.getClickCount() == 2) {
                        int markerHit = hitTestMarker(e.getPoint());
                        if (markerHit != -1) {
                            selectMarker(markerHit);
                            showMarkerContextMenu(e.getX(), e.getY());
                            return;
                        }
                        int textHitIndex = hitTestText(e.getPoint());
                        if (textHitIndex != -1) {
                            selectText(textHitIndex);
                            showTextContextMenu(e.getX(), e.getY());
                            return;
                        }
                        int lineHitIndex = hitTestLine(e.getPoint());
                        if (lineHitIndex != -1) {
                            selectLine(lineHitIndex);
                            showLineContextMenu(e.getX(), e.getY());
                            return;
                        }
                    }

                    // If editing position of a selected marker, set it to this click
                    if (isEditingMarkerPosition && selectedMarkerIndex != -1) {
                        List<Marker> markers = pageIndexToMarkers.get(currentPage);
                        if (markers != null && selectedMarkerIndex < markers.size()) {
                            Point2D.Float pdfPoint = toPdfPoint(e.getPoint());
                            if (pdfPoint != null) {
                                Marker m = markers.get(selectedMarkerIndex);
                                m.position.x = pdfPoint.x;
                                m.position.y = pdfPoint.y;
                                throttledRepaint();
                                return;
                            }
                        }
                    }
                    if (markerToggleButton.isSelected()) {
                        addMarkerFromClick(e.getPoint());
                        return;
                    }

                    if (lineToggleButton.isSelected()) {
                        Point2D.Float pdfPoint = toPdfPoint(e.getPoint());
                        if (pdfPoint == null) return;
                        
                        switch (currentLineType) {
                            case POLYLINE:
                                if (constructingPolylinePoints == null) constructingPolylinePoints = new java.util.ArrayList<>();
                                constructingPolylinePoints.add(pdfPoint);
                                pageLabel.setText("Polyline: " + constructingPolylinePoints.size() + " points (right-click to finish)");
                                break;
                            case STRAIGHT_LINE:
                                if (straightLineStart == null) {
                                    straightLineStart = pdfPoint;
                                    pageLabel.setText("Straight Line: Click second point");
                                } else {
                                    // Ask for line details
                                    String details = JOptionPane.showInputDialog(PdfViewerComponent.this, "Enter line details:", "Line Details", JOptionPane.PLAIN_MESSAGE);
                                    String trimmed = details == null ? null : details.trim();
                                    
                                    // Create straight line
                                    List<Line> list = pageIndexToLines.computeIfAbsent(currentPage, k -> new ArrayList<>());
                                    Line straightLine = new Line(straightLineStart, pdfPoint, LineType.STRAIGHT_LINE);
                                    if (trimmed != null && !trimmed.isEmpty()) straightLine.text = trimmed;
                                    list.add(straightLine);
                                    straightLineStart = null;
                                    pageLabel.setText("Straight line added");
                                    notifyChangesMade();
                                    
                                    // Terminate line creation mode after adding the line
                                    lineToggleButton.setSelected(false);
                                }
                                break;
                            case CIRCLE:
                                if (circleCenter == null) {
                                    circleCenter = pdfPoint;
                                    pageLabel.setText("Circle: Click to set radius");
                                } else {
                                    // Calculate radius and create circle
                                    float radius = (float) Math.sqrt(
                                        Math.pow(pdfPoint.x - circleCenter.x, 2) + 
                                        Math.pow(pdfPoint.y - circleCenter.y, 2)
                                    );
                                    
                                    // Ask for circle details
                                    String details = JOptionPane.showInputDialog(PdfViewerComponent.this, "Enter circle details:", "Circle Details", JOptionPane.PLAIN_MESSAGE);
                                    String trimmed = details == null ? null : details.trim();
                                    
                                    List<Line> list = pageIndexToLines.computeIfAbsent(currentPage, k -> new ArrayList<>());
                                    Line circle = new Line(circleCenter, radius);
                                    if (trimmed != null && !trimmed.isEmpty()) circle.text = trimmed;
                                    list.add(circle);
                                    circleCenter = null;
                                    pageLabel.setText("Circle added");
                                    notifyChangesMade();
                                    
                                    // Terminate line creation mode after adding the circle
                                    lineToggleButton.setSelected(false);
                                }
                                break;
                        }
                        throttledRepaint();
                        return;
                    }
                    
                    if (textToggleButton.isSelected()) {
                        addTextFromClick(e.getPoint());
                        return;
                    }

                    int hitIndex = hitTestMarker(e.getPoint());
                    if (hitIndex != -1) {
                        // Start dragging this marker
                        selectMarker(hitIndex);
                        isDraggingMarker = true;
                        imageCanvas.setCursor(new Cursor(Cursor.MOVE_CURSOR));
                        return;
                    }
                    
                    int textHitIndex = hitTestText(e.getPoint());
                    if (textHitIndex != -1) {
                        selectText(textHitIndex);
                        return;
                    }
                    
                    int lineHitIndex = hitTestLine(e.getPoint());
                    if (lineHitIndex != -1) {
                        selectLine(lineHitIndex);
                        return;
                    }

                    // Otherwise, we no longer start panning with left-click drag.
                    // Map movement should be done via scrollbars or mouse wheel only
                    // to avoid shifting marker positions visually.
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                // Handle touch end for touch screen support
                handleTouchEnd(e);
                
                if (isPanning) {
                    isPanning = false;
                    // Restore appropriate cursor based on active mode
                    if (markerToggleButton.isSelected() || lineToggleButton.isSelected() || textToggleButton.isSelected()) {
                        imageCanvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
                    } else {
                        imageCanvas.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    }
                }
                if (isDraggingMarker) {
                    isDraggingMarker = false;
                    imageCanvas.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }

                // On some platforms popup triggers fire on release
                if (e.isPopupTrigger()) {
                    // If currently drawing a polyline, treat right-click as finalize here too
                    if (lineToggleButton.isSelected() && constructingPolylinePoints != null && !constructingPolylinePoints.isEmpty()) {
                        finalizePolyline();
                        throttledRepaint();
                        return;
                    }
                    if (isEditingMarkerPosition) {
                        showMarkerContextMenu(e.getX(), e.getY());
                    } else {
                        int hitIndex = hitTestMarker(e.getPoint());
                        if (hitIndex != -1) {
                            selectMarker(hitIndex);
                            showMarkerContextMenu(e.getX(), e.getY());
                        } else {
                            int textHitIndex = hitTestText(e.getPoint());
                            if (textHitIndex != -1) {
                                selectText(textHitIndex);
                                showTextContextMenu(e.getX(), e.getY());
                            }
                        }
                    }
                }
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                // Handle line type selection on right-click of line button
                if (SwingUtilities.isRightMouseButton(e) && e.getSource() == lineToggleButton) {
                    showLineTypeMenu(e.getX(), e.getY());
                }
            }
        });
        
        // Add mouse motion listener for panning and hover detection
        imageCanvas.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                // Handle touch move for touch screen support
                handleTouchMove(e);

                // Disable panning while a polyline is being constructed
                // to keep in-progress lines visually stable.
                boolean blockingPolylineInProgress =
                    lineToggleButton.isSelected() &&
                    currentLineType == LineType.POLYLINE &&
                    constructingPolylinePoints != null &&
                    !constructingPolylinePoints.isEmpty();

                // Drag-panning via left mouse button is disabled; movement is via
                // scrollbars or mouse wheel only to keep markers visually stable.
                if (isDraggingMarker && selectedMarkerIndex != -1) {
                    List<Marker> markers = pageIndexToMarkers.get(currentPage);
                    if (markers != null && selectedMarkerIndex < markers.size()) {
                        Point2D.Float pdfPoint = toPdfPoint(e.getPoint());
                        if (pdfPoint != null) {
                            Marker m = markers.get(selectedMarkerIndex);
                            m.position.x = pdfPoint.x;
                            m.position.y = pdfPoint.y;
                            throttledRepaint();
                        }
                    }
                }
            }
            
            @Override
            public void mouseMoved(MouseEvent e) {
                // Track cursor for button-based zoom to center on pointer
                lastMousePosition = e.getPoint();
                hoverPosition = e.getPoint();
                
                // Debounce mouse move events for better performance
                mouseMoveTimer.stop();
                mouseMoveTimer = new javax.swing.Timer(MOUSE_MOVE_DELAY, ev -> {
                    if (isEditingMarkerPosition && selectedMarkerIndex != -1) {
                        List<Marker> markers = pageIndexToMarkers.get(currentPage);
                        if (markers != null && selectedMarkerIndex < markers.size()) {
                            Point2D.Float pdfPoint = toPdfPoint(e.getPoint());
                            if (pdfPoint != null) {
                                Marker m = markers.get(selectedMarkerIndex);
                                m.position.x = pdfPoint.x;
                                m.position.y = pdfPoint.y;
                                throttledRepaint();
                            }
                        }
                    } else if (lineToggleButton.isSelected()) {
                        // Preview while moving for all line types
                        if (currentLineType == LineType.POLYLINE && constructingPolylinePoints != null && !constructingPolylinePoints.isEmpty()) {
                            throttledRepaint();
                        } else if (currentLineType == LineType.STRAIGHT_LINE && straightLineStart != null) {
                            throttledRepaint();
                        } else if (currentLineType == LineType.CIRCLE && circleCenter != null) {
                            throttledRepaint();
                        }
                    } else {
                        // Check for hover over annotations
                        checkHoverOverAnnotations(e.getPoint());
                    }
                });
                mouseMoveTimer.setRepeats(false);
                mouseMoveTimer.start();
            }
        });
        
        // Add component listener to handle window resizing and full screen
        this.addComponentListener(new ComponentListener() {
            @Override
            public void componentResized(ComponentEvent e) {
                // Debounce resize events to avoid excessive re-rendering
                if (resizeDebounceTimer != null) {
                    resizeDebounceTimer.stop();
                }
                resizeDebounceTimer = new javax.swing.Timer(100, ev -> {
                    if (currentPageImage != null) {
                        fitToFullVisibility();
                    }
                });
                resizeDebounceTimer.setRepeats(false);
                resizeDebounceTimer.start();
            }
            
            @Override
            public void componentMoved(ComponentEvent e) {
                // No action needed
            }
            
            @Override
            public void componentShown(ComponentEvent e) {
                // No action needed
            }
            
            @Override
            public void componentHidden(ComponentEvent e) {
                // No action needed
            }
        });
        
        // Add keyboard support for deleting selected lines
        imageCanvas.setFocusable(true);
        imageCanvas.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                System.out.println("Key pressed: " + e.getKeyCode() + " (Delete=" + java.awt.event.KeyEvent.VK_DELETE + ")");
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DELETE) {
                    System.out.println("Delete key pressed. selectedLineIndex=" + selectedLineIndex + ", selectedMarkerIndex=" + selectedMarkerIndex + ", selectedTextIndex=" + selectedTextIndex);
                    if (selectedLineIndex != -1) {
                        deleteSelectedLine();
                    } else if (selectedMarkerIndex != -1) {
                        deleteSelectedMarker();
                    } else if (selectedTextIndex != -1) {
                        deleteSelectedText();
                    } else {
                        System.out.println("No item selected for deletion");
                    }
                }
            }
        });
        
        // Scale slider removed - zoom is now handled directly
    }
    
    /**
     * Setup touch screen support for multi-touch gestures
     */
    private void setupTouchSupport() {
        // Touch enabled for both TV and laptops (Windows converts touch to mouse events)
        touchEnabled = TvMode.TOUCH_ENABLED;
        if (touchEnabled) {
            try {
                System.setProperty("awt.multiTouch.enabled", "true");
                System.out.println("Touch support enabled (TV and laptop)");
            } catch (Exception e) {
                // Continue with touch enabled - mouse events work for touch
            }
        }
        
        // Setup long press timer for context menu
        longPressTimer = new javax.swing.Timer((int)LONG_PRESS_DURATION, e -> {
            if (touchStartPoint != null && !isPinching && !editModeActivated) {
                handleLongPress(touchStartPoint);
            }
        });
        longPressTimer.setRepeats(false);
        
        // Setup edit mode timer (1 second hold)
        editModeTimer = new javax.swing.Timer((int)EDIT_MODE_DURATION, e -> {
            if (touchStartPoint != null && !isPinching && !editModeActivated) {
                activateEditMode(touchStartPoint);
            }
        });
        editModeTimer.setRepeats(false);
        
        // Touch support is integrated into existing mouse handlers
        // The existing mouse listeners will call handleTouchStart/End/Move methods
    }
    
    /**
     * Handle touch start event
     */
    private void handleTouchStart(java.awt.event.MouseEvent e) {
        if (!touchEnabled) return;
        
        // Reset edit mode flag
        editModeActivated = false;
        
        // Cancel timers if already running
        if (longPressTimer != null) {
            longPressTimer.stop();
        }
        if (editModeTimer != null) {
            editModeTimer.stop();
        }
        
        // Track first touch point for long press detection
        if (firstTouchPoint == null) {
            firstTouchPoint = e.getPoint();
            touchStartPoint = e.getPoint();
            touchStartTime = System.currentTimeMillis();
            
            // Start edit mode timer (1 second hold for edit mode)
            if (editModeTimer != null) {
                editModeTimer.start();
            }
            
            // Start long press timer (for context menu on touch screens)
            if (longPressTimer != null) {
                longPressTimer.start();
            }
        } else if (secondTouchPoint == null && firstTouchPoint != null) {
            // Second touch point detected - start pinch gesture
            secondTouchPoint = e.getPoint();
            isPinching = true;
            initialPinchDistance = (float)firstTouchPoint.distance(secondTouchPoint);
            initialScale = scale;
            
            // Cancel timers when pinch starts
            if (longPressTimer != null) {
                longPressTimer.stop();
            }
            if (editModeTimer != null) {
                editModeTimer.stop();
            }
        }
    }
    
    /**
     * Handle touch move event
     */
    private void handleTouchMove(java.awt.event.MouseEvent e) {
        if (!touchEnabled) return;
        
        // Only handle touch-specific gestures (pinch-to-zoom)
        // Normal mouse panning is handled by existing mouse handlers
        if (isPinching && firstTouchPoint != null && secondTouchPoint != null) {
            // Update second touch point for pinch-to-zoom
            if (e.getPoint().distance(firstTouchPoint) > e.getPoint().distance(secondTouchPoint)) {
                // This is the second touch point
                secondTouchPoint = e.getPoint();
            } else {
                // This is the first touch point
                firstTouchPoint = e.getPoint();
            }
            
            // Calculate pinch distance
            float currentDistance = (float)firstTouchPoint.distance(secondTouchPoint);
            if (initialPinchDistance > 0) {
                // Calculate scale factor
                float scaleFactor = currentDistance / initialPinchDistance;
                float newScale = initialScale * scaleFactor;
                
                // Limit zoom range - re-render page at new scale so markers stay fixed (no drift)
                if (newScale >= 0.1f && newScale <= 5.0f) {
                    scale = newScale;
                    clearImageCache();
                    loadCurrentPage();
                }
            }
        }
        // Note: Single touch drag/panning is handled by existing mouse panning logic
    }
    
    /**
     * Handle touch end event
     */
    private void handleTouchEnd(java.awt.event.MouseEvent e) {
        if (!touchEnabled) return;
        
        // Cancel timers
        if (longPressTimer != null) {
            longPressTimer.stop();
        }
        if (editModeTimer != null) {
            editModeTimer.stop();
        }
        
        // Check if this was a tap (quick touch and release)
        if (firstTouchPoint != null && !isPinching && !editModeActivated) {
            long touchDuration = System.currentTimeMillis() - touchStartTime;
            if (touchDuration < LONG_PRESS_DURATION && touchStartPoint != null) {
                // Quick tap - existing mouse handlers will process it automatically
                // Touch events are converted to mouse events by the system

                // Extra behavior for TV/touch: if drawing a polyline, double-tap finishes the line
                if (lineToggleButton.isSelected() &&
                    currentLineType == LineType.POLYLINE &&
                    constructingPolylinePoints != null &&
                    !constructingPolylinePoints.isEmpty()) {

                    long now = System.currentTimeMillis();
                    // If second tap within 400ms, finalize the polyline
                    if (now - lastPolylineTapTime <= 400L) {
                        finalizePolyline();
                        throttledRepaint();
                        lastPolylineTapTime = 0L;
                    } else {
                        lastPolylineTapTime = now;
                    }
                }
            }
        }
        
        // Reset edit mode flag after a short delay to allow edit mode to complete
        if (editModeActivated) {
            javax.swing.Timer resetTimer = new javax.swing.Timer(100, ev -> {
                editModeActivated = false;
            });
            resetTimer.setRepeats(false);
            resetTimer.start();
        }
        
        // Reset touch points
        if (secondTouchPoint != null) {
            secondTouchPoint = null;
            isPinching = false;
        } else if (firstTouchPoint != null) {
            firstTouchPoint = null;
            touchStartPoint = null;
        }
    }
    
    /**
     * Handle long press (context menu) - 500ms
     */
    private void handleLongPress(Point point) {
        if (!touchEnabled || editModeActivated) return;
        
        // Show context menu at long press location
        int hitIndex = hitTestMarker(point);
        if (hitIndex != -1) {
            selectMarker(hitIndex);
            showMarkerContextMenu(point.x, point.y);
        } else {
            int textHitIndex = hitTestText(point);
            if (textHitIndex != -1) {
                selectText(textHitIndex);
                showTextContextMenu(point.x, point.y);
            }
        }
    }
    
    /**
     * Activate edit mode after 1 second hold on touch screen
     */
    private void activateEditMode(Point point) {
        if (!touchEnabled) return;
        
        editModeActivated = true;
        
        // Cancel context menu timer since we're entering edit mode
        if (longPressTimer != null) {
            longPressTimer.stop();
        }
        
        // Check what was touched and enter appropriate edit mode
        int markerIndex = hitTestMarker(point);
        if (markerIndex != -1) {
            // Enter marker edit mode
            selectMarker(markerIndex);
            startEditPositionMode();
            pageLabel.setText("Edit mode: Drag marker to new position, then tap to confirm");
            return;
        }
        
        int lineIndex = hitTestLine(point);
        if (lineIndex != -1) {
            // Enter line edit mode
            selectLine(lineIndex);
            pageLabel.setText("Edit mode: Line selected - use context menu for options");
            return;
        }
        
        int textIndex = hitTestText(point);
        if (textIndex != -1) {
            // Enter text edit mode
            selectText(textIndex);
            pageLabel.setText("Edit mode: Text selected - use context menu for options");
            return;
        }
        
        // If nothing specific was touched, show general edit options
        pageLabel.setText("Edit mode activated - tap on annotation to edit");
    }
    
    private void setupHoverTimer() {
        hoverTimer = new javax.swing.Timer(500, e -> {
            if (hoveredMarkerIndex != -1 || hoveredLineIndex != -1 || hoveredTextIndex != -1) {
                showHoverTooltip();
            }
        });
        hoverTimer.setRepeats(false);
        
        // Create tooltip
        hoverTooltip = new JPopupMenu();
    }
    
    private void setupPerformanceOptimizations() {
        // Setup repaint throttling timer
        repaintTimer = new javax.swing.Timer(REPAINT_DELAY, e -> {
            if (isRendering) {
                isRendering = false;
                imageCanvas.repaint();
            }
        });
        repaintTimer.setRepeats(false);
        
        // Setup mouse move debouncing timer
        mouseMoveTimer = new javax.swing.Timer(MOUSE_MOVE_DELAY, e -> {
            // Process mouse move events here if needed
        });
        mouseMoveTimer.setRepeats(false);
    }
    
    private void throttledRepaint() {
        if (!isRendering) {
            isRendering = true;
            repaintTimer.restart();
        }
    }
    
    private void clearImageCache() {
        cachedPageImage = null;
        cachedScale = -1;
        cachedPage = -1;
    }
    
    private void showHoverTooltip() {
        String content = "";
        String title = "";
        
        if (hoveredMarkerIndex != -1) {
            List<Marker> markers = pageIndexToMarkers.get(currentPage);
            if (markers != null && hoveredMarkerIndex < markers.size()) {
                Marker marker = markers.get(hoveredMarkerIndex);
                title = "Marker";
                content = marker.text != null && !marker.text.isEmpty() ? marker.text : "No text";
            }
        } else if (hoveredLineIndex != -1) {
            List<Line> lines = pageIndexToLines.get(currentPage);
            if (lines != null && hoveredLineIndex < lines.size()) {
                Line line = lines.get(hoveredLineIndex);
                title = "Line";
                content = line.text != null && !line.text.isEmpty() ? line.text : "No text";
            }
        } else if (hoveredTextIndex != -1) {
            List<TextAnnotation> texts = pageIndexToTexts.get(currentPage);
            if (texts != null && hoveredTextIndex < texts.size()) {
                TextAnnotation text = texts.get(hoveredTextIndex);
                title = "Text Annotation";
                content = text.text != null && !text.text.isEmpty() ? text.text : "No text";
            }
        }
        
        if (!content.isEmpty()) {
            hoverTooltip.removeAll();
            StringBuilder html = new StringBuilder("<html><b>")
                .append(title)
                .append(":</b><br>")
                .append(content.replace("\n", "<br>"));

            // Append creator info if available
            String createdBy = null;
            if (hoveredMarkerIndex != -1) {
                java.util.List<Marker> markers = pageIndexToMarkers.get(currentPage);
                if (markers != null && hoveredMarkerIndex < markers.size()) {
                    createdBy = markers.get(hoveredMarkerIndex).createdBy;
                }
            } else if (hoveredLineIndex != -1) {
                java.util.List<Line> lines = pageIndexToLines.get(currentPage);
                if (lines != null && hoveredLineIndex < lines.size()) {
                    createdBy = lines.get(hoveredLineIndex).createdBy;
                }
            } else if (hoveredTextIndex != -1) {
                java.util.List<TextAnnotation> texts = pageIndexToTexts.get(currentPage);
                if (texts != null && hoveredTextIndex < texts.size()) {
                    createdBy = texts.get(hoveredTextIndex).createdBy;
                }
            }

            if (createdBy != null && !createdBy.isEmpty()) {
                html.append("<br><i>By: ").append(createdBy).append("</i>");
            }

            html.append("<br><i>(Right-click for options)</i></html>");

            JLabel label = new JLabel(html.toString());
            label.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            hoverTooltip.add(label);
            
            // Add mouse listener to show context menu on right-click
            hoverTooltip.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (e.getButton() == java.awt.event.MouseEvent.BUTTON3) {
                        hideHoverTooltip();
                        if (hoveredLineIndex != -1) {
                            selectLine(hoveredLineIndex);
                            showLineContextMenu(e.getX(), e.getY());
                        } else if (hoveredMarkerIndex != -1) {
                            // Marker context menu is already handled elsewhere
                        } else if (hoveredTextIndex != -1) {
                            // Text context menu is already handled elsewhere
                        }
                    }
                }
            });
            
            if (hoverPosition != null) {
                hoverTooltip.show(imageCanvas, hoverPosition.x + 10, hoverPosition.y - 30);
            }
        }
    }
    
    private void hideHoverTooltip() {
        hoverTooltip.hide();
        hoveredMarkerIndex = -1;
        hoveredLineIndex = -1;
        hoveredTextIndex = -1;
    }
    
    private void checkHoverOverAnnotations(Point mousePoint) {
        // Reset hover states
        int oldMarkerHover = hoveredMarkerIndex;
        int oldLineHover = hoveredLineIndex;
        int oldTextHover = hoveredTextIndex;
        
        hoveredMarkerIndex = -1;
        hoveredLineIndex = -1;
        hoveredTextIndex = -1;
        
        // Check markers first (they're on top)
        int markerHit = hitTestMarker(mousePoint);
        if (markerHit != -1) {
            hoveredMarkerIndex = markerHit;
        } else {
            // Check text annotations
            int textHit = hitTestText(mousePoint);
            if (textHit != -1) {
                hoveredTextIndex = textHit;
            } else {
                // Check lines (they're at the bottom)
                int lineHit = hitTestLine(mousePoint);
                if (lineHit != -1) {
                    hoveredLineIndex = lineHit;
                }
            }
        }
        
        // If hover state changed, restart timer
        if (oldMarkerHover != hoveredMarkerIndex || oldLineHover != hoveredLineIndex || oldTextHover != hoveredTextIndex) {
            hoverTimer.stop();
            hideHoverTooltip();
            
            if (hoveredMarkerIndex != -1 || hoveredLineIndex != -1 || hoveredTextIndex != -1) {
                hoverTimer.start();
            }
        }
        
        // Update cursor based on hover
        if (hoveredMarkerIndex != -1 || hoveredLineIndex != -1 || hoveredTextIndex != -1) {
            imageCanvas.setCursor(new Cursor(Cursor.HAND_CURSOR));
        } else {
            // Reset cursor based on current mode
            if (markerToggleButton.isSelected() || lineToggleButton.isSelected() || textToggleButton.isSelected()) {
                imageCanvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
            } else {
                imageCanvas.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
        }
    }
    
    private int hitTestLine(Point canvasPoint) {
        List<Line> lines = pageIndexToLines.get(currentPage);
        if (lines == null || lines.isEmpty()) return -1;
        
        for (int i = lines.size() - 1; i >= 0; i--) { // topmost first
            Line line = lines.get(i);
            
            if (line.type == LineType.CIRCLE) {
                // Test circle hit
                int centerX = Math.round(line.start.x * scale) + imageOffset.x;
                int centerY = Math.round(line.start.y * scale) + imageOffset.y;
                int radius = Math.round(line.radius * scale);
                
                int dx = canvasPoint.x - centerX;
                int dy = canvasPoint.y - centerY;
                int distance = (int) Math.sqrt(dx * dx + dy * dy);
                
                // Check if point is within circle boundary (with some tolerance)
                if (Math.abs(distance - radius) <= Math.max(5, Math.round(5 * scale))) {
                    return i;
                }
            } else {
                // Test line hit (for straight lines and polylines)
                int x1 = Math.round(line.start.x * scale) + imageOffset.x;
                int y1 = Math.round(line.start.y * scale) + imageOffset.y;
                int x2 = Math.round(line.end.x * scale) + imageOffset.x;
                int y2 = Math.round(line.end.y * scale) + imageOffset.y;
                
                // Calculate distance from point to line
                int tolerance = Math.max(5, Math.round(5 * scale));
                if (isPointNearLine(canvasPoint.x, canvasPoint.y, x1, y1, x2, y2, tolerance)) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    private boolean isPointNearLine(int px, int py, int x1, int y1, int x2, int y2, int tolerance) {
        // Calculate distance from point to line segment
        int A = px - x1;
        int B = py - y1;
        int C = x2 - x1;
        int D = y2 - y1;
        
        int dot = A * C + B * D;
        int lenSq = C * C + D * D;
        
        if (lenSq == 0) {
            // Line is actually a point
            return Math.sqrt(A * A + B * B) <= tolerance;
        }
        
        double param = (double) dot / lenSq;
        
        int xx, yy;
        if (param < 0) {
            xx = x1;
            yy = y1;
        } else if (param > 1) {
            xx = x2;
            yy = y2;
        } else {
            xx = x1 + (int) (param * C);
            yy = y1 + (int) (param * D);
        }
        
        int dx = px - xx;
        int dy = py - yy;
        return Math.sqrt(dx * dx + dy * dy) <= tolerance;
    }
    
    
    public void loadPdf(File pdfFile) {
        // Handle null (clear viewer)
        if (pdfFile == null) {
            if (this.currentPdfFile != null) {
                PdfRenderer.closeDocument(this.currentPdfFile);
            }
            this.currentPdfFile = null;
            this.currentPage = 1;
            this.totalPages = 1;
            this.currentPageImage = null;
            clearImageCache();
            updateImageDisplay();
            updatePageLabel();
            updateButtonStates();
            pageLabel.setText("No PDF loaded");
            return;
        }
        
        // Close previous PDF document to free memory
        if (this.currentPdfFile != null && !this.currentPdfFile.equals(pdfFile)) {
            PdfRenderer.closeDocument(this.currentPdfFile);
        }
        
        // Clear cache when loading new PDF
        clearImageCache();
        
        this.currentPdfFile = pdfFile;
        this.currentPage = 1;
        this.imageOffset = new Point(0, 0); // Reset pan offset
        this.pageIndexToMarkers.clear();
        this.pageIndexToLines.clear();
        this.pageIndexToTexts.clear();
        this.constructingPolylinePoints = null;
        this.selectedMarkerIndex = -1;
        this.selectedTextIndex = -1;
        this.isDraggingMarker = false;
        
        // Show loading indicator
        pageLabel.setText("Loading PDF...");
        
        // Load PDF in background thread to avoid blocking UI
        new Thread(() -> {
            try {
                // Get page count (this will cache the document)
                this.totalPages = PdfRenderer.getPageCount(pdfFile);
                if (this.totalPages < 1) {
                    throw new IOException("PDF has no pages");
                }
                
                // Load first page - will auto-fit to fill screen when done
                SwingUtilities.invokeLater(() -> {
                    loadCurrentPage(true);  // fitToScreen = true for initial load
                    updateButtonStates();
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    showError("Error loading PDF: " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void loadCurrentPage() {
        loadCurrentPage(false);
    }
    
    private void loadCurrentPage(boolean fitToScreenWhenDone) {
        if (currentPdfFile == null) return;
        
        // Check if we can use cached image
        if (cachedPageImage != null && cachedPage == currentPage && Math.abs(cachedScale - scale) < 0.001f) {
            currentPageImage = cachedPageImage;
            updateImageDisplay();
            updatePageLabel();
            if (fitToScreenWhenDone) {
                scheduleFitToScreen();
            }
            return;
        }
        
        // Show loading indicator
        pageLabel.setText("Rendering page " + currentPage + "...");
        
        // Capture snapshot so zoom/close during render doesn't pass null or wrong page
        final File fileToRender = currentPdfFile;
        final int pageToRender = currentPage;
        final float scaleToRender = scale;
        if (fileToRender == null || !fileToRender.exists()) {
            pageLabel.setText("Page " + currentPage + " of " + totalPages);
            return;
        }
        
        // Render in background thread to avoid blocking UI
        new Thread(() -> {
            try {
                BufferedImage renderedImage = PdfRenderer.renderPage(fileToRender, pageToRender, scaleToRender);
                
                // Update UI on EDT only if this render is still current (no new zoom/page change)
                SwingUtilities.invokeLater(() -> {
                    if (currentPdfFile != fileToRender || currentPage != pageToRender || scale != scaleToRender) {
                        return;
                    }
                    currentPageImage = renderedImage;
                    
                    // Cache the rendered image
                    cachedPageImage = currentPageImage;
                    cachedPage = currentPage;
                    cachedScale = scale;
                    // Apply any pan offset requested during zoom so markers and PDF stay in sync
                    if (pendingImageOffsetAfterLoad != null) {
                        imageOffset.x = pendingImageOffsetAfterLoad.x;
                        imageOffset.y = pendingImageOffsetAfterLoad.y;
                        pendingImageOffsetAfterLoad = null;
                    }
                    
                    updateImageDisplay();
                    updatePageLabel();
                    
                    // Fit to fill screen when PDF first loads
                    if (fitToScreenWhenDone) {
                        scheduleFitToScreen();
                    }
                });
            } catch (IOException e) {
                String errMsg = e.getMessage();
                if (errMsg == null) errMsg = "Unknown error";
                final String msg = errMsg;
                SwingUtilities.invokeLater(() -> {
                    showError("Error rendering page: " + msg);
                });
            }
        }).start();
    }
    
    /** Schedule fit-to-screen after layout has settled */
    private void scheduleFitToScreen() {
        Timer fitTimer = new Timer(150, e -> {
            fitToFullVisibility();
        });
        fitTimer.setRepeats(false);
        fitTimer.start();
    }
    
    private void updateImageDisplay() {
        // On laptop (no TV mode), keep pan offset at zero so PDF and markers always move together via scrollbars only
        if (!TvMode.ENABLED) {
            imageOffset.x = 0;
            imageOffset.y = 0;
        }
        if (currentPageImage != null) {
            // Update canvas preferred size to the scaled image size (panning handled via offset)
            imageCanvas.setPreferredSize(new Dimension(
                currentPageImage.getWidth(),
                currentPageImage.getHeight()
            ));
            imageCanvas.revalidate();
            imageCanvas.repaint();
        } else {
            imageCanvas.setPreferredSize(new Dimension(800, 600));
            imageCanvas.revalidate();
            imageCanvas.repaint();
        }
    }
    
    private void updatePageLabel() {
        pageLabel.setText("Page " + currentPage + " of " + totalPages);
    }
    
    private void updateButtonStates() {
        prevPageButton.setEnabled(currentPage > 1);
        nextPageButton.setEnabled(currentPage < totalPages);
    }
    
    private void previousPage() {
        if (currentPage > 1) {
            currentPage--;
            imageOffset = new Point(0, 0); // Reset pan offset
            loadCurrentPage();
            updateButtonStates();
        }
    }
    
    private void nextPage() {
        if (currentPage < totalPages) {
            currentPage++;
            imageOffset = new Point(0, 0); // Reset pan offset
            loadCurrentPage();
            updateButtonStates();
        }
    }
    
    public void zoomIn() {
        zoomAtCanvasPoint(true, lastMousePosition != null ? lastMousePosition : new Point(imageCanvas.getWidth()/2, imageCanvas.getHeight()/2));
    }
    
    public void zoomOut() {
        zoomAtCanvasPoint(false, lastMousePosition != null ? lastMousePosition : new Point(imageCanvas.getWidth()/2, imageCanvas.getHeight()/2));
    }

    private void zoomAtCanvasPoint(boolean zoomIn, Point canvasPoint) {
        if (currentPdfFile == null || currentPageImage == null) return;
        if (canvasPoint == null) canvasPoint = new Point(imageCanvas.getWidth()/2, imageCanvas.getHeight()/2);

        // Convert canvas point to image coordinates before zoom
        float preImageX = (canvasPoint.x - imageOffset.x) / scale;
        float preImageY = (canvasPoint.y - imageOffset.y) / scale;

        // Update scale
        float delta = Math.max(0.1f, scale * 0.1f);
        float newScale = zoomIn ? Math.min(5.0f, scale + delta) : Math.max(0.1f, scale - delta);
        if (newScale == scale) return;
        scale = newScale;

        // On TV, compute pan offset to apply when the new image is ready (keeps point under cursor)
        if (TvMode.ENABLED) {
            int newCanvasX = Math.round(preImageX * scale) + imageOffset.x;
            int newCanvasY = Math.round(preImageY * scale) + imageOffset.y;
            pendingImageOffsetAfterLoad = new Point(
                imageOffset.x + (canvasPoint.x - newCanvasX),
                imageOffset.y + (canvasPoint.y - newCanvasY)
            );
        } else {
            pendingImageOffsetAfterLoad = null;
        }

        // Re-render at new scale; display is updated only when new image is ready so markers never drift
        loadCurrentPage();
    }
    
    public void fitToWidth() {
        // Calculate appropriate scale to fit width
        if (currentPdfFile != null && currentPageImage != null) {
            Container parent = getParent();
            if (parent != null) {
                int availableWidth = Math.max(100, parent.getWidth() - 50); // Account for scroll bars
                // Derive original width from current image and scale without extra render
                float originalWidth = currentPageImage.getWidth() / Math.max(0.001f, scale);
                scale = Math.max(0.1f, Math.min(5.0f, (float) availableWidth / Math.max(1f, originalWidth)));
                loadCurrentPage();
            }
        }
    }
    
    public void fitToFullVisibility() {
        // Calculate scale to fill the available screen/viewport
        if (currentPdfFile != null && currentPageImage != null) {
            Dimension availableSize = getAvailableSize();
            
            // Prefer viewport size; fallback to screen size for maximized window
            if (availableSize.width <= 0 || availableSize.height <= 0) {
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                availableSize = new Dimension(
                    Math.max(400, screenSize.width - 120),
                    Math.max(300, screenSize.height - 180)
                );
            }
            
            int availableWidth = Math.max(100, availableSize.width);
            int availableHeight = Math.max(100, availableSize.height);
            
            float originalWidth = currentPageImage.getWidth() / Math.max(0.001f, scale);
            float originalHeight = currentPageImage.getHeight() / Math.max(0.001f, scale);
            float scaleX = (float) availableWidth / Math.max(1f, originalWidth);
            float scaleY = (float) availableHeight / Math.max(1f, originalHeight);
            // Use min to fit entirely, or max to fill (user said "fill" - use max to fill screen)
            scale = Math.max(0.1f, Math.min(5.0f, Math.max(scaleX, scaleY)));
            imageOffset = new Point(0, 0);
            loadCurrentPage();
        }
    }
    
    private Dimension getAvailableSize() {
        // Try to get the available size from multiple sources for better full screen support
        Container parent = getParent();
        if (parent != null) {
            // First try to get the scroll pane viewport size
            JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, imageCanvas);
            if (scrollPane != null) {
                Dimension viewportSize = scrollPane.getViewport().getSize();
                if (viewportSize.width > 0 && viewportSize.height > 0) {
                    // Account for borders and padding
                    return new Dimension(viewportSize.width - 20, viewportSize.height - 20);
                }
            }
            
            // Fallback to parent component size
            Dimension parentSize = parent.getSize();
            if (parentSize.width > 0 && parentSize.height > 0) {
                // Account for borders, padding, and control panels
                return new Dimension(parentSize.width - 40, parentSize.height - 100);
            }
            
            // Try to get the preferred size if current size is not available
            Dimension preferredSize = parent.getPreferredSize();
            if (preferredSize.width > 0 && preferredSize.height > 0) {
                return new Dimension(preferredSize.width - 40, preferredSize.height - 100);
            }
        }
        
        // Final fallback to component size
        Dimension componentSize = getSize();
        if (componentSize.width > 0 && componentSize.height > 0) {
            return new Dimension(componentSize.width - 20, componentSize.height - 20);
        }
        
        // Default fallback
        return new Dimension(800, 600);
    }
    
    private void toggleEditMode() {
        if (editModeActive) {
            // Exit edit mode - hide edit buttons
            editModeActive = false;
            currentEditorUsername = null;
            markerToggleButton.setSelected(false);
            lineToggleButton.setSelected(false);
            textToggleButton.setSelected(false);
            stopEditPositionMode();
            constructingPolylinePoints = null;
            setEditButtonsVisible(false);
            editModeButton.setText("Edit");
            imageCanvas.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            imageCanvas.repaint();
            return;
        }
        
        // Prompt for credentials to enter edit mode
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        JLabel userLabel = new JLabel("Username:");
        JLabel passLabel = new JLabel("Password:");
        JTextField userField = new JTextField(20);
        JPasswordField passField = new JPasswordField(20);
        if (TvMode.ENABLED) {
            userLabel.setFont(TvMode.getLabelFont());
            passLabel.setFont(TvMode.getLabelFont());
            userField.setFont(TvMode.getInputFont());
            passField.setFont(TvMode.getInputFont());
        }
        panel.add(userLabel);
        panel.add(userField);
        panel.add(passLabel);
        panel.add(passField);
        
        int r = JOptionPane.showConfirmDialog(this, panel, "Edit Mode - Enter Credentials", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;

        String usernameInput = userField.getText().trim();
        String passwordInput = new String(passField.getPassword());

        boolean canEdit = false;
        UserService.User user = userService.verify(usernameInput, passwordInput);
        canEdit = user != null && userService.canEdit(user);

        if (!canEdit) {
            JOptionPane.showMessageDialog(this, "Invalid credentials or you do not have permission to make changes.", "Access Denied", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        editModeActive = true;
        currentEditorUsername = (user != null ? user.username : usernameInput);
        setEditButtonsVisible(true);
        editModeButton.setText("Exit Edit");
        pageLabel.setText("Edit mode: " + currentEditorUsername);
    }
    
    private void setEditButtonsVisible(boolean visible) {
        markerToggleButton.setVisible(visible);
        lineToggleButton.setVisible(visible);
        textToggleButton.setVisible(visible);
        doneMovingButton.setVisible(visible && isEditingMarkerPosition);
    }
    
    private void showError(String message) {
        // Render error as a dialog; keep canvas clean
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
    
    public void clear() {
        currentPdfFile = null;
        currentPage = 1;
        totalPages = 1;
        currentPageImage = null;
        pageIndexToMarkers.clear();
        pageIndexToLines.clear();
        pageIndexToTexts.clear();
        constructingPolylinePoints = null;
        selectedMarkerIndex = -1;
        selectedTextIndex = -1;
        isDraggingMarker = false;
        imageCanvas.repaint();
        updateButtonStates();
        updatePageLabel();
    }
    
    public boolean hasAnyChanges() {
        return !pageIndexToMarkers.isEmpty() || 
               !pageIndexToLines.isEmpty() || 
               !pageIndexToTexts.isEmpty();
    }
    
    // Additional methods for external control
    public void fitToPage() {
        // Reset to 100% scale
        scale = 1.0f;
        if (currentPdfFile != null) {
            loadCurrentPage();
        }
    }
    
    public void rotateClockwise() {
        // For now, just reset to fit to width
        // In a full implementation, this would rotate the image
        fitToWidth();
    }

    private void addMarkerFromClick(Point clickPointOnCanvas) {
        if (currentPageImage == null) return;

        Point2D.Float pdfPoint = toPdfPoint(clickPointOnCanvas);
        if (pdfPoint == null) return;

        // Show color selection dialog first
        Color selectedColor = showColorSelectionDialog();
        if (selectedColor == null) return; // user cancelled color selection

        String label = JOptionPane.showInputDialog(this, "Enter marker note:", "Add Marker", JOptionPane.PLAIN_MESSAGE);
        if (label == null) label = "";

        List<Marker> list = pageIndexToMarkers.computeIfAbsent(currentPage, k -> new ArrayList<>());
        Marker newMarker = new Marker(pdfPoint, label, selectedColor, currentEditorUsername);
        list.add(newMarker);
        selectedMarkerIndex = list.size() - 1;
        imageCanvas.repaint();
        notifyChangesMade();

        // One-marker-at-a-time: turn off marker mode after inserting
        markerToggleButton.setSelected(false);
        imageCanvas.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        updateButtonStates();

        // Changes will be saved automatically when the application closes or file is switched
    }
    
    private Color showColorSelectionDialog() {
        JDialog colorDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Select Marker Color", true);
        colorDialog.setLayout(new BorderLayout());
        colorDialog.setSize(300, 200);
        colorDialog.setLocationRelativeTo(this);
        
        JPanel colorPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        colorPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        Color[] colors = {
            new Color(255, 0, 0, 220),     // Bright Red
            new Color(0, 255, 0, 220),     // Bright Green
            new Color(255, 20, 147, 220),  // Deep Pink/Magenta
            new Color(255, 140, 0, 220)    // Dark Orange
        };
        
        String[] colorNames = {"Red", "Green", "Pink", "Orange"};
        final Color[] selectedColor = {null};
        
        for (int i = 0; i < colors.length; i++) {
            JButton colorButton = new JButton(colorNames[i]);
            colorButton.setBackground(colors[i]);
            colorButton.setForeground(Color.BLACK);
            colorButton.setFont(TvMode.getButtonFont());
            colorButton.setPreferredSize(new Dimension(TvMode.BUTTON_MIN_WIDTH, TvMode.BUTTON_HEIGHT));
            
            final int index = i;
            colorButton.addActionListener(e -> {
                selectedColor[0] = colors[index];
                colorDialog.dispose();
            });
            
            colorPanel.add(colorButton);
        }
        
        JButton cancelButton = new JButton("Cancel");
        TvMode.applyButtonStyle(cancelButton);
        cancelButton.addActionListener(e -> {
            selectedColor[0] = null;
            colorDialog.dispose();
        });
        
        colorDialog.add(colorPanel, BorderLayout.CENTER);
        colorDialog.add(cancelButton, BorderLayout.SOUTH);
        
        colorDialog.setVisible(true);
        return selectedColor[0];
    }
    
    
    private void addTextFromClick(Point clickPointOnCanvas) {
        if (currentPageImage == null) return;

        Point2D.Float pdfPoint = toPdfPoint(clickPointOnCanvas);
        if (pdfPoint == null) return;

        String text = JOptionPane.showInputDialog(this, "Enter text annotation:", "Add Text", JOptionPane.PLAIN_MESSAGE);
        if (text == null || text.trim().isEmpty()) return; // user cancelled or empty text

        List<TextAnnotation> list = pageIndexToTexts.computeIfAbsent(currentPage, k -> new ArrayList<>());
        list.add(new TextAnnotation(pdfPoint, text.trim(), currentEditorUsername));
        selectedTextIndex = list.size() - 1;
        imageCanvas.repaint();
        notifyChangesMade();
        
        // Terminate text creation mode after adding the text
        textToggleButton.setSelected(false);
        
        // Changes will be saved automatically when the application closes or file is switched
    }

    private void selectMarker(int index) {
        selectedMarkerIndex = index;
        imageCanvas.repaint();
    }
    
    private void selectText(int index) {
        selectedTextIndex = index;
        imageCanvas.repaint();
    }

    private void deleteSelectedMarker() {
        List<Marker> markers = pageIndexToMarkers.get(currentPage);
        if (markers == null || selectedMarkerIndex < 0 || selectedMarkerIndex >= markers.size()) return;
        markers.remove(selectedMarkerIndex);
        selectedMarkerIndex = -1;
        imageCanvas.repaint();
        notifyChangesMade();
    }

    private void editSelectedMarkerText() {
        List<Marker> markers = pageIndexToMarkers.get(currentPage);
        if (markers == null || selectedMarkerIndex < 0 || selectedMarkerIndex >= markers.size()) return;
        Marker m = markers.get(selectedMarkerIndex);
        String newText = JOptionPane.showInputDialog(this, "Edit marker note:", m.text);
        if (newText != null) {
            m.text = newText;
            imageCanvas.repaint();
        }
    }
    
    private void changeMarkerColor(Color newColor) {
        List<Marker> markers = pageIndexToMarkers.get(currentPage);
        if (markers == null || selectedMarkerIndex < 0 || selectedMarkerIndex >= markers.size()) return;
        Marker m = markers.get(selectedMarkerIndex);
        m.color = newColor;
        imageCanvas.repaint();
    }
    
    private void deleteSelectedText() {
        List<TextAnnotation> texts = pageIndexToTexts.get(currentPage);
        if (texts == null || selectedTextIndex < 0 || selectedTextIndex >= texts.size()) return;
        texts.remove(selectedTextIndex);
        selectedTextIndex = -1;
        imageCanvas.repaint();
        notifyChangesMade();
    }
    
    private void editSelectedText() {
        List<TextAnnotation> texts = pageIndexToTexts.get(currentPage);
        if (texts == null || selectedTextIndex < 0 || selectedTextIndex >= texts.size()) return;
        TextAnnotation text = texts.get(selectedTextIndex);
        String newText = JOptionPane.showInputDialog(this, "Edit text annotation:", text.text);
        if (newText != null && !newText.trim().isEmpty()) {
            text.text = newText.trim();
            imageCanvas.repaint();
        }
    }

    private void addLine(Point2D.Float start, Point2D.Float end) {
        if (start == null || end == null) return;
        List<Line> list = pageIndexToLines.computeIfAbsent(currentPage, k -> new ArrayList<>());
        Line line = new Line(start, end);
        line.createdBy = currentEditorUsername;
        list.add(line);
    }

    private void finalizePolyline() {
        if (constructingPolylinePoints == null || constructingPolylinePoints.size() < 2) {
            constructingPolylinePoints = null;
            return;
        }
        
        // Ask for details once, apply to all segments
        String details = JOptionPane.showInputDialog(this, "Enter line details:", "Line Details", JOptionPane.PLAIN_MESSAGE);
        String trimmed = details == null ? null : details.trim();

        List<Line> list = pageIndexToLines.computeIfAbsent(currentPage, k -> new ArrayList<>());
        for (int i = 1; i < constructingPolylinePoints.size(); i++) {
            Line seg = new Line(constructingPolylinePoints.get(i - 1), constructingPolylinePoints.get(i));
            if (trimmed != null && !trimmed.isEmpty()) seg.text = trimmed;
            seg.createdBy = currentEditorUsername;
            list.add(seg);
        }
        constructingPolylinePoints = null;
        notifyChangesMade();
        
        // Terminate line creation mode after adding the polyline
        lineToggleButton.setSelected(false);
    }

    private void showMarkerContextMenu(int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        if (isEditingMarkerPosition) {
            JMenuItem done = new JMenuItem("Done Moving");
            done.addActionListener(ev -> stopEditPositionMode());
            menu.add(done);
        } else {
            JMenuItem editPos = new JMenuItem("Edit Position");
            JMenuItem editText = new JMenuItem("Edit Text");
            
            // Color selection submenu
            JMenu colorMenu = new JMenu("Change Color");
            JMenuItem redColor = new JMenuItem("Red");
            JMenuItem greenColor = new JMenuItem("Green");
            JMenuItem pinkColor = new JMenuItem("Pink");
            JMenuItem orangeColor = new JMenuItem("Orange");
            
            redColor.addActionListener(ev -> changeMarkerColor(new Color(255, 0, 0, 220)));
            greenColor.addActionListener(ev -> changeMarkerColor(new Color(0, 255, 0, 220)));
            pinkColor.addActionListener(ev -> changeMarkerColor(new Color(255, 20, 147, 220)));
            orangeColor.addActionListener(ev -> changeMarkerColor(new Color(255, 140, 0, 220)));
            
            colorMenu.add(redColor);
            colorMenu.add(greenColor);
            colorMenu.add(pinkColor);
            colorMenu.add(orangeColor);
            
            JMenuItem del = new JMenuItem("Delete Marker");
            editPos.addActionListener(ev -> { startEditPositionMode(); });
            editText.addActionListener(ev -> editSelectedMarkerText());
            del.addActionListener(ev -> deleteSelectedMarker());
            menu.add(editPos);
            menu.add(editText);
            menu.add(colorMenu);
            menu.add(del);
        }
        menu.show(imageCanvas, x, y);
    }
    
    private void showLineContextMenu(int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem editText = new JMenuItem("Edit Text");
        JMenuItem del = new JMenuItem("Delete Line");
        
        editText.addActionListener(ev -> editSelectedLineText());
        del.addActionListener(ev -> deleteSelectedLine());
        
        menu.add(editText);
        menu.add(del);
        menu.show(imageCanvas, x, y);
    }
    
    private void selectLine(int index) {
        selectedLineIndex = index;
        imageCanvas.requestFocusInWindow(); // Ensure canvas has focus for keyboard events
        imageCanvas.repaint();
        System.out.println("Line " + index + " selected. Press Delete key to delete.");
    }
    
    private void editSelectedLineText() {
        List<Line> lines = pageIndexToLines.get(currentPage);
        if (lines == null || selectedLineIndex < 0 || selectedLineIndex >= lines.size()) return;
        
        Line line = lines.get(selectedLineIndex);
        String newText = JOptionPane.showInputDialog(this, "Enter line text:", line.text != null ? line.text : "");
        if (newText != null) {
            line.text = newText.trim();
            imageCanvas.repaint();
            notifyChangesMade();
        }
    }
    
    private void deleteSelectedLine() {
        List<Line> lines = pageIndexToLines.get(currentPage);
        if (lines == null || selectedLineIndex < 0 || selectedLineIndex >= lines.size()) {
            System.out.println("Cannot delete line: lines=" + lines + ", selectedIndex=" + selectedLineIndex);
            return;
        }
        
        System.out.println("Deleting line " + selectedLineIndex);
        lines.remove(selectedLineIndex);
        selectedLineIndex = -1;
        imageCanvas.repaint();
        notifyChangesMade();
        System.out.println("Line deleted successfully");
    }
    
    private void showTextContextMenu(int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem editText = new JMenuItem("Edit Text");
        JMenuItem del = new JMenuItem("Delete Text");
        editText.addActionListener(ev -> editSelectedText());
        del.addActionListener(ev -> deleteSelectedText());
        menu.add(editText);
        menu.add(del);
        menu.show(imageCanvas, x, y);
    }
    
    private void showLineTypeMenu(int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem polyline = new JMenuItem("Polyline (Multi-point)");
        JMenuItem straight = new JMenuItem("Straight Line");
        JMenuItem circle = new JMenuItem("Circle");
        
        polyline.addActionListener(ev -> {
            currentLineType = LineType.POLYLINE;
            lineToggleButton.setText("Add Line (Polyline)");
        });
        straight.addActionListener(ev -> {
            currentLineType = LineType.STRAIGHT_LINE;
            lineToggleButton.setText("Add Line (Straight)");
        });
        circle.addActionListener(ev -> {
            currentLineType = LineType.CIRCLE;
            lineToggleButton.setText("Add Line (Circle)");
        });
        
        menu.add(polyline);
        menu.add(straight);
        menu.add(circle);
        menu.show(lineToggleButton, x, y);
    }

    private int hitTestMarker(Point canvasPoint) {
        List<Marker> markers = pageIndexToMarkers.get(currentPage);
        if (markers == null || markers.isEmpty()) return -1;
        int drawOx = TvMode.ENABLED ? imageOffset.x : 0;
        int drawOy = TvMode.ENABLED ? imageOffset.y : 0;
        final int hitRadiusPx = Math.max(MARKER_RADIUS_PX + 4, 12); // hit area for constant-size markers
        for (int i = markers.size() - 1; i >= 0; i--) { // topmost first
            Marker m = markers.get(i);
            int cx = Math.round(m.position.x * scale) + drawOx;
            int cy = Math.round(m.position.y * scale) + drawOy;
            int r = hitRadiusPx + ((isEditingMarkerPosition && i == selectedMarkerIndex) ? 6 : 0);
            int dx = canvasPoint.x - cx;
            int dy = canvasPoint.y - cy;
            if ((dx * dx + dy * dy) <= r * r) return i;
        }
        return -1;
    }
    
    private int hitTestText(Point canvasPoint) {
        List<TextAnnotation> texts = pageIndexToTexts.get(currentPage);
        if (texts == null || texts.isEmpty()) return -1;
        
        Font baseFont = imageCanvas.getFont();
        Font textFont = baseFont.deriveFont(Math.max(10f, 12f * Math.min(1.0f, scale)));
        FontMetrics fm = imageCanvas.getFontMetrics(textFont);
        
        for (int i = texts.size() - 1; i >= 0; i--) { // topmost first
            TextAnnotation text = texts.get(i);
            int drawX = Math.round(text.position.x * scale) + imageOffset.x;
            int drawY = Math.round(text.position.y * scale) + imageOffset.y;
            
            int textWidth = fm.stringWidth(text.text);
            int textHeight = fm.getHeight();
            int padding = Math.max(4, Math.round(4 * scale));
            
            int left = drawX - padding;
            int top = drawY - textHeight + fm.getAscent() - padding;
            int right = left + textWidth + 2 * padding;
            int bottom = top + textHeight + 2 * padding;
            
            if (canvasPoint.x >= left && canvasPoint.x <= right && 
                canvasPoint.y >= top && canvasPoint.y <= bottom) {
                return i;
            }
        }
        return -1;
    }

    private Point2D.Float toPdfPoint(Point canvasPoint) {
        if (currentPageImage == null) return null;
        float imageX = (canvasPoint.x - imageOffset.x) / scale;
        float imageY = (canvasPoint.y - imageOffset.y) / scale;
        if (imageX < 0 || imageY < 0 || imageX >= (float) currentPageImage.getWidth() / scale || imageY >= (float) currentPageImage.getHeight() / scale) {
            return null;
        }
        return new Point2D.Float(imageX, imageY);
    }

    // Canvas component that draws the current page and overlay markers
    private class ImageCanvas extends JComponent {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                // Background text when no PDF
                if (currentPageImage == null) {
                    g2.setColor(Color.DARK_GRAY);
                    String msg = "No PDF loaded";
                    FontMetrics fm = g2.getFontMetrics();
                    int x = (getWidth() - fm.stringWidth(msg)) / 2;
                    int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString(msg, Math.max(10, x), Math.max(20, y));
                    return;
                }

                // On laptop use (0,0) so PDF and markers always move together; on TV use pan offset
                int drawOx = TvMode.ENABLED ? imageOffset.x : 0;
                int drawOy = TvMode.ENABLED ? imageOffset.y : 0;

                // Draw page image with pan offset
                g2.drawImage(currentPageImage, drawOx, drawOy, null);

                // Draw markers for current page, scaled with zoom (colored points)
                List<Marker> markers = pageIndexToMarkers.get(currentPage);
                if (markers != null && !markers.isEmpty()) {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    for (int i = 0; i < markers.size(); i++) {
                        Marker m = markers.get(i);
                        int drawX = Math.round(m.position.x * scale) + drawOx;
                        int drawY = Math.round(m.position.y * scale) + drawOy;

                        // Constant on-screen radius so all markers look the same size at any zoom
                        int r = MARKER_RADIUS_PX;
                        int d = r * 2;

                        // Intensify marker color slightly for better visibility,
                        // especially for reds and greens.
                        Color baseColor = (m.color != null) ? m.color : Color.RED;
                        Color drawColor;
                        if (Color.RED.equals(baseColor)) {
                            drawColor = new Color(220, 0, 0);      // brighter red
                        } else if (Color.GREEN.equals(baseColor)) {
                            drawColor = new Color(0, 200, 0);      // brighter green
                        } else {
                            drawColor = baseColor;
                        }

                        // Draw colored circle as the marker point
                        g2.setColor(drawColor);
                        g2.fillOval(drawX - r, drawY - r, d, d);

                        // Border / highlight based on state (stroke also scales mildly with zoom)
                        if (i == selectedMarkerIndex) {
                            g2.setColor(Color.YELLOW);
                        } else if (i == hoveredMarkerIndex) {
                            g2.setColor(Color.CYAN); // Highlight on hover
                        } else {
                            g2.setColor(Color.WHITE);
                        }
                        g2.setStroke(new BasicStroke(Math.max(1.2f, scale)));
                        g2.drawOval(drawX - r, drawY - r, d, d);

                        // Text label bubble, like a placemark label
                        if (m.text != null && !m.text.isEmpty()) {
                            String text = m.text;
                            if (m.createdBy != null && !m.createdBy.isEmpty()) {
                                text = text + "  (" + m.createdBy + ")";
                            }
                            Font base = g2.getFont();
                            g2.setFont(base.deriveFont(Math.max(10f, 12f * Math.min(1.0f, scale))));
                            FontMetrics fm = g2.getFontMetrics();
                            int pad = 6;
                            int tw = fm.stringWidth(text);
                            int th = fm.getHeight();

                            // Place label slightly above and to the right of the pin
                            int bx = drawX + r + 10;
                            int by = drawY - th / 2;

                            Shape bubble = new Rectangle2D.Float(
                                bx - pad,
                                by - pad + fm.getAscent() - th,
                                tw + pad * 2,
                                th + pad
                            );
                            g2.setColor(new Color(0, 0, 0, 180));
                            g2.fill(bubble);
                            g2.setColor(Color.WHITE);
                            g2.drawString(text, bx, by);
                        }
                    }
                }

                // Draw persistent lines (colored red)
                List<Line> lines = pageIndexToLines.get(currentPage);
                if (lines != null && !lines.isEmpty()) {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    for (int i = 0; i < lines.size(); i++) {
                        Line ln = lines.get(i);
                        
                        // Set color and stroke based on selection and hover state
                        if (i == selectedLineIndex) {
                            g2.setColor(Color.BLUE); // Selected line in blue
                            g2.setStroke(new BasicStroke(Math.max(4f, scale * 2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        } else if (i == hoveredLineIndex) {
                            g2.setColor(Color.CYAN); // Highlight on hover
                            g2.setStroke(new BasicStroke(Math.max(3f, scale * 1.5f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        } else {
                            g2.setColor(Color.RED);
                            g2.setStroke(new BasicStroke(Math.max(2f, scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        }
                        switch (ln.type) {
                            case POLYLINE:
                            case STRAIGHT_LINE:
                                int x1 = Math.round(ln.start.x * scale) + drawOx;
                                int y1 = Math.round(ln.start.y * scale) + drawOy;
                                int x2 = Math.round(ln.end.x * scale) + drawOx;
                                int y2 = Math.round(ln.end.y * scale) + drawOy;
                                g2.drawLine(x1, y1, x2, y2);
                                break;
                            case CIRCLE:
                                int centerX = Math.round(ln.start.x * scale) + drawOx;
                                int centerY = Math.round(ln.start.y * scale) + drawOy;
                                int radius = Math.round(ln.radius * scale);
                                g2.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
                                break;
                        }
                        // draw text near midpoint if present
                        if (ln.text != null && !ln.text.isEmpty()) {
                            String text = ln.text;
                            if (ln.createdBy != null && !ln.createdBy.isEmpty()) {
                                text = text + "  (" + ln.createdBy + ")";
                            }
                            Font base = g2.getFont();
                            g2.setFont(base.deriveFont(Math.max(10f, 12f * Math.min(1.0f, scale))));
                            FontMetrics fm = g2.getFontMetrics();
                            int pad = 6;
                            int midX, midY;
                            if (ln.type == LineType.CIRCLE) {
                                midX = Math.round(ln.start.x * scale) + drawOx;
                                midY = Math.round(ln.start.y * scale) + drawOy;
                            } else {
                                midX = (Math.round(ln.start.x * scale) + Math.round(ln.end.x * scale)) / 2 + drawOx;
                                midY = (Math.round(ln.start.y * scale) + Math.round(ln.end.y * scale)) / 2 + drawOy;
                            }
                            int tw = fm.stringWidth(text);
                            int th = fm.getHeight();
                            Shape bubble = new Rectangle2D.Float(midX - tw/2 - pad, midY - th/2 - pad + fm.getAscent() - th, tw + pad * 2, th + pad);
                            g2.setColor(new Color(0, 0, 0, 140));
                            g2.fill(bubble);
                            g2.setColor(Color.WHITE);
                            g2.drawString(text, midX - tw/2, midY);
                            g2.setColor(Color.RED);
                            g2.setStroke(new BasicStroke(Math.max(2f, scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        }
                    }
                }

                // Draw preview polyline during creation (red dashed)
                if (lineToggleButton.isSelected() && constructingPolylinePoints != null && !constructingPolylinePoints.isEmpty()) {
                    g2.setColor(new Color(255, 0, 0, 170));
                    float dash = Math.max(4f, scale * 2f);
                    g2.setStroke(new BasicStroke(Math.max(1.5f, scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{dash, dash}, 0));
                    // draw segments between added points
                    for (int i = 1; i < constructingPolylinePoints.size(); i++) {
                        Point2D.Float a = constructingPolylinePoints.get(i - 1);
                        Point2D.Float b = constructingPolylinePoints.get(i);
                        int x1 = Math.round(a.x * scale) + drawOx;
                        int y1 = Math.round(a.y * scale) + drawOy;
                        int x2 = Math.round(b.x * scale) + drawOx;
                        int y2 = Math.round(b.y * scale) + drawOy;
                        g2.drawLine(x1, y1, x2, y2);
                    }
                    // trailing segment to last known mouse position
                    if (lastMousePosition != null) {
                        Point2D.Float last = constructingPolylinePoints.get(constructingPolylinePoints.size() - 1);
                        int x1 = Math.round(last.x * scale) + drawOx;
                        int y1 = Math.round(last.y * scale) + drawOy;
                        g2.drawLine(x1, y1, lastMousePosition.x, lastMousePosition.y);
                    }
                }
                
                // Draw preview for straight line
                if (lineToggleButton.isSelected() && currentLineType == LineType.STRAIGHT_LINE && straightLineStart != null && lastMousePosition != null) {
                    g2.setColor(new Color(255, 0, 0, 170));
                    g2.setStroke(new BasicStroke(Math.max(1.5f, scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int x1 = Math.round(straightLineStart.x * scale) + drawOx;
                    int y1 = Math.round(straightLineStart.y * scale) + drawOy;
                    g2.drawLine(x1, y1, lastMousePosition.x, lastMousePosition.y);
                }
                
                // Draw preview for circle
                if (lineToggleButton.isSelected() && currentLineType == LineType.CIRCLE && circleCenter != null && lastMousePosition != null) {
                    g2.setColor(new Color(255, 0, 0, 170));
                    g2.setStroke(new BasicStroke(Math.max(1.5f, scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int centerX = Math.round(circleCenter.x * scale) + drawOx;
                    int centerY = Math.round(circleCenter.y * scale) + drawOy;
                    float radius = (float) Math.sqrt(
                        Math.pow(lastMousePosition.x - centerX, 2) + 
                        Math.pow(lastMousePosition.y - centerY, 2)
                    );
                    g2.drawOval(centerX - (int)radius, centerY - (int)radius, (int)radius * 2, (int)radius * 2);
                }
                
                // Draw text annotations for current page
                List<TextAnnotation> texts = pageIndexToTexts.get(currentPage);
                if (texts != null && !texts.isEmpty()) {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Font baseFont = g2.getFont();
                    Font textFont = baseFont.deriveFont(Math.max(10f, 12f * Math.min(1.0f, scale)));
                    g2.setFont(textFont);
                    FontMetrics fm = g2.getFontMetrics();
                    
                    for (int i = 0; i < texts.size(); i++) {
                        TextAnnotation text = texts.get(i);
                        int drawX = Math.round(text.position.x * scale) + drawOx;
                        int drawY = Math.round(text.position.y * scale) + drawOy;
                        
                        // Draw text background
                        String displayText = text.text;
                        if (text.createdBy != null && !text.createdBy.isEmpty()) {
                            displayText = displayText + "  (" + text.createdBy + ")";
                        }
                        int textWidth = fm.stringWidth(displayText);
                        int textHeight = fm.getHeight();
                        int padding = Math.max(4, Math.round(4 * scale));
                        
                        g2.setColor(text.backgroundColor);
                        g2.fillRoundRect(drawX - padding, drawY - textHeight + fm.getAscent() - padding, 
                                        textWidth + 2 * padding, textHeight + 2 * padding, 
                                        Math.max(4, Math.round(4 * scale)), Math.max(4, Math.round(4 * scale)));
                        
                        // Draw text border if selected or hovered
                        if (i == selectedTextIndex) {
                            g2.setColor(Color.BLUE);
                            g2.setStroke(new BasicStroke(Math.max(2f, scale)));
                            g2.drawRoundRect(drawX - padding, drawY - textHeight + fm.getAscent() - padding, 
                                           textWidth + 2 * padding, textHeight + 2 * padding, 
                                           Math.max(4, Math.round(4 * scale)), Math.max(4, Math.round(4 * scale)));
                        } else if (i == hoveredTextIndex) {
                            g2.setColor(Color.CYAN); // Highlight on hover
                            g2.setStroke(new BasicStroke(Math.max(2f, scale)));
                            g2.drawRoundRect(drawX - padding, drawY - textHeight + fm.getAscent() - padding, 
                                           textWidth + 2 * padding, textHeight + 2 * padding, 
                                           Math.max(4, Math.round(4 * scale)), Math.max(4, Math.round(4 * scale)));
                        }
                        
                        // Draw text
                        g2.setColor(Color.BLACK);
                        g2.drawString(displayText, drawX, drawY);
                    }
                }
            } finally {
                g2.dispose();
            }
        }
    }

    private void startEditPositionMode() {
        isEditingMarkerPosition = true;
        if (doneMovingButton != null) {
            doneMovingButton.setVisible(editModeActive);
        }
        imageCanvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        if (wobbleTimer == null) {
            wobbleTimer = new javax.swing.Timer(120, e -> imageCanvas.repaint());
        }
        if (!wobbleTimer.isRunning()) wobbleTimer.start();
    }

    private void stopEditPositionMode() {
        isEditingMarkerPosition = false;
        if (doneMovingButton != null) {
            doneMovingButton.setVisible(false);
        }
        imageCanvas.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        if (wobbleTimer != null && wobbleTimer.isRunning()) wobbleTimer.stop();
        imageCanvas.repaint();
    }

    // ===== Persistence API =====
    public static class MarkerDTO {
        public float x;
        public float y;
        public String text;
        public int colorRgb; // store color as RGB integer
        public String createdBy;
        
        public MarkerDTO(float x, float y, String text) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.colorRgb = new Color(255, 0, 0, 220).getRGB(); // default bright red
            this.createdBy = null;
        }
        
        public MarkerDTO(float x, float y, String text, Color color) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.colorRgb = color.getRGB();
            this.createdBy = null;
        }

        public MarkerDTO(float x, float y, String text, Color color, String createdBy) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.colorRgb = color.getRGB();
            this.createdBy = createdBy;
        }
    }
    
    public static class TextDTO {
        public float x;
        public float y;
        public String text;
        public int backgroundColorRgb; // store background color as RGB integer
        public String createdBy;
        
        public TextDTO(float x, float y, String text) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.backgroundColorRgb = new Color(255, 255, 0, 180).getRGB(); // default yellow
            this.createdBy = null;
        }
        
        public TextDTO(float x, float y, String text, Color backgroundColor) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.backgroundColorRgb = backgroundColor.getRGB();
            this.createdBy = null;
        }

        public TextDTO(float x, float y, String text, Color backgroundColor, String createdBy) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.backgroundColorRgb = backgroundColor.getRGB();
            this.createdBy = createdBy;
        }
    }
    
    public static class LineDTO {
        public float startX, startY, endX, endY;
        public String text;
        public int lineType; // 0=polyline, 1=straight, 2=circle
        public float radius; // for circles
        public String createdBy;
        
        public LineDTO(float startX, float startY, float endX, float endY) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.text = "";
            this.lineType = 0; // polyline
            this.radius = 0;
            this.createdBy = null;
        }
        
        public LineDTO(float startX, float startY, float endX, float endY, String text) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.text = text;
            this.lineType = 0; // polyline
            this.radius = 0;
            this.createdBy = null;
        }

        public LineDTO(float startX, float startY, float endX, float endY, String text, String createdBy) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.text = text;
            this.lineType = 0;
            this.radius = 0;
            this.createdBy = createdBy;
        }
    }

    public java.util.Map<Integer, java.util.List<MarkerDTO>> exportMarkers() {
        java.util.Map<Integer, java.util.List<MarkerDTO>> snapshot = new java.util.HashMap<>();
        for (java.util.Map.Entry<Integer, java.util.List<Marker>> e : pageIndexToMarkers.entrySet()) {
            java.util.List<MarkerDTO> list = new java.util.ArrayList<>();
            for (Marker m : e.getValue()) {
                list.add(new MarkerDTO(m.position.x, m.position.y, m.text == null ? "" : m.text, m.color, m.createdBy));
            }
            snapshot.put(e.getKey(), list);
        }
        return snapshot;
    }

    public void importMarkers(java.util.Map<Integer, java.util.List<MarkerDTO>> snapshot) {
        pageIndexToMarkers.clear();
        if (snapshot == null) return;
        for (java.util.Map.Entry<Integer, java.util.List<MarkerDTO>> e : snapshot.entrySet()) {
            java.util.List<Marker> list = new java.util.ArrayList<>();
            for (MarkerDTO dto : e.getValue()) {
                Color markerColor = new Color(dto.colorRgb, true); // preserve alpha
                list.add(new Marker(new Point2D.Float(dto.x, dto.y), dto.text, markerColor, dto.createdBy));
            }
            pageIndexToMarkers.put(e.getKey(), list);
        }
        selectedMarkerIndex = -1;
        isDraggingMarker = false;
        stopEditPositionMode();
        if (imageCanvas != null) imageCanvas.repaint();
    }
    
    public java.util.Map<Integer, java.util.List<LineDTO>> exportLines() {
        java.util.Map<Integer, java.util.List<LineDTO>> snapshot = new java.util.HashMap<>();
        for (java.util.Map.Entry<Integer, java.util.List<Line>> e : pageIndexToLines.entrySet()) {
            java.util.List<LineDTO> list = new java.util.ArrayList<>();
            for (Line line : e.getValue()) {
                LineDTO dto = new LineDTO(line.start.x, line.start.y, line.end.x, line.end.y, line.text, line.createdBy);
                dto.lineType = line.type.ordinal(); // 0=polyline, 1=straight, 2=circle
                dto.radius = line.radius;
                list.add(dto);
            }
            snapshot.put(e.getKey(), list);
        }
        return snapshot;
    }
    
    public java.util.Map<Integer, java.util.List<TextDTO>> exportTexts() {
        java.util.Map<Integer, java.util.List<TextDTO>> snapshot = new java.util.HashMap<>();
        for (java.util.Map.Entry<Integer, java.util.List<TextAnnotation>> e : pageIndexToTexts.entrySet()) {
            java.util.List<TextDTO> list = new java.util.ArrayList<>();
            for (TextAnnotation text : e.getValue()) {
                list.add(new TextDTO(text.position.x, text.position.y, text.text, text.backgroundColor, text.createdBy));
            }
            snapshot.put(e.getKey(), list);
        }
        return snapshot;
    }
    
    public void importLines(java.util.Map<Integer, java.util.List<LineDTO>> snapshot) {
        pageIndexToLines.clear();
        if (snapshot == null) return;
        for (java.util.Map.Entry<Integer, java.util.List<LineDTO>> e : snapshot.entrySet()) {
            java.util.List<Line> list = new java.util.ArrayList<>();
            for (LineDTO dto : e.getValue()) {
                LineType type = LineType.values()[dto.lineType];
                Line line;
                if (type == LineType.CIRCLE) {
                    line = new Line(new Point2D.Float(dto.startX, dto.startY), dto.radius);
                } else {
                    line = new Line(new Point2D.Float(dto.startX, dto.startY), new Point2D.Float(dto.endX, dto.endY), type);
                }
                line.text = dto.text;
                line.createdBy = dto.createdBy;
                list.add(line);
            }
            pageIndexToLines.put(e.getKey(), list);
        }
        selectedLineIndex = -1;
        if (imageCanvas != null) imageCanvas.repaint();
    }
    
    public void importTexts(java.util.Map<Integer, java.util.List<TextDTO>> snapshot) {
        pageIndexToTexts.clear();
        if (snapshot == null) return;
        for (java.util.Map.Entry<Integer, java.util.List<TextDTO>> e : snapshot.entrySet()) {
            java.util.List<TextAnnotation> list = new java.util.ArrayList<>();
            for (TextDTO dto : e.getValue()) {
                Color backgroundColor = new Color(dto.backgroundColorRgb, true); // preserve alpha
                TextAnnotation text = new TextAnnotation(new Point2D.Float(dto.x, dto.y), dto.text, dto.createdBy);
                text.backgroundColor = backgroundColor;
                list.add(text);
            }
            pageIndexToTexts.put(e.getKey(), list);
        }
        selectedTextIndex = -1;
        if (imageCanvas != null) imageCanvas.repaint();
    }
}

