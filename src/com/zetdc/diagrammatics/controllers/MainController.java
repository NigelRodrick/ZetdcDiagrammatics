package com.zetdc.diagrammatics.controllers;

import com.zetdc.diagrammatics.utils.PdfReader;
import com.zetdc.diagrammatics.components.PdfViewerComponent;
import com.zetdc.diagrammatics.utils.FileManager;
import com.zetdc.diagrammatics.utils.PdfRenderer;
import com.zetdc.diagrammatics.utils.TvMode;
import com.zetdc.diagrammatics.utils.TouchSupport;
import com.zetdc.diagrammatics.utils.UserService;
import com.zetdc.diagrammatics.components.AdminDialog;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

/**
 * Main controller for the diagramming application
 * Handles UI interactions and coordinates between different components
 * 
 * Developer: Nigel Onai Rodrick Sibanda
 * Organization: ZETDC Southern Region
 */
public class MainController {
    
    private JFrame mainFrame;
    private JPanel leftPanel;
    private JPanel toolPanel;
    private JPanel pdfViewerPanel;
    
    // File info and PDF viewer
    private JLabel fileNameLabel;
    private JList<String> fileList;
    private JScrollPane fileScrollPane;
    private JTextArea pdfContentArea;
    private JButton viewFullContentButton;
    private File currentPdfFile;
    private PdfViewerComponent pdfViewerComponent;
    
    // File operations
    private File currentFile = null;
    private java.util.Map<String, File> fileMap = new java.util.HashMap<>();
    
    // Persistence state: per-file markers, lines, and texts (page -> items)
    private final Map<String, Map<Integer, List<PdfViewerComponent.MarkerDTO>>> filePathToMarkers = new HashMap<>();
    private final Map<String, Map<Integer, List<PdfViewerComponent.LineDTO>>> filePathToLines = new HashMap<>();
    private final Map<String, Map<Integer, List<PdfViewerComponent.TextDTO>>> filePathToTexts = new HashMap<>();
    private final Path stateFilePath = Paths.get(System.getProperty("user.home"), ".zetdcdiagrammatics-state.json");
    
    // Track unsaved changes
    private boolean hasUnsavedChanges = false;
    
    // User management
    private final UserService userService = new UserService();
    
    // Method to mark changes as unsaved (called by PdfViewerComponent)
    public void markAsUnsaved() {
        hasUnsavedChanges = true;
        
        // Save immediately to project folder to persist deletions
        if (currentPdfFile != null && pdfViewerComponent != null) {
            try {
                // Update in-memory state
                filePathToMarkers.put(currentPdfFile.getAbsolutePath(), pdfViewerComponent.exportMarkers());
                filePathToLines.put(currentPdfFile.getAbsolutePath(), pdfViewerComponent.exportLines());
                filePathToTexts.put(currentPdfFile.getAbsolutePath(), pdfViewerComponent.exportTexts());
                
                // Save immediately to project folder
                FileManager.saveAnnotations(
                    currentPdfFile,
                    filePathToMarkers.get(currentPdfFile.getAbsolutePath()),
                    filePathToLines.get(currentPdfFile.getAbsolutePath()),
                    filePathToTexts.get(currentPdfFile.getAbsolutePath())
                );
                
                System.out.println("Changes saved permanently to: " + FileManager.getProjectFolderForPdf(currentPdfFile));
            } catch (Exception e) {
                System.err.println("Failed to save changes immediately: " + e.getMessage());
            }
        }
    }
    
    public void createAndShowGUI() {
        // Create main frame
        mainFrame = new JFrame("ZetdcDiagrammatics");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setLayout(new BorderLayout());
        
        // Set application icon
        setApplicationIcon();
        
        // Create menu bar
        createMenuBar();
        
        // Create tool panel
        createToolPanel();
        
        // Create left panel with file list
        createLeftPanel();
        
        // Create PDF viewer panel
        createPdfViewerPanel();
        
        // Create status bar
        createStatusBar();
        
        // Set up layout
        mainFrame.add(toolPanel, BorderLayout.NORTH);
        mainFrame.add(leftPanel, BorderLayout.WEST);
        mainFrame.add(pdfViewerPanel, BorderLayout.CENTER);
        
        // Configure frame (TV mode: larger minimum for 10-foot UI)
        mainFrame.setMinimumSize(new Dimension(TvMode.MIN_WINDOW_WIDTH, TvMode.MIN_WINDOW_HEIGHT));
        mainFrame.setLocationRelativeTo(null);
        
        // Start in maximized state immediately
        mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        mainFrame.setVisible(true);

        // Load persisted state after UI is ready (defer to make startup faster)
        SwingUtilities.invokeLater(() -> {
            // Small delay to let UI render first
            Timer loadTimer = new Timer(100, e -> {
                loadAppState();
            });
            loadTimer.setRepeats(false);
            loadTimer.start();
        });

        // Handle window closing with save confirmation
        mainFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                try {
                    // Always save all edits before closing, regardless of confirmation
                    saveAllEdits();
                    
                    if (confirmSaveChanges("Exit Application")) {
                        // Close all PDF documents and clear cache before exiting
                        try {
                            PdfRenderer.clearCache();
                            System.out.println("All PDF documents closed and cache cleared");
                        } catch (Exception ex) {
                            System.err.println("Error closing PDF documents: " + ex.getMessage());
                        }
                        
                        // Also persist lightweight app state
                        saveAppState();
                        System.exit(0);
                    }
                } catch (Exception ex) {
                    // If there's any error, try to cleanup anyway
                    try {
                        PdfRenderer.clearCache();
                    } catch (Exception cleanupEx) {
                        // Ignore cleanup errors
                    }
                    System.exit(0);
                }
            }
        });
    }
    
    private boolean confirmSaveChanges(String action) {
        if (!hasUnsavedChanges) {
            return true; // No unsaved changes, proceed
        }
        
        int result = JOptionPane.showConfirmDialog(
            mainFrame,
            "You have unsaved changes. Do you want to save them permanently before " + action.toLowerCase() + "?",
            "Save Changes Permanently",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        if (result == JOptionPane.YES_OPTION) {
            // User wants to save - try to save permanently
            try {
                saveAppStatePermanently();
                hasUnsavedChanges = false;
                JOptionPane.showMessageDialog(mainFrame, 
                    "All changes have been saved permanently!", 
                    "Save Successful", 
                    JOptionPane.INFORMATION_MESSAGE);
                return true; // Proceed with the action (like closing)
            } catch (Exception e) {
                // Save failed - ask if user wants to discard all changes
                int discardResult = JOptionPane.showConfirmDialog(
                    mainFrame,
                    "Failed to save changes: " + e.getMessage() + "\n\nDo you want to discard all changes and continue?",
                    "Save Failed - Discard Changes?",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE
                );
                
                if (discardResult == JOptionPane.YES_OPTION) {
                    // User chose to discard all changes
                    discardAllChanges();
                    hasUnsavedChanges = false;
                    return true; // Proceed with the action
                } else {
                    // User cancelled - don't proceed with the action
                    return false;
                }
            }
        } else {
            // User chose not to save - ask for confirmation to discard all changes
                int confirmResult = JOptionPane.showConfirmDialog(
                    mainFrame,
                "Are you sure you want to discard ALL unsaved changes permanently?",
                "Confirm Discard All Changes",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                );
                
                if (confirmResult == JOptionPane.YES_OPTION) {
                // User confirmed - discard all changes and proceed
                discardAllChanges();
                    hasUnsavedChanges = false;
                return true; // Proceed with the action
                } else {
                    // User cancelled - don't proceed with the action
                    return false;
                }
        }
    }
    
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        if (TvMode.ENABLED) {
            menuBar.setFont(TvMode.getMenuFont());
        }
        
        // File menu
        JMenu fileMenu = new JMenu("File");
        if (TvMode.ENABLED) fileMenu.setFont(TvMode.getMenuFont());
        JMenuItem newItem = new JMenuItem("New");
        JMenuItem openItem = new JMenuItem("Open...");
        JMenuItem saveChangesItem = new JMenuItem("Save Changes Permanently");
        JMenuItem exportPdfItem = new JMenuItem("Export PDF with Changes");
        JMenuItem shareItem = new JMenuItem("Open Folder for Sharing");
        JMenuItem discardChangesItem = new JMenuItem("Discard All Changes");
        JMenuItem exitItem = new JMenuItem("Exit");
        
        newItem.addActionListener(e -> newFile());
        openItem.addActionListener(e -> openFile());
        saveChangesItem.addActionListener(e -> {
            try {
                saveAppStatePermanently();
                hasUnsavedChanges = false;
                String lastEditor = (pdfViewerComponent != null && pdfViewerComponent.getCurrentEditorUsername() != null
                    && !pdfViewerComponent.getCurrentEditorUsername().trim().isEmpty())
                    ? pdfViewerComponent.getCurrentEditorUsername().trim()
                    : "Unknown";
                JOptionPane.showMessageDialog(mainFrame, 
                    "All changes have been saved permanently.\nLast editor: " + lastEditor, 
                    "Save Successful", 
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(mainFrame, 
                    "Failed to save changes: " + ex.getMessage(), 
                    "Save Failed", 
                    JOptionPane.ERROR_MESSAGE);
            }
        });
        exportPdfItem.addActionListener(e -> exportPdfWithChanges());
        shareItem.addActionListener(e -> openFolderForSharing());
        discardChangesItem.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(
                mainFrame,
                "Are you sure you want to discard ALL changes permanently?",
                "Confirm Discard All Changes",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (result == JOptionPane.YES_OPTION) {
                discardAllChanges();
                hasUnsavedChanges = false;
            }
        });
        exitItem.addActionListener(e -> System.exit(0));
        
        fileMenu.add(newItem);
        fileMenu.add(openItem);
        fileMenu.addSeparator();
        fileMenu.add(saveChangesItem);
        fileMenu.add(exportPdfItem);
        fileMenu.add(shareItem);
        fileMenu.add(discardChangesItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        
        // Edit menu
        JMenu editMenu = new JMenu("Edit");
        if (TvMode.ENABLED) editMenu.setFont(TvMode.getMenuFont());
        JMenuItem clearItem = new JMenuItem("Clear PDF");
        
        clearItem.addActionListener(e -> {
            if (pdfViewerComponent != null) {
                pdfViewerComponent.clear();
            }
        });
        
        editMenu.add(clearItem);
        
        // View menu
        JMenu viewMenu = new JMenu("View");
        if (TvMode.ENABLED) viewMenu.setFont(TvMode.getMenuFont());
        JMenuItem fullScreenItem = new JMenuItem("Toggle Full Screen (F11)");
        fullScreenItem.addActionListener(e -> toggleFullScreen());
        viewMenu.add(fullScreenItem);

        // Edits menu – entry point for editing
        JMenu editsMenu = new JMenu("Edits");
        if (TvMode.ENABLED) editsMenu.setFont(TvMode.getMenuFont());
        JMenuItem startEditingItem = new JMenuItem("Start Editing (Login)");
        JMenuItem addMarkerItem = new JMenuItem("Add Marker");
        JMenuItem addLineItem = new JMenuItem("Add Line");
        JMenuItem addTextItem = new JMenuItem("Add Text");
        JMenuItem deleteSelectedItem = new JMenuItem("Delete Selected (Marker/Line/Text)");

        startEditingItem.addActionListener(e -> {
            if (pdfViewerComponent != null) {
                // Reuse the same behavior as the Edit button in the viewer (prompts for login)
                try {
                    java.lang.reflect.Method m = pdfViewerComponent.getClass().getDeclaredMethod("toggleEditMode");
                    m.setAccessible(true);
                    m.invoke(pdfViewerComponent);
                } catch (Exception ignore) {
                    // Ignore if reflection fails
                }

                // After toggling, update the menu text based on edit mode state
                if (pdfViewerComponent.isEditModeActive()) {
                    String user = pdfViewerComponent.getCurrentEditorUsername();
                    if (user != null && !user.trim().isEmpty()) {
                        startEditingItem.setText("Log out (" + user.trim() + ")");
                    } else {
                        startEditingItem.setText("Log out");
                    }
                } else {
                    startEditingItem.setText("Start Editing (Login)");
                }
            }
        });

        addMarkerItem.addActionListener(e -> {
            if (pdfViewerComponent != null) pdfViewerComponent.startAddMarkerMode();
        });
        addLineItem.addActionListener(e -> {
            if (pdfViewerComponent != null) pdfViewerComponent.startAddLineMode();
        });
        addTextItem.addActionListener(e -> {
            if (pdfViewerComponent != null) pdfViewerComponent.startAddTextMode();
        });
        deleteSelectedItem.addActionListener(e -> {
            if (pdfViewerComponent != null) {
                // Use existing Delete key behavior via a helper: prioritize line, then marker, then text
                pdfViewerComponent.deleteCurrentSelection();
            }
        });

        editsMenu.add(startEditingItem);
        editsMenu.addSeparator();
        editsMenu.add(addMarkerItem);
        editsMenu.add(addLineItem);
        editsMenu.add(addTextItem);
        editsMenu.addSeparator();
        editsMenu.add(deleteSelectedItem);

        // Admin menu - add users who can make changes
        JMenu adminMenu = new JMenu("Admin");
        if (TvMode.ENABLED) adminMenu.setFont(TvMode.getMenuFont());
        JMenuItem manageUsersItem = new JMenuItem("Manage Users");
        manageUsersItem.addActionListener(e -> showAdminDialog());
        adminMenu.add(manageUsersItem);
        
        // Help menu
        JMenu helpMenu = new JMenu("Help");
        if (TvMode.ENABLED) helpMenu.setFont(TvMode.getMenuFont());
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> showAboutDialog());
        helpMenu.add(aboutItem);
        
        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(viewMenu);
        menuBar.add(editsMenu);
        menuBar.add(adminMenu);
        menuBar.add(helpMenu);
        
        mainFrame.setJMenuBar(menuBar);
    }
    
    private void createToolPanel() {
        toolPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        // Panel no longer shows separate application tools; all edit tools are under the Edits menu.
    }
    
    
    private void createLeftPanel() {
        leftPanel = new JPanel();
        leftPanel.setLayout(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(TvMode.LEFT_PANEL_WIDTH, 0));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Files"));
        if (TvMode.ENABLED) {
            leftPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                "Files",
                0, 0,
                TvMode.getTitleFont()
            ));
        }
        
        // Top panel for file name label
        JPanel topPanel = new JPanel(new BorderLayout());
        fileNameLabel = new JLabel("No file selected");
        fileNameLabel.setBorder(BorderFactory.createEmptyBorder(TvMode.PADDING, TvMode.PADDING, TvMode.PADDING, TvMode.PADDING));
        if (TvMode.ENABLED) fileNameLabel.setFont(TvMode.getListFont());
        topPanel.add(fileNameLabel, BorderLayout.CENTER);
        
        // File list
        fileList = new JList<>();
        if (TvMode.ENABLED) {
            fileList.setFont(TvMode.getListFont());
            fileList.setFixedCellHeight(TvMode.LIST_ROW_HEIGHT);
        }
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedFile = fileList.getSelectedValue();
                if (selectedFile != null) {
                    // Try to find the actual file and load it
                    File actualFile = findFileByName(selectedFile);
                    if (actualFile != null && actualFile.exists()) {
                        loadPdfFromFile(actualFile);
                    } else {
                        loadPdfFile(selectedFile); // Fallback to simulated content
                    }
                }
            }
        });
        
        // Start with empty file list
        fileList.setListData(new String[0]);
        
        // Add context menu to file list (touch: larger font for menu items)
        JPopupMenu fileListContextMenu = new JPopupMenu();
        JMenuItem saveFileItem = new JMenuItem("Save Changes");
        JMenuItem exportPdfItem = new JMenuItem("Export PDF with Changes");
        JMenuItem removeFileItem = new JMenuItem("Remove from List");
        if (TvMode.TOUCH_ENABLED) {
            saveFileItem.setFont(TvMode.getMenuFont());
            exportPdfItem.setFont(TvMode.getMenuFont());
            removeFileItem.setFont(TvMode.getMenuFont());
        }
        
        saveFileItem.addActionListener(e -> saveCurrentFile());
        exportPdfItem.addActionListener(e -> exportPdfWithChanges());
        removeFileItem.addActionListener(e -> removeSelectedFile());
        
        fileListContextMenu.add(saveFileItem);
        fileListContextMenu.add(exportPdfItem);
        fileListContextMenu.addSeparator();
        fileListContextMenu.add(removeFileItem);
        
        // Mouse + touch: right-click or long-press (500ms) for context menu
        javax.swing.Timer[] fileListLongPressTimer = { null };
        java.awt.Point[] fileListPressPoint = { null };
        fileList.addMouseListener(new java.awt.event.MouseAdapter() {
            void showContextMenuAt(int x, int y) {
                int index = fileList.locationToIndex(new java.awt.Point(x, y));
                if (index >= 0) {
                    fileList.setSelectedIndex(index);
                    fileListContextMenu.show(fileList, x, y);
                }
            }
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenuAt(e.getX(), e.getY());
                } else if (TvMode.TOUCH_ENABLED && SwingUtilities.isLeftMouseButton(e)) {
                    int index = fileList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        fileListPressPoint[0] = e.getPoint();
                        if (fileListLongPressTimer[0] != null) fileListLongPressTimer[0].stop();
                        fileListLongPressTimer[0] = new javax.swing.Timer(500, ev -> {
                            if (fileListPressPoint[0] != null) {
                                showContextMenuAt(fileListPressPoint[0].x, fileListPressPoint[0].y);
                                fileListPressPoint[0] = null;
                            }
                        });
                        fileListLongPressTimer[0].setRepeats(false);
                        fileListLongPressTimer[0].start();
                    }
                }
            }
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (fileListLongPressTimer[0] != null) {
                    fileListLongPressTimer[0].stop();
                    fileListLongPressTimer[0] = null;
                }
                fileListPressPoint[0] = null;
            }
        });
        
        fileScrollPane = new JScrollPane(fileList);
        fileScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        fileScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        TouchSupport.configureTouchScrollPane(fileScrollPane);
        
        // Bottom panel for browse button
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton browseButton = new JButton("Browse PDF Files");
        TvMode.applyButtonStyle(browseButton);
        browseButton.addActionListener(e -> openFile());
        bottomPanel.add(browseButton);
        
        // Add components to left panel
        leftPanel.add(topPanel, BorderLayout.NORTH);
        leftPanel.add(fileScrollPane, BorderLayout.CENTER);
        leftPanel.add(bottomPanel, BorderLayout.SOUTH);
    }
    
    private void createPdfViewerPanel() {
        pdfViewerPanel = new JPanel(new BorderLayout());
        pdfViewerPanel.setBorder(BorderFactory.createTitledBorder("PDF Viewer"));
        if (TvMode.ENABLED) {
            pdfViewerPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                "PDF Viewer",
                0, 0,
                TvMode.getTitleFont()
            ));
        }
        
        // Create tabbed pane for different viewing modes
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // Visual PDF viewer (new)
        pdfViewerComponent = new PdfViewerComponent(userService);
        pdfViewerComponent.setMainController(this);
        tabbedPane.addTab("Visual View", pdfViewerComponent);
        
        // Text content viewer (existing)
        pdfContentArea = new JTextArea();
        pdfContentArea.setEditable(false);
        pdfContentArea.setFont(new Font("Monospaced", Font.PLAIN, TvMode.FONT_INPUT));
        pdfContentArea.setBackground(Color.WHITE);
        pdfContentArea.setText("Select a PDF file from the left panel to view its content here.\n\n" +
                              "This PDF viewer displays actual text content from PDF files.\n" +
                              "Use the 'View Full Content' button to see the complete document.");
        
        JScrollPane pdfScrollPane = new JScrollPane(pdfContentArea);
        pdfScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        pdfScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        TouchSupport.configureTouchScrollPane(pdfScrollPane);
        
        // Control panel for text view
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        viewFullContentButton = new JButton("View Full Content");
        TvMode.applyButtonStyle(viewFullContentButton);
        viewFullContentButton.setEnabled(false);
        viewFullContentButton.addActionListener(e -> viewFullPdfContent());
        
        controlPanel.add(viewFullContentButton);
        
        JPanel textViewPanel = new JPanel(new BorderLayout());
        textViewPanel.add(controlPanel, BorderLayout.NORTH);
        textViewPanel.add(pdfScrollPane, BorderLayout.CENTER);
        
        tabbedPane.addTab("Text View", textViewPanel);
        
        pdfViewerPanel.add(tabbedPane, BorderLayout.CENTER);
    }
    
    
    private void createStatusBar() {
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel readyLabel = new JLabel("Ready");
        JLabel viewerLabel = new JLabel("PDF Viewer");
        if (TvMode.ENABLED) {
            readyLabel.setFont(TvMode.getStatusFont());
            viewerLabel.setFont(TvMode.getStatusFont());
        }
        statusBar.add(readyLabel);
        statusBar.add(new JSeparator(SwingConstants.VERTICAL));
        statusBar.add(viewerLabel);
        mainFrame.add(statusBar, BorderLayout.SOUTH);
    }
    
    
    // File operations
    private void newFile() {
        if (pdfViewerComponent != null) {
            pdfViewerComponent.clear();
        }
        currentFile = null;
        updateWindowTitle();
    }
    
    private void openFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Open File");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("All Files", "pdf", "diagram"));
        fileChooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF Files", "pdf"));
        fileChooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Diagram Files", "diagram"));
        
        int result = fileChooser.showOpenDialog(mainFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String fileName = selectedFile.getName().toLowerCase();
            
            try {
                if (fileName.endsWith(".pdf")) {
                    // Handle PDF file
                    loadPdfFromFile(selectedFile);
                } else if (fileName.endsWith(".diagram")) {
                    // Handle diagram file - not supported in PDF-only mode
                    showErrorDialog("Error", "Diagram files not supported", "This application now focuses on PDF viewing. Please select a PDF file.");
                } else {
                    showErrorDialog("Error", "Unsupported file type", "Please select a PDF or diagram file.");
                }
            } catch (Exception e) {
                showErrorDialog("Error", "Failed to open file", e.getMessage());
            }
        }
    }
    
    
    private void updateWindowTitle() {
        String title = "ZetdcDiagrammatics";
        if (currentFile != null) {
            title += " - " + currentFile.getName();
        } else {
            title += " - Untitled";
        }
        mainFrame.setTitle(title);
    }
    
    private void showErrorDialog(String title, String header, String content) {
        JOptionPane.showMessageDialog(mainFrame, content, title, JOptionPane.ERROR_MESSAGE);
    }
    
    private void loadPdfFile(String fileName) {
        // Update file name label
        fileNameLabel.setText("Selected: " + fileName);
        
        // Simulate PDF content loading
        String pdfContent = "PDF Content for: " + fileName + "\n\n" +
                           "This is a simulated PDF viewer.\n" +
                           "In a real implementation, this would display the actual PDF content.\n\n" +
                           "File: " + fileName + "\n" +
                           "Size: 2.5 MB\n" +
                           "Pages: 15\n" +
                           "Created: 2024-01-15\n\n" +
                           "Content preview:\n" +
                           "Lorem ipsum dolor sit amet, consectetur adipiscing elit.\n" +
                           "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.\n" +
                           "Ut enim ad minim veniam, quis nostrud exercitation ullamco.\n" +
                           "Laboris nisi ut aliquip ex ea commodo consequat.\n\n" +
                           "Duis aute irure dolor in reprehenderit in voluptate velit esse\n" +
                           "cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat\n" +
                           "cupidatat non proident, sunt in culpa qui officia deserunt\n" +
                           "mollit anim id est laborum.\n\n" +
                           "Sed ut perspiciatis unde omnis iste natus error sit voluptatem\n" +
                           "accusantium doloremque laudantium, totam rem aperiam, eaque\n" +
                           "ipsa quae ab illo inventore veritatis et quasi architecto beatae\n" +
                           "vitae dicta sunt explicabo.";
        
        pdfContentArea.setText(pdfContent);
        pdfContentArea.setCaretPosition(0); // Scroll to top
    }
    
    private void loadPdfFromFile(File pdfFile) {
        // Persist markers for the currently open file before switching
        try {
            if (currentPdfFile != null && pdfViewerComponent != null) {
                // Update in-memory state
                filePathToMarkers.put(currentPdfFile.getAbsolutePath(), pdfViewerComponent.exportMarkers());
                filePathToLines.put(currentPdfFile.getAbsolutePath(), pdfViewerComponent.exportLines());
                filePathToTexts.put(currentPdfFile.getAbsolutePath(), pdfViewerComponent.exportTexts());
                
                // Save immediately to project folder to persist deletions
                FileManager.saveAnnotations(
                    currentPdfFile,
                    filePathToMarkers.get(currentPdfFile.getAbsolutePath()),
                    filePathToLines.get(currentPdfFile.getAbsolutePath()),
                    filePathToTexts.get(currentPdfFile.getAbsolutePath())
                );
                
                // Also save to global state for backup
                saveAppState();
            }
        } catch (Exception ignore) {}

        // Update file name label
        fileNameLabel.setText("Selected: " + pdfFile.getName());
        
        try {
            // Check if it's a valid PDF (more lenient check)
            if (!pdfFile.exists() || !pdfFile.getName().toLowerCase().endsWith(".pdf")) {
                throw new IOException("File does not exist or is not a PDF file");
            }
            
            // Ensure project folder exists on Desktop for this PDF and try loading annotations
            try {
                FileManager.ensureProjectFolder(pdfFile);
            } catch (IOException ioe) {
                // Non-fatal: continue without stopping
            }

            // Get document information
            String docInfo = PdfReader.getDocumentInfo(pdfFile);

            // Build annotations / change history summary
            String historySection = buildAnnotationsSummary(pdfFile);

            // Defer text extraction to speed up initial open
            String contentPreview = "[Text preview deferred for faster open. Click 'View Full Content' to extract.]";
            
            // Check if content is empty and provide helpful message
            String contentSection;
            if (contentPreview.trim().isEmpty() || contentPreview.contains("placeholder")) {
                contentSection = "Content Preview (First 3 pages):\n" +
                               "================================\n\n" +
                               "This PDF appears to contain mostly graphics, drawings, or images.\n" +
                               "Text content may be limited or embedded as graphics.\n\n" +
                               "This is common for:\n" +
                               "• CAD drawings (AutoCAD files)\n" +
                               "• Scanned documents\n" +
                               "• Image-based PDFs\n" +
                               "• Technical drawings\n\n" +
                               "Click 'View Full Content' to see any available text content.";
            } else {
                contentSection = "Content Preview (First 3 pages):\n" +
                               "================================\n\n" +
                               contentPreview;
            }
            
            // Combine information, change history and content
            String fullContent = docInfo + "\n" + historySection + "\n" + contentSection;
            
            // If content is too long, truncate it
            if (fullContent.length() > 10000) {
                fullContent = fullContent.substring(0, 10000) + "\n\n... (Content truncated for display)";
            }
            
            pdfContentArea.setText(fullContent);
            pdfContentArea.setCaretPosition(0); // Scroll to top
            
            // Enable full content button and store current file
            currentPdfFile = pdfFile;
            viewFullContentButton.setEnabled(true);
            
            // Load PDF in visual viewer
            if (pdfViewerComponent != null) {
                pdfViewerComponent.loadPdf(pdfFile);
                // First try to load from Desktop project folder if available
                try {
                    FileManager.LoadedAnnotations loaded = FileManager.loadAnnotations(pdfFile);
                    if (loaded != null) {
                        if (loaded.markers != null && !loaded.markers.isEmpty()) {
                            pdfViewerComponent.importMarkers(loaded.markers);
                        }
                        if (loaded.lines != null && !loaded.lines.isEmpty()) {
                            pdfViewerComponent.importLines(loaded.lines);
                        }
                        if (loaded.texts != null && !loaded.texts.isEmpty()) {
                            pdfViewerComponent.importTexts(loaded.texts);
                        }
                    }
                } catch (IOException ignore) {
                    // If project annotations are not present, fall back to app state
                    Map<Integer, List<PdfViewerComponent.MarkerDTO>> restoredMarkers = filePathToMarkers.get(pdfFile.getAbsolutePath());
                    if (restoredMarkers != null) {
                        pdfViewerComponent.importMarkers(restoredMarkers);
                    }
                    Map<Integer, List<PdfViewerComponent.LineDTO>> restoredLines = filePathToLines.get(pdfFile.getAbsolutePath());
                    if (restoredLines != null) {
                        pdfViewerComponent.importLines(restoredLines);
                    }
                    Map<Integer, List<PdfViewerComponent.TextDTO>> restoredTexts = filePathToTexts.get(pdfFile.getAbsolutePath());
                    if (restoredTexts != null) {
                        pdfViewerComponent.importTexts(restoredTexts);
                    }
                }
            }
            
            // Add the file to the file list if not already present
            addFileToList(pdfFile);
            
        } catch (Exception e) {
            String errorContent = "Error loading PDF file: " + pdfFile.getName() + "\n\n" +
                                 "Error: " + e.getMessage() + "\n\n" +
                                 "File Information:\n" +
                                 "• Path: " + pdfFile.getAbsolutePath() + "\n" +
                                 "• Size: " + formatFileSize(pdfFile.length()) + " bytes\n" +
                                 "• Exists: " + pdfFile.exists() + "\n" +
                                 "• Can Read: " + pdfFile.canRead() + "\n" +
                                 "• Is File: " + pdfFile.isFile() + "\n\n" +
                                 "Possible causes:\n" +
                                 "• File is corrupted or damaged\n" +
                                 "• File is password protected\n" +
                                 "• File is being used by another application\n" +
                                 "• PDFBox library not available (for full content extraction)\n" +
                                 "• File format issues\n\n" +
                                 "Note: Basic file information is still available above.";
            
            pdfContentArea.setText(errorContent);
            pdfContentArea.setCaretPosition(0);
        }
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    private File findFileByName(String fileName) {
        return fileMap.get(fileName);
    }
    
    private void addFileToList(String fileName) {
        // Get current list data
        java.util.Vector<String> currentFiles = new java.util.Vector<>();
        for (int i = 0; i < fileList.getModel().getSize(); i++) {
            currentFiles.add(fileList.getModel().getElementAt(i));
        }
        
        // Add new file if not already present
        if (!currentFiles.contains(fileName)) {
            currentFiles.add(fileName);
            fileList.setListData(currentFiles);
            fileList.repaint(); // Force repaint to show the new item
        }
    }
    
    private void addFileToList(File file) {
        String fileName = file.getName();
        fileMap.put(fileName, file);
        
        // Get current list data
        java.util.Vector<String> currentFiles = new java.util.Vector<>();
        for (int i = 0; i < fileList.getModel().getSize(); i++) {
            currentFiles.add(fileList.getModel().getElementAt(i));
        }
        
        // Add new file if not already present
        if (!currentFiles.contains(fileName)) {
            currentFiles.add(fileName);
            fileList.setListData(currentFiles);
            fileList.repaint();
        }
    }
    
    private void viewFullPdfContent() {
        if (currentPdfFile == null) {
            return;
        }
        
        try {
            // Extract full text content
            String fullContent = PdfReader.extractText(currentPdfFile);
            
            // Get document info
            String docInfo = PdfReader.getDocumentInfo(currentPdfFile);

            // Build annotations / change history summary
            String historySection = buildAnnotationsSummary(currentPdfFile);
            
            // Check if content is meaningful
            String contentSection;
            if (fullContent.trim().isEmpty() || fullContent.length() < 50) {
                contentSection = "Full Document Content:\n" +
                               "======================\n\n" +
                               "This PDF contains minimal or no extractable text content.\n\n" +
                               "This is typical for:\n" +
                               "• CAD drawings and technical diagrams\n" +
                               "• Scanned documents (images)\n" +
                               "• Graphics-heavy documents\n" +
                               "• AutoCAD-generated PDFs\n\n" +
                               "The document likely contains:\n" +
                               "• Vector graphics and drawings\n" +
                               "• Technical specifications\n" +
                               "• Visual diagrams and layouts\n" +
                               "• Embedded images and symbols\n\n" +
                               "To view the visual content, you would need a PDF viewer\n" +
                               "that can render graphics (like Adobe Reader).\n\n" +
                               "Extracted content (if any):\n" +
                               "---------------------------\n" +
                               (fullContent.trim().isEmpty() ? "[No text content found]" : fullContent);
            } else {
                contentSection = "Full Document Content:\n" +
                               "======================\n\n" +
                               fullContent;
            }
            
            // Combine info, change history and content
            String completeContent = docInfo + "\n" + historySection + "\n" + contentSection;
            
            pdfContentArea.setText(completeContent);
            pdfContentArea.setCaretPosition(0); // Scroll to top
            
        } catch (Exception e) {
            String errorContent = "Error loading full PDF content: " + currentPdfFile.getName() + "\n\n" +
                                 "Error: " + e.getMessage() + "\n\n" +
                                 "This might be due to:\n" +
                                 "• File is password protected\n" +
                                 "• File is corrupted\n" +
                                 "• PDFBox library not available";
            
            pdfContentArea.setText(errorContent);
            pdfContentArea.setCaretPosition(0);
        }
    }

    /**
     * Build a summary of annotations and a simple change history
     * for the specified PDF file, based on the stored markers,
     * lines and text annotations (including their creators).
     */
    private String buildAnnotationsSummary(File pdfFile) {
        StringBuilder sb = new StringBuilder();
        sb.append("Annotations & Change History:\n");
        sb.append("================================\n\n");

        String path = pdfFile.getAbsolutePath();

        // Ensure we have the latest state from the viewer for this file
        if (currentPdfFile != null &&
            currentPdfFile.equals(pdfFile) &&
            pdfViewerComponent != null) {
            filePathToMarkers.put(path, pdfViewerComponent.exportMarkers());
            filePathToLines.put(path, pdfViewerComponent.exportLines());
            filePathToTexts.put(path, pdfViewerComponent.exportTexts());
        }

        Map<Integer, List<PdfViewerComponent.MarkerDTO>> markers =
            filePathToMarkers.getOrDefault(path, new HashMap<>());
        Map<Integer, List<PdfViewerComponent.LineDTO>> lines =
            filePathToLines.getOrDefault(path, new HashMap<>());
        Map<Integer, List<PdfViewerComponent.TextDTO>> texts =
            filePathToTexts.getOrDefault(path, new HashMap<>());

        int markerCount = 0;
        int lineCount = 0;
        int textCount = 0;
        java.util.Set<String> editors = new java.util.HashSet<>();

        for (List<PdfViewerComponent.MarkerDTO> list : markers.values()) {
            markerCount += list.size();
            for (PdfViewerComponent.MarkerDTO m : list) {
                if (m.createdBy != null && !m.createdBy.trim().isEmpty()) {
                    editors.add(m.createdBy.trim());
                }
            }
        }
        for (List<PdfViewerComponent.LineDTO> list : lines.values()) {
            lineCount += list.size();
            for (PdfViewerComponent.LineDTO l : list) {
                if (l.createdBy != null && !l.createdBy.trim().isEmpty()) {
                    editors.add(l.createdBy.trim());
                }
            }
        }
        for (List<PdfViewerComponent.TextDTO> list : texts.values()) {
            textCount += list.size();
            for (PdfViewerComponent.TextDTO t : list) {
                if (t.createdBy != null && !t.createdBy.trim().isEmpty()) {
                    editors.add(t.createdBy.trim());
                }
            }
        }

        sb.append("Total markers: ").append(markerCount).append("\n");
        sb.append("Total lines:   ").append(lineCount).append("\n");
        sb.append("Total notes:   ").append(textCount).append("\n\n");

        if (!editors.isEmpty()) {
            sb.append("Editors who made changes:\n");
            for (String editor : editors) {
                sb.append("  • ").append(editor).append("\n");
            }
            sb.append("\n");
        } else {
            sb.append("No recorded editors for this document yet.\n\n");
        }

        sb.append("Per‑page annotation summary:\n");
        sb.append("--------------------------------\n");

        java.util.SortedSet<Integer> pages = new java.util.TreeSet<>();
        pages.addAll(markers.keySet());
        pages.addAll(lines.keySet());
        pages.addAll(texts.keySet());

        if (pages.isEmpty()) {
            sb.append("No annotations have been created for this document.\n");
        } else {
            for (Integer page : pages) {
                int mc = markers.getOrDefault(page, java.util.Collections.emptyList()).size();
                int lc = lines.getOrDefault(page, java.util.Collections.emptyList()).size();
                int tc = texts.getOrDefault(page, java.util.Collections.emptyList()).size();
                sb.append("Page ").append(page).append(": ")
                  .append(mc).append(" markers, ")
                  .append(lc).append(" lines, ")
                  .append(tc).append(" notes\n");
            }
        }

        sb.append("\n");
        return sb.toString();
    }
    
    private void toggleFullScreen() {
        if (mainFrame.getExtendedState() == JFrame.MAXIMIZED_BOTH) {
            mainFrame.setExtendedState(JFrame.NORMAL);
        } else {
            mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
    }
    
    private void showAdminDialog() {
        // Prompt for admin credentials
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        JLabel userLabel = new JLabel("Admin Username:");
        JLabel passLabel = new JLabel("Admin Password:");
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
        
        int r = JOptionPane.showConfirmDialog(mainFrame, panel, "Admin Login", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;

        String usernameInput = userField.getText().trim();
        String passwordInput = new String(passField.getPassword());

        UserService.User admin = userService.verify(usernameInput, passwordInput);
        boolean isAdmin = admin != null && userService.isAdmin(admin);

        if (!isAdmin) {
            JOptionPane.showMessageDialog(mainFrame, "Invalid admin credentials.", "Access Denied", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        AdminDialog dialog = new AdminDialog(mainFrame, userService);
        dialog.setVisible(true);
    }
    
    private void showAboutDialog() {
        String message = "Schemmatics Digital Chart v1.0.0\n\n" +
                        "A modern PDF viewing and annotation application\n" +
                        "Built with Java Swing\n\n" +
                        "Developer: Nigel Onai Rodrick Sibanda\n" +
                        "System Engineer: Engineer Rungano Ziwani\n" +
                        "Organization: ZETDC Southern Region\n\n" +
                        "Features:\n" +
                        "• PDF file viewing and navigation\n" +
                        "• Mouse wheel zoom and pan\n" +
                        "• Page navigation controls\n" +
                        "• Text content extraction\n" +
                        "• File management\n" +
                        "• Visual PDF rendering\n" +
                        "• Full screen support\n" +
                        "• Auto-fit to window size\n" +
                        "• PDF annotation tools (markers, lines, text)";
        
        JOptionPane.showMessageDialog(mainFrame, message, "About", JOptionPane.INFORMATION_MESSAGE);
    }

    // ===== Persistence: save/load opened files and markers =====
    
    private void saveAppStatePermanently() throws Exception {
        // Ensure current viewer data is captured for current file
        if (currentPdfFile != null && pdfViewerComponent != null) {
            filePathToMarkers.put(currentPdfFile.getAbsolutePath(), pdfViewerComponent.exportMarkers());
            filePathToLines.put(currentPdfFile.getAbsolutePath(), pdfViewerComponent.exportLines());
            filePathToTexts.put(currentPdfFile.getAbsolutePath(), pdfViewerComponent.exportTexts());
        }

        // Build list of files from the JList
        List<String> files = new ArrayList<>();
        for (int i = 0; i < fileList.getModel().getSize(); i++) {
            String name = fileList.getModel().getElementAt(i);
            File f = fileMap.get(name);
            if (f != null && f.exists()) files.add(f.getAbsolutePath());
        }

        // Enhanced JSON-like serialization with error handling
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        // files
        sb.append("  \"files\": [");
        for (int i = 0; i < files.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(escape(files.get(i))).append("\"");
        }
        sb.append("],\n");
        // markers
        sb.append("  \"markers\": {\n");
        int fi = 0; int fcount = filePathToMarkers.size();
        for (Map.Entry<String, Map<Integer, List<PdfViewerComponent.MarkerDTO>>> fe : filePathToMarkers.entrySet()) {
            sb.append("    \"").append(escape(fe.getKey())).append("\": {");
            Map<Integer, List<PdfViewerComponent.MarkerDTO>> pm = fe.getValue();
            int pi = 0; int pcount = pm.size();
            for (Map.Entry<Integer, List<PdfViewerComponent.MarkerDTO>> pe : pm.entrySet()) {
                sb.append("\"").append(pe.getKey()).append("\": [");
                List<PdfViewerComponent.MarkerDTO> list = pe.getValue();
                for (int mi = 0; mi < list.size(); mi++) {
                    PdfViewerComponent.MarkerDTO m = list.get(mi);
                    if (mi > 0) sb.append(", ");
                    sb.append("{\"x\":").append(m.x)
                      .append(",\"y\":").append(m.y)
                      .append(",\"text\":\"").append(escape(m.text)).append("\"")
                      .append(",\"colorRgb\":").append(m.colorRgb).append("}");
                }
                sb.append("]");
                if (++pi < pcount) sb.append(",");
            }
            sb.append("}");
            if (++fi < fcount) sb.append(",\n"); else sb.append("\n");
        }
        sb.append("  },\n");
        
        // lines
        sb.append("  \"lines\": {\n");
        fi = 0; fcount = filePathToLines.size();
        for (Map.Entry<String, Map<Integer, List<PdfViewerComponent.LineDTO>>> fe : filePathToLines.entrySet()) {
            sb.append("    \"").append(escape(fe.getKey())).append("\": {");
            Map<Integer, List<PdfViewerComponent.LineDTO>> pm = fe.getValue();
            int pi2 = 0; int pcount2 = pm.size();
            for (Map.Entry<Integer, List<PdfViewerComponent.LineDTO>> pe : pm.entrySet()) {
                sb.append("\"").append(pe.getKey()).append("\": [");
                List<PdfViewerComponent.LineDTO> list = pe.getValue();
                for (int mi = 0; mi < list.size(); mi++) {
                    PdfViewerComponent.LineDTO l = list.get(mi);
                    if (mi > 0) sb.append(", ");
                    sb.append("{\"startX\":").append(l.startX)
                      .append(",\"startY\":").append(l.startY)
                      .append(",\"endX\":").append(l.endX)
                      .append(",\"endY\":").append(l.endY)
                      .append(",\"text\":\"").append(escape(l.text)).append("\"")
                      .append(",\"lineType\":").append(l.lineType)
                      .append(",\"radius\":").append(l.radius).append("}");
                }
                sb.append("]");
                if (++pi2 < pcount2) sb.append(",");
            }
            sb.append("}");
            if (++fi < fcount) sb.append(",\n"); else sb.append("\n");
        }
        sb.append("  },\n");
        
        // texts
        sb.append("  \"texts\": {\n");
        fi = 0; fcount = filePathToTexts.size();
        for (Map.Entry<String, Map<Integer, List<PdfViewerComponent.TextDTO>>> fe : filePathToTexts.entrySet()) {
            sb.append("    \"").append(escape(fe.getKey())).append("\": {");
            Map<Integer, List<PdfViewerComponent.TextDTO>> pm = fe.getValue();
            int pi3 = 0; int pcount3 = pm.size();
            for (Map.Entry<Integer, List<PdfViewerComponent.TextDTO>> pe : pm.entrySet()) {
                sb.append("\"").append(pe.getKey()).append("\": [");
                List<PdfViewerComponent.TextDTO> list = pe.getValue();
                for (int mi = 0; mi < list.size(); mi++) {
                    PdfViewerComponent.TextDTO t = list.get(mi);
                    if (mi > 0) sb.append(", ");
                    sb.append("{\"x\":").append(t.x)
                      .append(",\"y\":").append(t.y)
                      .append(",\"text\":\"").append(escape(t.text)).append("\"")
                      .append(",\"backgroundColorRgb\":").append(t.backgroundColorRgb).append("}");
                }
                sb.append("]");
                if (++pi3 < pcount3) sb.append(",");
            }
            sb.append("}");
            if (++fi < fcount) sb.append(",\n"); else sb.append("\n");
        }
        sb.append("  }\n");
        sb.append("}\n");

        // Write to file with proper error handling
        try {
            Files.write(stateFilePath, sb.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("Changes saved permanently to: " + stateFilePath);
        } catch (Exception e) {
            throw new Exception("Failed to write to file: " + e.getMessage());
        }
    }

    /**
     * Opens the current PDF's project folder in the system file explorer
     * so the user can easily share the exported/annotated documents.
     */
    private void openFolderForSharing() {
        try {
            if (currentPdfFile == null) {
                JOptionPane.showMessageDialog(mainFrame,
                    "No PDF is currently open. Open a diagram first.",
                    "Share Document",
                    JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            java.nio.file.Path projectFolder = FileManager.getProjectFolderForPdf(currentPdfFile);

            // Ensure annotations are up to date before sharing
            if (pdfViewerComponent != null) {
                filePathToMarkers.put(currentPdfFile.getAbsolutePath(), pdfViewerComponent.exportMarkers());
                filePathToLines.put(currentPdfFile.getAbsolutePath(), pdfViewerComponent.exportLines());
                filePathToTexts.put(currentPdfFile.getAbsolutePath(), pdfViewerComponent.exportTexts());
                FileManager.saveAnnotations(
                    currentPdfFile,
                    filePathToMarkers.get(currentPdfFile.getAbsolutePath()),
                    filePathToLines.get(currentPdfFile.getAbsolutePath()),
                    filePathToTexts.get(currentPdfFile.getAbsolutePath())
                );
            }

            java.awt.Desktop desktop = java.awt.Desktop.isDesktopSupported() ? java.awt.Desktop.getDesktop() : null;
            if (desktop != null && desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                desktop.open(projectFolder.toFile());
            } else {
                JOptionPane.showMessageDialog(mainFrame,
                    "Cannot open folder automatically. Please navigate to:\n" + projectFolder.toString(),
                    "Share Document",
                    JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainFrame,
                "Failed to open folder for sharing: " + ex.getMessage(),
                "Share Document",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void discardAllChanges() {
        // Clear all markers, lines, and text annotations
        if (pdfViewerComponent != null) {
            pdfViewerComponent.clear();
        }
        
        // Clear all stored data
        filePathToMarkers.clear();
        filePathToLines.clear();
        filePathToTexts.clear();
        fileList.setListData(new String[0]);
        fileMap.clear();
        
        // Reset UI
        fileNameLabel.setText("No file selected");
        currentPdfFile = null;
        
        // Show confirmation
        JOptionPane.showMessageDialog(mainFrame, 
            "All changes have been discarded permanently.", 
            "Changes Discarded", 
            JOptionPane.INFORMATION_MESSAGE);
        
        System.out.println("All changes discarded permanently");
    }
    
    private void exportPdfWithChanges() {
        if (currentPdfFile == null) {
            JOptionPane.showMessageDialog(mainFrame, 
                "No PDF file is currently open. Please open a PDF file first.", 
                "No File Open", 
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Check if there are any changes to export
        boolean hasChanges = false;
        if (pdfViewerComponent != null) {
            // Check if there are any markers, lines, or text annotations in the current component
            Map<Integer, List<PdfViewerComponent.MarkerDTO>> markers = pdfViewerComponent.exportMarkers();
            Map<Integer, List<PdfViewerComponent.LineDTO>> lines = pdfViewerComponent.exportLines();
            Map<Integer, List<PdfViewerComponent.TextDTO>> texts = pdfViewerComponent.exportTexts();
            
            hasChanges = (markers != null && !markers.isEmpty()) || 
                        (lines != null && !lines.isEmpty()) || 
                        (texts != null && !texts.isEmpty());
        }
        
        if (!hasChanges) {
            JOptionPane.showMessageDialog(mainFrame, 
                "No changes found to export. Please add markers, lines, or text annotations first.", 
                "No Changes", 
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // Choose export location
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export PDF with Changes");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF Files", "pdf"));
        
        // Suggest a filename
        String originalName = currentPdfFile.getName();
        String baseName = originalName.substring(0, originalName.lastIndexOf('.'));
        fileChooser.setSelectedFile(new File(baseName + "_with_changes.pdf"));
        
        int result = fileChooser.showSaveDialog(mainFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File exportFile = fileChooser.getSelectedFile();
            
            try {
                // Export PDF with embedded annotations
                exportPdfWithAnnotations(currentPdfFile, exportFile);
                JOptionPane.showMessageDialog(mainFrame, 
                    "PDF exported successfully with all changes!", 
                    "Export Complete", 
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(mainFrame, 
                    "Error exporting PDF: " + e.getMessage(), 
                    "Export Error", 
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void saveAppState() {
        try {
            // Build list of files from the JList
            List<String> files = new ArrayList<>();
            for (int i = 0; i < fileList.getModel().getSize(); i++) {
                String name = fileList.getModel().getElementAt(i);
                File f = fileMap.get(name);
                if (f != null && f.exists()) files.add(f.getAbsolutePath());
            }

            // Ensure current viewer data is captured for current file
            if (currentPdfFile != null && pdfViewerComponent != null) {
                filePathToMarkers.put(currentPdfFile.getAbsolutePath(), pdfViewerComponent.exportMarkers());
                filePathToLines.put(currentPdfFile.getAbsolutePath(), pdfViewerComponent.exportLines());
                filePathToTexts.put(currentPdfFile.getAbsolutePath(), pdfViewerComponent.exportTexts());
            }

            // Minimal JSON-like serialization (manual, avoids extra deps)
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            // files
            sb.append("  \"files\": [");
            for (int i = 0; i < files.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(escape(files.get(i))).append("\"");
            }
            sb.append("],\n");
            // markers
            sb.append("  \"markers\": {\n");
            int fi = 0; int fcount = filePathToMarkers.size();
            for (Map.Entry<String, Map<Integer, List<PdfViewerComponent.MarkerDTO>>> fe : filePathToMarkers.entrySet()) {
                sb.append("    \"").append(escape(fe.getKey())).append("\": {");
                Map<Integer, List<PdfViewerComponent.MarkerDTO>> pm = fe.getValue();
                int pi = 0; int pcount = pm.size();
                for (Map.Entry<Integer, List<PdfViewerComponent.MarkerDTO>> pe : pm.entrySet()) {
                    sb.append("\"").append(pe.getKey()).append("\": [");
                    List<PdfViewerComponent.MarkerDTO> list = pe.getValue();
                    for (int mi = 0; mi < list.size(); mi++) {
                        PdfViewerComponent.MarkerDTO m = list.get(mi);
                        if (mi > 0) sb.append(", ");
                        sb.append("{\"x\":").append(m.x)
                          .append(",\"y\":").append(m.y)
                          .append(",\"text\":\"").append(escape(m.text)).append("\"}");
                    }
                    sb.append("]");
                    if (++pi < pcount) sb.append(",");
                }
                sb.append("}");
                if (++fi < fcount) sb.append(",\n"); else sb.append("\n");
            }
            sb.append("  },\n");
            
            // lines
            sb.append("  \"lines\": {\n");
            fi = 0; fcount = filePathToLines.size();
            for (Map.Entry<String, Map<Integer, List<PdfViewerComponent.LineDTO>>> fe : filePathToLines.entrySet()) {
                sb.append("    \"").append(escape(fe.getKey())).append("\": {");
                Map<Integer, List<PdfViewerComponent.LineDTO>> pm = fe.getValue();
                int pi4 = 0; int pcount4 = pm.size();
                for (Map.Entry<Integer, List<PdfViewerComponent.LineDTO>> pe : pm.entrySet()) {
                    sb.append("\"").append(pe.getKey()).append("\": [");
                    List<PdfViewerComponent.LineDTO> list = pe.getValue();
                    for (int mi = 0; mi < list.size(); mi++) {
                        PdfViewerComponent.LineDTO l = list.get(mi);
                        if (mi > 0) sb.append(", ");
                        sb.append("{\"startX\":").append(l.startX)
                          .append(",\"startY\":").append(l.startY)
                          .append(",\"endX\":").append(l.endX)
                          .append(",\"endY\":").append(l.endY)
                          .append(",\"text\":\"").append(escape(l.text)).append("\"")
                          .append(",\"lineType\":").append(l.lineType)
                          .append(",\"radius\":").append(l.radius).append("}");
                    }
                    sb.append("]");
                    if (++pi4 < pcount4) sb.append(",");
                }
                sb.append("}");
                if (++fi < fcount) sb.append(",\n"); else sb.append("\n");
            }
            sb.append("  },\n");
            
            // texts
            sb.append("  \"texts\": {\n");
            fi = 0; fcount = filePathToTexts.size();
            for (Map.Entry<String, Map<Integer, List<PdfViewerComponent.TextDTO>>> fe : filePathToTexts.entrySet()) {
                sb.append("    \"").append(escape(fe.getKey())).append("\": {");
                Map<Integer, List<PdfViewerComponent.TextDTO>> pm = fe.getValue();
                int pi5 = 0; int pcount5 = pm.size();
                for (Map.Entry<Integer, List<PdfViewerComponent.TextDTO>> pe : pm.entrySet()) {
                    sb.append("\"").append(pe.getKey()).append("\": [");
                    List<PdfViewerComponent.TextDTO> list = pe.getValue();
                    for (int mi = 0; mi < list.size(); mi++) {
                        PdfViewerComponent.TextDTO t = list.get(mi);
                        if (mi > 0) sb.append(", ");
                        sb.append("{\"x\":").append(t.x)
                          .append(",\"y\":").append(t.y)
                          .append(",\"text\":\"").append(escape(t.text)).append("\"")
                          .append(",\"backgroundColorRgb\":").append(t.backgroundColorRgb).append("}");
                    }
                    sb.append("]");
                    if (++pi5 < pcount5) sb.append(",");
                }
                sb.append("}");
                if (++fi < fcount) sb.append(",\n"); else sb.append("\n");
            }
            sb.append("  }\n");
            sb.append("}\n");

            Files.write(stateFilePath, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            // Silently ignore persistence errors for now
        }
    }

    private void loadAppState() {
        try {
            File fileToOpen = null;
            long bestTimestamp = Long.MIN_VALUE;
            // 1) Scan Desktop/ZetdcDiagrammatics projects and load all edits and files
            try {
                java.nio.file.Path projectsRoot = com.zetdc.diagrammatics.utils.FileManager.getProjectsRoot();
                if (Files.exists(projectsRoot)) {
                    try (java.util.stream.Stream<java.nio.file.Path> entries = Files.list(projectsRoot)) {
                        java.util.List<java.nio.file.Path> projectDirs = entries.filter(Files::isDirectory).toList();
                        for (java.nio.file.Path dir : projectDirs) {
                            java.nio.file.Path annotations = dir.resolve("annotations.json");
                            if (Files.exists(annotations)) {
                                com.zetdc.diagrammatics.utils.FileManager.LoadedAnnotations loaded = com.zetdc.diagrammatics.utils.FileManager.loadAnnotations(annotations);
                                String source = loaded.sourcePdfPath;
                                if (source != null) {
                                    File pdf = new File(source);
                                    if (pdf.exists()) {
                                        addFileToList(pdf);
                                        // Do not open yet; populate memory so opening is fast
                                        if (loaded.markers != null && !loaded.markers.isEmpty()) {
                                            filePathToMarkers.put(pdf.getAbsolutePath(), loaded.markers);
                                        }
                                        if (loaded.lines != null && !loaded.lines.isEmpty()) {
                                            filePathToLines.put(pdf.getAbsolutePath(), loaded.lines);
                                        }
                                        if (loaded.texts != null && !loaded.texts.isEmpty()) {
                                            filePathToTexts.put(pdf.getAbsolutePath(), loaded.texts);
                                        }
                                        // Choose the most recently modified annotations.json as last project
                                        try {
                                            long ts = Files.getLastModifiedTime(annotations).toMillis();
                                            if (ts > bestTimestamp) {
                                                bestTimestamp = ts;
                                                fileToOpen = pdf;
                                            }
                                        } catch (Exception ignoreTs) {}
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignore) { }

            // 2) Load previous lightweight app state (for any files outside projects)
            if (Files.exists(stateFilePath)) {
                String json = new String(Files.readAllBytes(stateFilePath), StandardCharsets.UTF_8);
                List<String> files = parseStringArray(json, "files");
                Map<String, Map<Integer, List<PdfViewerComponent.MarkerDTO>>> markers = parseMarkers(json);
                if (markers != null) filePathToMarkers.putAll(markers);
                Map<String, Map<Integer, List<PdfViewerComponent.LineDTO>>> lines = parseLines(json);
                if (lines != null) filePathToLines.putAll(lines);
                Map<String, Map<Integer, List<PdfViewerComponent.TextDTO>>> texts = parseTexts(json);
                if (texts != null) filePathToTexts.putAll(texts);

                for (String p : files) {
                    File f = new File(p);
                    if (f.exists()) {
                        addFileToList(f);
                    }
                }
                if (!files.isEmpty()) {
                    File last = new File(files.get(files.size() - 1));
                    if (last.exists()) {
                        try {
                            long ts = Files.getLastModifiedTime(stateFilePath).toMillis();
                            if (ts > bestTimestamp) {
                                bestTimestamp = ts;
                                fileToOpen = last;
                            }
                        } catch (Exception ignoreTs) {}
                    }
                }
            }

            // 3) Open the best candidate if available
            if (fileToOpen != null && fileToOpen.exists()) {
                loadPdfFromFile(fileToOpen);
            }
        } catch (Exception ex) {
            // Ignore parsing errors
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static List<String> parseStringArray(String json, String key) {
        List<String> out = new ArrayList<>();
        try {
            String look = "\"" + key + "\"";
            int i = json.indexOf(look);
            if (i < 0) return out;
            int lb = json.indexOf('[', i);
            int rb = json.indexOf(']', lb);
            if (lb < 0 || rb < 0) return out;
            String arr = json.substring(lb + 1, rb).trim();
            if (arr.isEmpty()) return out;
            for (String part : arr.split(",")) {
                String p = part.trim();
                if (p.startsWith("\"") && p.endsWith("\"")) {
                    out.add(p.substring(1, p.length() - 1).replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\"));
                }
            }
        } catch (Exception ignore) {}
        return out;
    }

    private static Map<String, Map<Integer, List<PdfViewerComponent.MarkerDTO>>> parseMarkers(String json) {
        Map<String, Map<Integer, List<PdfViewerComponent.MarkerDTO>>> out = new HashMap<>();
        try {
            String look = "\"markers\"";
            int i = json.indexOf(look);
            if (i < 0) return out;
            int lb = json.indexOf('{', i);
            int rb = json.indexOf("}\n", lb);
            if (lb < 0 || rb < 0) return out;
            String block = json.substring(lb + 1, rb).trim();
            // Split by files keys
            int pos = 0;
            while (pos < block.length()) {
                int k1 = block.indexOf('"', pos);
                if (k1 < 0) break;
                int k2 = block.indexOf('"', k1 + 1);
                String file = block.substring(k1 + 1, k2).replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
                int ob = block.indexOf('{', k2);
                int cb = block.indexOf('}', ob);
                String pages = block.substring(ob + 1, cb);
                Map<Integer, List<PdfViewerComponent.MarkerDTO>> pageMap = new HashMap<>();
                // pages entries: "<num>":[{...},{...}],...
                int pp = 0;
                while (pp < pages.length()) {
                    int pk1 = pages.indexOf('"', pp);
                    if (pk1 < 0) break;
                    int pk2 = pages.indexOf('"', pk1 + 1);
                    String pStr = pages.substring(pk1 + 1, pk2);
                    int arrL = pages.indexOf('[', pk2);
                    int arrR = pages.indexOf(']', arrL);
                    String arr = pages.substring(arrL + 1, arrR);
                    List<PdfViewerComponent.MarkerDTO> list = new ArrayList<>();
                    if (!arr.trim().isEmpty()) {
                        // items like {"x":1.0,"y":2.0,"text":"..."}
                        String[] items = arr.split("\\},");
                        for (String it : items) {
                            String item = it.trim();
                            if (!item.endsWith("}")) item = item + "}";
                            float x = parseFloatField(item, "x");
                            float y = parseFloatField(item, "y");
                            String t = parseStringField(item, "text");
                            list.add(new PdfViewerComponent.MarkerDTO(x, y, t));
                        }
                    }
                    pageMap.put(Integer.parseInt(pStr), list);
                    pp = arrR + 1;
                    int comma = pages.indexOf(',', pp);
                    if (comma < 0) break;
                    pp = comma + 1;
                }
                out.put(file, pageMap);
                pos = cb + 1;
                int comma = block.indexOf(',', pos);
                if (comma < 0) break;
                pos = comma + 1;
            }
        } catch (Exception ignore) {}
        return out;
    }

    private static Map<String, Map<Integer, List<PdfViewerComponent.LineDTO>>> parseLines(String json) {
        Map<String, Map<Integer, List<PdfViewerComponent.LineDTO>>> out = new HashMap<>();
        try {
            String look = "\"lines\"";
            int i = json.indexOf(look);
            if (i < 0) return out;
            int lb = json.indexOf('{', i);
            int rb = json.indexOf("}\n", lb);
            if (lb < 0 || rb < 0) return out;
            String block = json.substring(lb + 1, rb).trim();
            // Split by files keys
            int pos = 0;
            while (pos < block.length()) {
                int keyStart = block.indexOf('"', pos);
                if (keyStart < 0) break;
                int keyEnd = block.indexOf('"', keyStart + 1);
                if (keyEnd < 0) break;
                String file = block.substring(keyStart + 1, keyEnd);
                int cb = block.indexOf('{', keyEnd);
                int ce = block.indexOf('}', cb);
                if (cb < 0 || ce < 0) break;
                String pages = block.substring(cb + 1, ce);
                Map<Integer, List<PdfViewerComponent.LineDTO>> pageMap = new HashMap<>();
                int pp = 0;
                while (pp < pages.length()) {
                    int pStart = pages.indexOf('"', pp);
                    if (pStart < 0) break;
                    int pEnd = pages.indexOf('"', pStart + 1);
                    if (pEnd < 0) break;
                    String pStr = pages.substring(pStart + 1, pEnd);
                    int arrL = pages.indexOf('[', pEnd);
                    int arrR = pages.indexOf(']', arrL);
                    if (arrL < 0 || arrR < 0) break;
                    String arr = pages.substring(arrL + 1, arrR);
                    List<PdfViewerComponent.LineDTO> list = new ArrayList<>();
                    if (!arr.trim().isEmpty()) {
                        // items like {"startX":1.0,"startY":2.0,"endX":3.0,"endY":4.0,"text":"...","lineType":0,"radius":0.0}
                        String[] items = arr.split("\\},");
                        for (String it : items) {
                            String item = it.trim();
                            if (!item.endsWith("}")) item = item + "}";
                            float startX = parseFloatField(item, "startX");
                            float startY = parseFloatField(item, "startY");
                            float endX = parseFloatField(item, "endX");
                            float endY = parseFloatField(item, "endY");
                            String t = parseStringField(item, "text");
                            int lineType = parseIntField(item, "lineType");
                            float radius = parseFloatField(item, "radius");
                            PdfViewerComponent.LineDTO dto = new PdfViewerComponent.LineDTO(startX, startY, endX, endY, t);
                            dto.lineType = lineType;
                            dto.radius = radius;
                            list.add(dto);
                        }
                    }
                    pageMap.put(Integer.parseInt(pStr), list);
                    pp = arrR + 1;
                    int comma = pages.indexOf(',', pp);
                    if (comma < 0) break;
                    pp = comma + 1;
                }
                out.put(file, pageMap);
                pos = cb + 1;
                int comma = block.indexOf(',', pos);
                if (comma < 0) break;
                pos = comma + 1;
            }
        } catch (Exception ignore) {}
        return out;
    }

    private static Map<String, Map<Integer, List<PdfViewerComponent.TextDTO>>> parseTexts(String json) {
        Map<String, Map<Integer, List<PdfViewerComponent.TextDTO>>> out = new HashMap<>();
        try {
            String look = "\"texts\"";
            int i = json.indexOf(look);
            if (i < 0) return out;
            int lb = json.indexOf('{', i);
            int rb = json.indexOf("}\n", lb);
            if (lb < 0 || rb < 0) return out;
            String block = json.substring(lb + 1, rb).trim();
            // Split by files keys
            int pos = 0;
            while (pos < block.length()) {
                int keyStart = block.indexOf('"', pos);
                if (keyStart < 0) break;
                int keyEnd = block.indexOf('"', keyStart + 1);
                if (keyEnd < 0) break;
                String file = block.substring(keyStart + 1, keyEnd);
                int cb = block.indexOf('{', keyEnd);
                int ce = block.indexOf('}', cb);
                if (cb < 0 || ce < 0) break;
                String pages = block.substring(cb + 1, ce);
                Map<Integer, List<PdfViewerComponent.TextDTO>> pageMap = new HashMap<>();
                int pp = 0;
                while (pp < pages.length()) {
                    int pStart = pages.indexOf('"', pp);
                    if (pStart < 0) break;
                    int pEnd = pages.indexOf('"', pStart + 1);
                    if (pEnd < 0) break;
                    String pStr = pages.substring(pStart + 1, pEnd);
                    int arrL = pages.indexOf('[', pEnd);
                    int arrR = pages.indexOf(']', arrL);
                    if (arrL < 0 || arrR < 0) break;
                    String arr = pages.substring(arrL + 1, arrR);
                    List<PdfViewerComponent.TextDTO> list = new ArrayList<>();
                    if (!arr.trim().isEmpty()) {
                        // items like {"x":1.0,"y":2.0,"text":"...","backgroundColorRgb":-1}
                        String[] items = arr.split("\\},");
                        for (String it : items) {
                            String item = it.trim();
                            if (!item.endsWith("}")) item = item + "}";
                            float x = parseFloatField(item, "x");
                            float y = parseFloatField(item, "y");
                            String t = parseStringField(item, "text");
                            int backgroundColorRgb = parseIntField(item, "backgroundColorRgb");
                            list.add(new PdfViewerComponent.TextDTO(x, y, t, new Color(backgroundColorRgb, true)));
                        }
                    }
                    pageMap.put(Integer.parseInt(pStr), list);
                    pp = arrR + 1;
                    int comma = pages.indexOf(',', pp);
                    if (comma < 0) break;
                    pp = comma + 1;
                }
                out.put(file, pageMap);
                pos = cb + 1;
                int comma = block.indexOf(',', pos);
                if (comma < 0) break;
                pos = comma + 1;
            }
        } catch (Exception ignore) {}
        return out;
    }

    private static float parseFloatField(String json, String field) {
        try {
            String look = "\"" + field + "\":";
            int i = json.indexOf(look);
            if (i < 0) return 0f;
            int start = i + look.length();
            int end = start;
            while (end < json.length() && "-+.0123456789".indexOf(json.charAt(end)) >= 0) end++;
            return Float.parseFloat(json.substring(start, end));
        } catch (Exception e) { return 0f; }
    }

    private static int parseIntField(String json, String field) {
        try {
            String look = "\"" + field + "\":";
            int i = json.indexOf(look);
            if (i < 0) return 0;
            int start = i + look.length();
            int end = start;
            while (end < json.length() && "-0123456789".indexOf(json.charAt(end)) >= 0) end++;
            return Integer.parseInt(json.substring(start, end));
        } catch (Exception e) { return 0; }
    }

    private static String parseStringField(String json, String field) {
        try {
            String look = "\"" + field + "\":\"";
            int i = json.indexOf(look);
            if (i < 0) return "";
            int start = i + look.length();
            int end = json.indexOf('"', start);
            String raw = json.substring(start, end);
            return raw.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
        } catch (Exception e) { return ""; }
    }
    
    // ===== File List Context Menu Methods =====
    
    /**
     * Save all edits for all opened PDF files
     * This ensures all annotations are persisted before closing
     */
    private void saveAllEdits() {
        // First, save current file's edits if it exists
        if (currentPdfFile != null && pdfViewerComponent != null) {
            try {
                // Update in-memory state with current file's edits
                filePathToMarkers.put(currentPdfFile.getAbsolutePath(), pdfViewerComponent.exportMarkers());
                filePathToLines.put(currentPdfFile.getAbsolutePath(), pdfViewerComponent.exportLines());
                filePathToTexts.put(currentPdfFile.getAbsolutePath(), pdfViewerComponent.exportTexts());
            } catch (Exception e) {
                System.err.println("Error exporting current file edits: " + e.getMessage());
            }
        }
        
        // Collect all unique file paths from all three maps
        java.util.Set<String> allFilePaths = new java.util.HashSet<>();
        allFilePaths.addAll(filePathToMarkers.keySet());
        allFilePaths.addAll(filePathToLines.keySet());
        allFilePaths.addAll(filePathToTexts.keySet());
        
        // Save all files that have annotations
        int savedCount = 0;
        int errorCount = 0;
        
        for (String filePath : allFilePaths) {
            try {
                File pdfFile = new File(filePath);
                if (!pdfFile.exists()) {
                    continue; // Skip files that no longer exist
                }
                
                Map<Integer, List<PdfViewerComponent.MarkerDTO>> markers = filePathToMarkers.get(filePath);
                Map<Integer, List<PdfViewerComponent.LineDTO>> lines = filePathToLines.get(filePath);
                Map<Integer, List<PdfViewerComponent.TextDTO>> texts = filePathToTexts.get(filePath);
                
                // Only save if there are actual annotations
                boolean hasAnnotations = (markers != null && !markers.isEmpty()) ||
                                        (lines != null && !lines.isEmpty()) ||
                                        (texts != null && !texts.isEmpty());
                
                if (hasAnnotations) {
                    FileManager.saveAnnotations(pdfFile, markers, lines, texts);
                    savedCount++;
                    System.out.println("Saved edits for: " + pdfFile.getName());
                }
            } catch (Exception e) {
                errorCount++;
                System.err.println("Error saving edits for file: " + filePath + " - " + e.getMessage());
            }
        }
        
        if (savedCount > 0) {
            System.out.println("Successfully saved edits for " + savedCount + " file(s)");
        }
        if (errorCount > 0) {
            System.err.println("Failed to save edits for " + errorCount + " file(s)");
        }
    }
    
    private void saveCurrentFile() {
        if (currentPdfFile == null) {
            JOptionPane.showMessageDialog(mainFrame, "No file is currently selected.", "No File Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        try {
            // Save annotations into Desktop project folder for this PDF
            try {
                Map<Integer, List<PdfViewerComponent.MarkerDTO>> markers = pdfViewerComponent.exportMarkers();
                Map<Integer, List<PdfViewerComponent.LineDTO>> lines = pdfViewerComponent.exportLines();
                Map<Integer, List<PdfViewerComponent.TextDTO>> texts = pdfViewerComponent.exportTexts();
                FileManager.saveAnnotations(currentPdfFile, markers, lines, texts);
            } catch (IOException ioe) {
                JOptionPane.showMessageDialog(mainFrame, "Error saving to project folder: " + ioe.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            // Also save lightweight app state
            saveAppState();
            hasUnsavedChanges = false;
            JOptionPane.showMessageDialog(mainFrame, "Changes saved successfully!", "Save Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainFrame, "Error saving changes: " + e.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    
    private void removeSelectedFile() {
        int selectedIndex = fileList.getSelectedIndex();
        if (selectedIndex < 0) {
            JOptionPane.showMessageDialog(mainFrame, "Please select a file to remove.", "No File Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String fileName = fileList.getSelectedValue();
        int confirm = JOptionPane.showConfirmDialog(mainFrame, 
            "Are you sure you want to remove '" + fileName + "' from the list?", 
            "Confirm Removal", 
            JOptionPane.YES_NO_OPTION, 
            JOptionPane.QUESTION_MESSAGE);
            
        if (confirm == JOptionPane.YES_OPTION) {
            // Remove from fileMap
            fileMap.remove(fileName);
            
            // Remove from file list
            java.util.Vector<String> currentFiles = new java.util.Vector<>();
            for (int i = 0; i < fileList.getModel().getSize(); i++) {
                if (i != selectedIndex) {
                    currentFiles.add(fileList.getModel().getElementAt(i));
                }
            }
            fileList.setListData(currentFiles);
            
            // Clear current file if it was the one removed
            if (currentPdfFile != null && currentPdfFile.getName().equals(fileName)) {
                currentPdfFile = null;
                pdfViewerComponent.loadPdf(null);
                fileNameLabel.setText("No file selected");
            }
            
            // Save the updated state
            saveAppState();
        }
    }
    
    private void exportPdfWithAnnotations(File sourceFile, File targetFile) throws Exception {
        try {
            // Robust path: render each page (with annotations) to an image and build a new PDF.
            exportPdfAsImages(sourceFile, targetFile);
            System.out.println("PDF exported with annotated page images successfully!");
        } catch (Exception e) {
            // Fallback to companion file method if even image export fails
            System.out.println("Image-based export failed, using companion file method: " + e.getMessage());
            exportPdfWithCompanionFile(sourceFile, targetFile);
            throw new Exception("PDF visual annotation embedding failed, created companion file instead: " + e.getMessage());
        }
    }

    /**
     * Robust export: render each page (with annotations) as an image and
     * create a new PDF composed of those page images.
     */
    private void exportPdfAsImages(File sourceFile, File targetFile) throws Exception {
        // Prepare PDFBox classes via reflection
        Class<?> pdDocumentClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
        Class<?> pdPageClass = Class.forName("org.apache.pdfbox.pdmodel.PDPage");
        Class<?> pdRectClass = Class.forName("org.apache.pdfbox.pdmodel.common.PDRectangle");
        Class<?> imageXObjectClass = Class.forName("org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject");
        Class<?> losslessFactoryClass = Class.forName("org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory");
        Class<?> contentStreamClass = Class.forName("org.apache.pdfbox.pdmodel.PDPageContentStream");

        // New document that will contain image pages
        Object targetDoc = pdDocumentClass.getConstructor().newInstance();

        // Get current annotations snapshot once
        Map<Integer, List<PdfViewerComponent.MarkerDTO>> markers =
            pdfViewerComponent != null ? pdfViewerComponent.exportMarkers() : new HashMap<>();
        Map<Integer, List<PdfViewerComponent.LineDTO>> lines =
            pdfViewerComponent != null ? pdfViewerComponent.exportLines() : new HashMap<>();
        Map<Integer, List<PdfViewerComponent.TextDTO>> texts =
            pdfViewerComponent != null ? pdfViewerComponent.exportTexts() : new HashMap<>();

        // Number of pages
        int pageCount = PdfRenderer.getPageCount(sourceFile);

        for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
            // Render base PDF page as image
            java.awt.image.BufferedImage pageImage =
                PdfRenderer.renderPage(sourceFile, pageNumber, 1.0f);

            // Draw annotations onto the image
            java.awt.image.BufferedImage annotated =
                new java.awt.image.BufferedImage(pageImage.getWidth(), pageImage.getHeight(),
                                                 java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = annotated.createGraphics();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.drawImage(pageImage, 0, 0, null);

            int pageKey = pageNumber; // annotations use 1-based page indices

            // Markers
            List<PdfViewerComponent.MarkerDTO> pageMarkers =
                markers != null ? markers.get(pageKey) : null;
            if (pageMarkers != null) {
                for (PdfViewerComponent.MarkerDTO m : pageMarkers) {
                    int cx = Math.round(m.x);
                    int cy = Math.round(m.y);
                    int r = 8;
                    int d = r * 2;
                    Color color = new Color(m.colorRgb, true);
                    g2.setColor(color);
                    g2.fillOval(cx - r, cy - r, d, d);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawOval(cx - r, cy - r, d, d);

                    if (m.text != null && !m.text.trim().isEmpty()) {
                        String text = m.text.trim();
                        Font base = g2.getFont();
                        g2.setFont(base.deriveFont(12f));
                        FontMetrics fm = g2.getFontMetrics();
                        int pad = 4;
                        int tw = fm.stringWidth(text);
                        int th = fm.getHeight();
                        int bx = cx + r + 8;
                        int by = cy - th / 2;
                        g2.setColor(new Color(0, 0, 0, 170));
                        g2.fillRoundRect(bx - pad, by - th + fm.getAscent() - pad,
                                         tw + pad * 2, th + pad,
                                         6, 6);
                        g2.setColor(Color.WHITE);
                        g2.drawString(text, bx, by);
                    }
                }
            }

            // Lines
            List<PdfViewerComponent.LineDTO> pageLines =
                lines != null ? lines.get(pageKey) : null;
            if (pageLines != null) {
                for (PdfViewerComponent.LineDTO l : pageLines) {
                    g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setColor(Color.RED);
                    int x1 = Math.round(l.startX);
                    int y1 = Math.round(l.startY);
                    int x2 = Math.round(l.endX);
                    int y2 = Math.round(l.endY);
                    g2.drawLine(x1, y1, x2, y2);

                    if (l.text != null && !l.text.trim().isEmpty()) {
                        String text = l.text.trim();
                        Font base = g2.getFont();
                        g2.setFont(base.deriveFont(12f));
                        FontMetrics fm = g2.getFontMetrics();
                        int pad = 4;
                        int midX = (x1 + x2) / 2;
                        int midY = (y1 + y2) / 2;
                        int tw = fm.stringWidth(text);
                        int th = fm.getHeight();
                        g2.setColor(new Color(0, 0, 0, 170));
                        g2.fillRoundRect(midX - tw / 2 - pad, midY - th / 2 - pad + fm.getAscent() - th,
                                         tw + pad * 2, th + pad,
                                         6, 6);
                        g2.setColor(Color.WHITE);
                        g2.drawString(text, midX - tw / 2, midY);
                    }
                }
            }

            // Text annotations
            List<PdfViewerComponent.TextDTO> pageTexts =
                texts != null ? texts.get(pageKey) : null;
            if (pageTexts != null) {
                for (PdfViewerComponent.TextDTO t : pageTexts) {
                    String label = t.text != null ? t.text : "";
                    Color bg = new Color(t.backgroundColorRgb, true);
                    Font base = g2.getFont();
                    g2.setFont(base.deriveFont(12f));
                    FontMetrics fm = g2.getFontMetrics();
                    int pad = 4;
                    int drawX = Math.round(t.x);
                    int drawY = Math.round(t.y);
                    int tw = fm.stringWidth(label);
                    int th = fm.getHeight();

                    g2.setColor(bg);
                    g2.fillRoundRect(drawX - pad,
                                     drawY - th + fm.getAscent() - pad,
                                     tw + pad * 2,
                                     th + pad,
                                     6, 6);
                    g2.setColor(Color.BLACK);
                    g2.drawString(label, drawX, drawY);
                }
            }

            g2.dispose();

            // Create PDF page sized to the image
            float width = annotated.getWidth();
            float height = annotated.getHeight();
            Object rect = pdRectClass.getConstructor(float.class, float.class)
                                     .newInstance(width, height);
            Object page = pdPageClass.getConstructor(pdRectClass).newInstance(rect);
            pdDocumentClass.getMethod("addPage", pdPageClass).invoke(targetDoc, page);

            // Create image XObject from annotated image
            Object imageXObject = losslessFactoryClass
                .getMethod("createFromImage", pdDocumentClass, java.awt.image.BufferedImage.class)
                .invoke(null, targetDoc, annotated);

            // Draw image to fill the page
            Object contentStream = contentStreamClass
                .getConstructor(pdDocumentClass, pdPageClass)
                .newInstance(targetDoc, page);
            contentStreamClass
                .getMethod("drawImage", imageXObjectClass, float.class, float.class, float.class, float.class)
                .invoke(contentStream, imageXObject, 0f, 0f, width, height);
            contentStreamClass.getMethod("close").invoke(contentStream);
        }

        // Save and close
        pdDocumentClass.getMethod("save", File.class).invoke(targetDoc, targetFile);
        pdDocumentClass.getMethod("close").invoke(targetDoc);
    }
    
    private void exportPdfWithCompanionFile(File sourceFile, File targetFile) throws Exception {
        // Copy the original PDF file
        java.nio.file.Files.copy(sourceFile.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        
        // Create a companion file with annotation data
        File annotationFile = new File(targetFile.getParent(), 
            targetFile.getName().substring(0, targetFile.getName().lastIndexOf('.')) + "_annotations.txt");
        
        try (java.io.PrintWriter writer = new java.io.PrintWriter(annotationFile, "UTF-8")) {
            writer.println("=====================================");
            writer.println("ZETDC DIAGRAMMATICS - ANNOTATION REPORT");
            writer.println("=====================================");
            writer.println("PDF File: " + targetFile.getName());
            writer.println("Original PDF: " + sourceFile.getName());
            writer.println("Generated on: " + java.time.LocalDateTime.now());
            writer.println("Application: Shemmatics Control Application");
            writer.println("=====================================");
            
            if (pdfViewerComponent != null && currentPdfFile != null) {
                // Get current annotations directly from the viewer component
                Map<Integer, List<PdfViewerComponent.MarkerDTO>> markers = pdfViewerComponent.exportMarkers();
                Map<Integer, List<PdfViewerComponent.LineDTO>> lines = pdfViewerComponent.exportLines();
                Map<Integer, List<PdfViewerComponent.TextDTO>> texts = pdfViewerComponent.exportTexts();
                
                int totalAnnotations = 0;
                if (markers != null) totalAnnotations += markers.values().stream().mapToInt(List::size).sum();
                if (lines != null) totalAnnotations += lines.values().stream().mapToInt(List::size).sum();
                if (texts != null) totalAnnotations += texts.values().stream().mapToInt(List::size).sum();
                
                writer.println("\nSUMMARY:");
                writer.println("Total Annotations: " + totalAnnotations);
                writer.println("Markers: " + (markers != null ? markers.values().stream().mapToInt(List::size).sum() : 0));
                writer.println("Lines: " + (lines != null ? lines.values().stream().mapToInt(List::size).sum() : 0));
                writer.println("Texts: " + (texts != null ? texts.values().stream().mapToInt(List::size).sum() : 0));
                
                if (markers != null && !markers.isEmpty()) {
                    writer.println("\n" + "=".repeat(50));
                    writer.println("MARKERS (Red Circles with Text)");
                    writer.println("=".repeat(50));
                    for (Map.Entry<Integer, List<PdfViewerComponent.MarkerDTO>> entry : markers.entrySet()) {
                        int pageIndex = entry.getKey();
                        List<PdfViewerComponent.MarkerDTO> pageMarkers = entry.getValue();
                        
                        writer.println("\nPage " + (pageIndex + 1) + " (" + pageMarkers.size() + " markers):");
                        for (int i = 0; i < pageMarkers.size(); i++) {
                            PdfViewerComponent.MarkerDTO marker = pageMarkers.get(i);
                            writer.println("  " + (i + 1) + ". Position: (" + String.format("%.1f", marker.x) + ", " + String.format("%.1f", marker.y) + ")");
                            if (marker.text != null && !marker.text.trim().isEmpty()) {
                                writer.println("     Text: \"" + marker.text + "\"");
                            }
                        }
                    }
                }
                
                if (lines != null && !lines.isEmpty()) {
                    writer.println("\n" + "=".repeat(50));
                    writer.println("LINES (Red Lines)");
                    writer.println("=".repeat(50));
                    for (Map.Entry<Integer, List<PdfViewerComponent.LineDTO>> entry : lines.entrySet()) {
                        int pageIndex = entry.getKey();
                        List<PdfViewerComponent.LineDTO> pageLines = entry.getValue();
                        
                        writer.println("\nPage " + (pageIndex + 1) + " (" + pageLines.size() + " lines):");
                        for (int i = 0; i < pageLines.size(); i++) {
                            PdfViewerComponent.LineDTO line = pageLines.get(i);
                            writer.println("  " + (i + 1) + ". From: (" + String.format("%.1f", line.startX) + ", " + String.format("%.1f", line.startY) + ")");
                            writer.println("     To: (" + String.format("%.1f", line.endX) + ", " + String.format("%.1f", line.endY) + ")");
                            String lineType = line.lineType == 0 ? "Polyline" : line.lineType == 1 ? "Straight" : "Circle";
                            writer.println("     Type: " + lineType);
                        }
                    }
                }
                
                if (texts != null && !texts.isEmpty()) {
                    writer.println("\n" + "=".repeat(50));
                    writer.println("TEXT ANNOTATIONS (Yellow Background)");
                    writer.println("=".repeat(50));
                    for (Map.Entry<Integer, List<PdfViewerComponent.TextDTO>> entry : texts.entrySet()) {
                        int pageIndex = entry.getKey();
                        List<PdfViewerComponent.TextDTO> pageTexts = entry.getValue();
                        
                        writer.println("\nPage " + (pageIndex + 1) + " (" + pageTexts.size() + " texts):");
                        for (int i = 0; i < pageTexts.size(); i++) {
                            PdfViewerComponent.TextDTO text = pageTexts.get(i);
                            writer.println("  " + (i + 1) + ". Position: (" + String.format("%.1f", text.x) + ", " + String.format("%.1f", text.y) + ")");
                            writer.println("     Text: \"" + text.text + "\"");
                        }
                    }
                }
                
                if (totalAnnotations == 0) {
                    writer.println("\n" + "=".repeat(50));
                    writer.println("NO ANNOTATIONS FOUND");
                    writer.println("=".repeat(50));
                    writer.println("This PDF has no annotations added yet.");
                }
            }
            
            writer.println("\n" + "=".repeat(50));
            writer.println("INSTRUCTIONS");
            writer.println("=".repeat(50));
            writer.println("1. To view these annotations visually:");
            writer.println("   - Open the PDF file in Shemmatics Control Application");
            writer.println("   - The annotations will be displayed as overlays");
            writer.println("");
            writer.println("2. To edit annotations:");
            writer.println("   - Use the application to add, modify, or delete annotations");
            writer.println("   - Save your changes before exporting again");
            writer.println("");
            writer.println("3. This companion file contains the annotation data");
            writer.println("   - Keep it with the PDF file for reference");
            writer.println("   - The annotations are not embedded in the PDF itself");
            writer.println("=".repeat(50));
        }
        
        System.out.println("PDF exported with companion annotation file");
    }
    
    private void setApplicationIcon() {
        try {
            // Try to load logo from resources
            String[] logoPaths = {
                "/logo.png",
                "/logo.jpg", 
                "/logo.jpeg",
                "/logo.gif",
                "/logo.bmp",
                "/resources/logo.png",
                "/resources/logo.jpg",
                "/resources/logo.jpeg", 
                "/resources/logo.gif",
                "/resources/logo.bmp",
                "/images/logo.png",
                "/images/logo.jpg",
                "/images/logo.jpeg",
                "/images/logo.gif",
                "/images/logo.bmp"
            };
            
            for (String logoPath : logoPaths) {
                java.io.InputStream logoStream = getClass().getResourceAsStream(logoPath);
                if (logoStream != null) {
                    byte[] logoBytes = logoStream.readAllBytes();
                    logoStream.close();
                    
                    // Create ImageIcon and set as window icon
                    ImageIcon logoIcon = new ImageIcon(logoBytes);
                    Image logoImage = logoIcon.getImage();
                    
                    // Provide multiple sizes so the OS can pick the best one
                    Image icon16 = logoImage.getScaledInstance(16, 16, Image.SCALE_SMOOTH);
                    Image icon32 = logoImage.getScaledInstance(32, 32, Image.SCALE_SMOOTH);
                    Image icon48 = logoImage.getScaledInstance(48, 48, Image.SCALE_SMOOTH);
                    mainFrame.setIconImages(Arrays.asList(icon16, icon32, icon48));
                    
                    System.out.println("Application icon loaded from: " + logoPath);
                    return;
                }
            }
            
            // If no logo found, create a default icon
            createDefaultIcon();
            
        } catch (Exception e) {
            System.out.println("Error loading application icon: " + e.getMessage());
            createDefaultIcon();
        }
    }
    
    private void createDefaultIcon() {
        try {
            // Create a simple default icon with stronger contrast
            int size = 48;
            java.awt.image.BufferedImage iconImage = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = iconImage.createGraphics();
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Draw a simple "Z" icon with ZESA‑style blue and a bold letter
            g2d.setColor(new Color(0, 70, 160));
            g2d.fillRoundRect(0, 0, size, size, 10, 10);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 28));
            g2d.drawString("Z", 14, 32);
            
            g2d.dispose();
            // Provide multiple sizes for better visibility in different title bars
            Image icon16 = iconImage.getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            Image icon32 = iconImage.getScaledInstance(32, 32, Image.SCALE_SMOOTH);
            mainFrame.setIconImages(Arrays.asList(icon16, icon32, iconImage));
            System.out.println("Default application icon created");
        } catch (Exception e) {
            System.out.println("Error creating default icon: " + e.getMessage());
        }
    }
}