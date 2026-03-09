# Schemmatics Digital Chart - Software Documentation

## Document Information

**Application Name:** Schemmatics Digital Chart (ZetdcDiagrammatics)  
**Version:** 1.0.0  
**Document Version:** 1.0  
**Date:** 2024  
**Technology Stack:** Java 11+, Java Swing, Apache PDFBox 2.0.29  
**Developer:** Nigel Onai Rodrick Sibanda  
**Organization:** ZETDC Southern Region

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [System Overview](#system-overview)
3. [System Requirements](#system-requirements)
4. [Features and Capabilities](#features-and-capabilities)
5. [Architecture](#architecture)
6. [Installation and Setup](#installation-and-setup)
7. [User Guide](#user-guide)
8. [Technical Documentation](#technical-documentation)
9. [File Structure](#file-structure)
10. [Data Persistence](#data-persistence)
11. [Troubleshooting](#troubleshooting)
12. [Future Enhancements](#future-enhancements)

---

## Executive Summary

Schemmatics Digital Chart is a modern desktop application designed for viewing, annotating, and managing PDF documents. Built with Java Swing, the application provides an intuitive interface for technical professionals to add markers, lines, and text annotations to PDF files, particularly useful for technical drawings, CAD diagrams, and engineering documents.

The application features real-time PDF rendering, multi-page navigation, zoom and pan capabilities, and persistent annotation storage. Annotations are automatically saved to project folders on the user's Desktop, ensuring data integrity and easy project management.

---

## System Overview

### Purpose

Schemmatics Digital Chart serves as a specialized PDF annotation tool for technical documentation, allowing users to:
- View PDF documents with high-quality rendering
- Add visual annotations (markers, lines, text) to PDF pages
- Navigate through multi-page documents efficiently
- Export annotated PDFs with embedded annotations
- Manage multiple PDF projects simultaneously

### Target Users

- Engineers and technical professionals
- CAD operators and drafters
- Project managers reviewing technical documents
- Quality assurance personnel
- Anyone requiring precise PDF annotation capabilities

### Key Benefits

- **Fast Performance:** Optimized rendering with caching for smooth navigation
- **Persistent Storage:** Automatic saving of annotations to project folders
- **Multi-Project Support:** Manage multiple PDF projects simultaneously
- **Touch Screen Support:** Enhanced support for touch-enabled devices
- **Export Capabilities:** Export PDFs with visual annotations embedded

---

## System Requirements

### Minimum Requirements

- **Operating System:** Windows 10/11, macOS 10.14+, or Linux (Ubuntu 18.04+)
- **Java Runtime:** Java 11 or higher (JRE 11+)
- **Memory:** 2 GB RAM minimum (4 GB recommended)
- **Storage:** 100 MB free disk space
- **Display:** 1024x768 resolution minimum

### Recommended Requirements

- **Operating System:** Windows 11, macOS 12+, or Linux (Ubuntu 20.04+)
- **Java Runtime:** Java 17 or higher
- **Memory:** 8 GB RAM
- **Storage:** 500 MB free disk space
- **Display:** 1920x1080 resolution or higher
- **Graphics:** Hardware acceleration supported

### Dependencies

- **Apache PDFBox 2.0.29:** PDF rendering and manipulation
- **Java Swing:** User interface framework (included with Java)
- **Commons Logging 1.2:** Logging framework

---

## Features and Capabilities

### Core Features

#### 1. PDF Viewing
- **Visual PDF Rendering:** High-quality page rendering using Apache PDFBox
- **Text Content Extraction:** Extract and display text content from PDFs
- **Multi-Page Navigation:** Navigate through documents with page controls
- **Document Information:** Display PDF metadata (title, author, creation date, etc.)

#### 2. Annotation Tools

**Markers:**
- Add red circular markers at specific coordinates
- Attach text labels to markers
- Customizable marker colors
- Hover tooltips for marker information

**Lines:**
- Draw straight lines between two points
- Draw polylines with multiple segments
- Draw circular arcs
- Add text labels to lines
- Customizable line types and styles

**Text Annotations:**
- Add text boxes with customizable backgrounds
- Position text at specific coordinates
- Color-coded text annotations
- Editable text content

#### 3. Navigation and Viewing

- **Zoom Controls:**
  - Zoom in/out with mouse wheel
  - Zoom slider for precise control
  - Fit to width
  - Fit to full page
  - Custom zoom levels

- **Pan and Scroll:**
  - Mouse drag to pan
  - Scroll bars for navigation
  - Touch screen pan support

- **Page Navigation:**
  - Previous/Next page buttons
  - Page number display
  - Jump to specific page (via page number)

#### 4. File Management

- **Open PDF Files:** Browse and open PDF files from file system
- **File List:** Maintain a list of recently opened files
- **Project Folders:** Automatic creation of project folders on Desktop
- **Save Annotations:** Persistent storage of all annotations
- **Export PDF:** Export PDFs with embedded annotations

#### 5. User Interface

- **Tabbed Interface:** Separate tabs for Visual View and Text View
- **Context Menus:** Right-click context menus for file operations
- **Menu Bar:** File, Edit, View, and Help menus
- **Status Bar:** Application status information
- **Splash Screen:** Application startup splash screen

### Advanced Features

#### Touch Screen Support
- Long press detection for context menus
- Pinch-to-zoom gesture support
- Touch-based panning
- Edit mode activation via touch

#### Performance Optimizations
- Image caching for faster page rendering
- Debounced mouse move events
- Optimized repaint cycles (~60 FPS)
- Lazy loading of PDF content

#### Data Persistence
- Automatic saving of annotations
- Project-based storage structure
- State restoration on application restart
- Backup state file in user home directory

---

## Architecture

### System Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    User Interface Layer                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ MainController│  │PdfViewerComponent│ │SplashScreen│  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
                        │
┌─────────────────────────────────────────────────────────┐
│                    Business Logic Layer                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  PdfReader   │  │  PdfRenderer │  │ FileManager  │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
                        │
┌─────────────────────────────────────────────────────────┐
│                      Data Layer                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  PDF Files   │  │ Annotations  │  │  State Files  │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### Component Architecture

#### Main Components

1. **Main.java**
   - Application entry point
   - Initializes splash screen
   - Sets up shutdown hooks
   - Launches main application window

2. **MainController.java**
   - Main application controller
   - Manages UI components
   - Handles file operations
   - Coordinates between components
   - Manages application state

3. **PdfViewerComponent.java**
   - PDF rendering and display
   - Annotation tools and interactions
   - Mouse and touch event handling
   - Zoom and pan functionality
   - Annotation management

4. **PdfRenderer.java**
   - PDF page rendering to images
   - PDF document caching
   - Resource management

5. **PdfReader.java**
   - PDF text extraction
   - Document information retrieval
   - PDF metadata parsing

6. **FileManager.java**
   - Project folder management
   - Annotation persistence
   - File I/O operations
   - JSON serialization/deserialization

7. **SplashScreen.java**
   - Application startup screen
   - Logo display
   - Loading indication

### Design Patterns

- **MVC (Model-View-Controller):** Separation of concerns between UI, logic, and data
- **Observer Pattern:** Event-driven UI updates
- **Singleton Pattern:** PDF renderer caching
- **Factory Pattern:** Component creation

---

## Installation and Setup

### Installation Methods

#### Method 1: Using Pre-built JAR

1. Download `zetdcschemmatics.jar` from the distribution
2. Ensure Java 11+ is installed on your system
3. Double-click the JAR file or run:
   ```bash
   java -jar zetdcschemmatics.jar
   ```

#### Method 2: Building from Source

**Prerequisites:**
- Java Development Kit (JDK) 11 or higher
- Gradle 7.0 or higher (or use Gradle Wrapper)

**Build Steps:**

1. **Clone or extract the project:**
   ```bash
   cd ZetdcDiagrammatics
   ```

2. **Build the project:**
   ```bash
   ./gradlew build
   ```
   (On Windows: `gradlew.bat build`)

3. **Create executable JAR:**
   ```bash
   ./gradlew fatJar
   ```

4. **Run the application:**
   ```bash
   ./gradlew run
   ```
   Or execute the JAR:
   ```bash
   java -jar build/libs/zetdcschemmatics.jar
   ```

#### Method 3: Using Eclipse IDE

1. Import the project into Eclipse
2. Ensure Java 11+ is configured as the project JDK
3. Right-click `Main.java` → Run As → Java Application

### Configuration

#### Application Settings

The application stores configuration in:
- **User Home Directory:** `~/.zetdcdiagrammatics-state.json`
- **Desktop Projects:** `~/Desktop/ZetdcDiagrammatics/`

No additional configuration files are required. The application creates necessary directories automatically.

#### System Properties

Optional JVM arguments for performance tuning:
```bash
java -Xmx2g -Xms512m -jar zetdcschemmatics.jar
```

---

## User Guide

### Getting Started

#### Launching the Application

1. Start the application using one of the installation methods above
2. The splash screen will appear briefly
3. The main window opens with an empty file list

#### Opening a PDF File

**Method 1: Using File Menu**
1. Click **File** → **Open...**
2. Navigate to your PDF file
3. Select the file and click **Open**

**Method 2: Using Browse Button**
1. Click **Browse PDF Files** button in the left panel
2. Select your PDF file
3. The file will be added to the list and opened

**Method 3: Drag and Drop** (if supported)
- Drag a PDF file into the application window

### Using Annotation Tools

#### Adding Markers

1. Click the **Marker** toggle button in the PDF viewer toolbar
2. Click anywhere on the PDF page to place a marker
3. Enter text for the marker (optional)
4. Click **Done Moving** to finish marker placement

**Editing Markers:**
- Right-click a marker to access context menu
- Options: Edit, Delete, Change Color

#### Drawing Lines

1. Click the **Line** toggle button
2. Click and drag on the PDF to draw a line
3. Release to complete the line
4. Enter text label (optional)
5. Select line type: Straight, Polyline, or Circle

**Line Types:**
- **Straight:** Single line between two points
- **Polyline:** Multiple connected line segments
- **Circle:** Circular arc (requires radius specification)

#### Adding Text Annotations

1. Click the **Text** toggle button
2. Click on the PDF where you want to place text
3. Type your text in the input dialog
4. Text appears with a colored background

**Editing Text:**
- Right-click text annotation
- Select Edit or Delete from context menu

### Navigation

#### Zooming

- **Mouse Wheel:** Scroll up to zoom in, down to zoom out
- **Zoom Slider:** Drag the slider for precise zoom control
- **Zoom Buttons:** Use + and - buttons for step zoom
- **Fit to Width:** Click to fit page width to window
- **Fit to Full:** Click to fit entire page to window

#### Panning

- **Mouse Drag:** Click and drag to pan around the page
- **Scroll Bars:** Use window scroll bars
- **Touch Screen:** Drag with finger on touch-enabled devices

#### Page Navigation

- **Previous Page:** Click ← button or use keyboard shortcut
- **Next Page:** Click → button or use keyboard shortcut
- **Page Display:** Shows current page number and total pages

### File Management

#### Saving Annotations

Annotations are automatically saved, but you can manually save:

1. **File** → **Save Changes Permanently**
2. Or right-click file in list → **Save Changes**

Annotations are saved to:
- `~/Desktop/ZetdcDiagrammatics/[PDF Name]/annotations.json`

#### Exporting PDF with Annotations

1. **File** → **Export PDF with Changes**
2. Choose save location
3. The exported PDF will have annotations visually embedded

**Note:** If visual embedding fails, a companion text file is created with annotation details.

#### Managing File List

- **Add File:** Use Browse button or File → Open
- **Remove File:** Right-click file → Remove from List
- **Switch Files:** Click file name in the list

### Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+O` | Open file |
| `Ctrl+S` | Save changes |
| `Ctrl+N` | New file (clear current) |
| `F11` | Toggle full screen |
| `Delete` | Delete selected annotation |
| `Esc` | Cancel current tool operation |

### Touch Screen Usage

#### Gestures

- **Tap:** Select annotation or place marker
- **Long Press (500ms):** Open context menu
- **Long Press (1000ms):** Activate edit mode
- **Pinch:** Zoom in/out
- **Drag:** Pan the page

---

## Technical Documentation

### Class Documentation

#### Main.java

**Purpose:** Application entry point and initialization

**Key Methods:**
- `main(String[] args)`: Application entry point
- `showSplashScreen()`: Displays startup splash screen
- `loadLogoImage()`: Loads application logo

**Responsibilities:**
- Initialize application
- Display splash screen
- Set up shutdown hooks
- Launch main window

#### MainController.java

**Purpose:** Main application controller and coordinator

**Key Methods:**
- `createAndShowGUI()`: Initializes and displays main window
- `loadPdfFromFile(File pdfFile)`: Loads and displays PDF
- `saveAppState()`: Saves application state
- `loadAppState()`: Restores application state
- `exportPdfWithChanges()`: Exports PDF with annotations

**State Management:**
- Tracks open files
- Manages annotation data per file
- Handles unsaved changes
- Persists state to disk

#### PdfViewerComponent.java

**Purpose:** PDF rendering and annotation component

**Key Methods:**
- `loadPdf(File pdfFile)`: Loads PDF file
- `renderPage(int pageIndex)`: Renders specific page
- `addMarker(Point2D position, String text)`: Adds marker annotation
- `addLine(Point2D start, Point2D end, String text)`: Adds line annotation
- `addText(Point2D position, String text)`: Adds text annotation
- `exportMarkers()`: Exports marker data
- `exportLines()`: Exports line data
- `exportTexts()`: Exports text data

**Annotation Data Structures:**

**MarkerDTO:**
```java
class MarkerDTO {
    float x, y;           // Position coordinates
    String text;          // Marker label
    int colorRgb;         // Color value
}
```

**LineDTO:**
```java
class LineDTO {
    float startX, startY; // Start coordinates
    float endX, endY;     // End coordinates
    String text;          // Line label
    int lineType;         // 0=Polyline, 1=Straight, 2=Circle
    float radius;         // Radius for circle type
}
```

**TextDTO:**
```java
class TextDTO {
    float x, y;           // Position coordinates
    String text;          // Text content
    int backgroundColorRgb; // Background color
}
```

#### PdfRenderer.java

**Purpose:** PDF page rendering engine

**Key Methods:**
- `renderPage(File pdfFile, int pageIndex, float scale)`: Renders page to image
- `clearCache()`: Clears PDF document cache
- `getPageCount(File pdfFile)`: Gets total page count

**Caching Strategy:**
- Caches open PDF documents
- Renders pages on demand
- Clears cache on application exit

#### PdfReader.java

**Purpose:** PDF text extraction and metadata

**Key Methods:**
- `extractText(File pdfFile)`: Extracts all text from PDF
- `getDocumentInfo(File pdfFile)`: Gets PDF metadata

#### FileManager.java

**Purpose:** File and project management

**Key Methods:**
- `getProjectFolderForPdf(File pdfFile)`: Gets project folder path
- `saveAnnotations(...)`: Saves annotations to JSON file
- `loadAnnotations(File pdfFile)`: Loads annotations from JSON file
- `ensureProjectFolder(File pdfFile)`: Creates project folder if needed

**Project Structure:**
```
Desktop/
└── ZetdcDiagrammatics/
    ├── Project1/
    │   └── annotations.json
    ├── Project2/
    │   └── annotations.json
    └── ...
```

### Data Flow

#### Opening a PDF

1. User selects file via File → Open
2. `MainController.openFile()` called
3. `MainController.loadPdfFromFile()` invoked
4. `FileManager.ensureProjectFolder()` creates project folder
5. `PdfViewerComponent.loadPdf()` loads PDF
6. `PdfRenderer.renderPage()` renders first page
7. `FileManager.loadAnnotations()` loads saved annotations
8. `PdfViewerComponent.importMarkers/Lines/Texts()` restores annotations
9. UI updates to display PDF and annotations

#### Adding an Annotation

1. User activates annotation tool (Marker/Line/Text)
2. User interacts with PDF (click/drag)
3. `PdfViewerComponent` captures coordinates
4. Annotation DTO created
5. Annotation added to in-memory data structure
6. `MainController.markAsUnsaved()` called
7. Annotation immediately saved to project folder
8. UI repaints to show new annotation

#### Saving State

1. User triggers save (manual or automatic)
2. `MainController.saveAppStatePermanently()` called
3. All annotation data exported from `PdfViewerComponent`
4. Data serialized to JSON format
5. Written to `annotations.json` in project folder
6. Backup state saved to `~/.zetdcdiagrammatics-state.json`

### Coordinate System

The application uses PDF coordinate system:
- **Origin:** Bottom-left corner (PDF standard)
- **Units:** Points (1/72 inch)
- **Scale:** Annotations stored at scale=1.0 (original PDF size)
- **Transformation:** Coordinates transformed for display based on current zoom/pan

### Performance Considerations

#### Rendering Optimization

- **Image Caching:** Rendered pages cached to avoid re-rendering
- **Lazy Loading:** Pages rendered on demand
- **Debouncing:** Mouse move events debounced to reduce repaints
- **Repaint Throttling:** Repaints limited to ~60 FPS

#### Memory Management

- **PDF Document Caching:** Open documents cached, closed on exit
- **Image Disposal:** Rendered images disposed when not needed
- **Garbage Collection:** Proper cleanup of resources

---

## File Structure

### Project Directory Structure

```
ZetdcDiagrammatics/
├── src/
│   ├── com/
│   │   └── zetdc/
│   │       └── diagrammatics/
│   │           ├── Main.java
│   │           ├── components/
│   │           │   ├── PdfViewerComponent.java
│   │           │   └── SplashScreen.java
│   │           ├── controllers/
│   │           │   └── MainController.java
│   │           ├── models/
│   │           │   ├── DrawableShape.java
│   │           │   ├── DrawingCanvas.java
│   │           │   └── ShapeType.java
│   │           ├── utils/
│   │           │   ├── FileManager.java
│   │           │   ├── PdfReader.java
│   │           │   └── PdfRenderer.java
│   │           ├── fxml/
│   │           │   └── MainView.fxml
│   │           └── css/
│   │               └── styles.css
│   └── resources/
│       └── logo.jpeg
├── lib/
│   ├── commons-logging-1.2.jar
│   ├── fontbox-2.0.29.jar
│   └── pdfbox-2.0.29.jar
├── build.gradle
├── MANIFEST.MF
├── README.md
├── SOFTWARE_DOCUMENTATION.md
└── zetdcschemmatics.jar
```

### Runtime File Structure

```
User Home/
├── .zetdcdiagrammatics-state.json  (Application state backup)
└── Desktop/
    └── ZetdcDiagrammatics/        (Project root)
        ├── Project1/              (Per-PDF project folder)
        │   └── annotations.json   (Annotation data)
        ├── Project2/
        │   └── annotations.json
        └── ...
```

### Annotation JSON Format

```json
{
  "sourcePdf": "/absolute/path/to/file.pdf",
  "markers": {
    "0": [
      {
        "x": 100.5,
        "y": 200.3,
        "text": "Marker label",
        "colorRgb": -65536
      }
    ]
  },
  "lines": {
    "0": [
      {
        "startX": 50.0,
        "startY": 100.0,
        "endX": 150.0,
        "endY": 200.0,
        "text": "Line label",
        "lineType": 1,
        "radius": 0.0
      }
    ]
  },
  "texts": {
    "0": [
      {
        "x": 75.0,
        "y": 150.0,
        "text": "Text annotation",
        "backgroundColorRgb": -256
      }
    ]
  }
}
```

---

## Data Persistence

### Storage Locations

1. **Project Folders:** `~/Desktop/ZetdcDiagrammatics/[PDF Name]/annotations.json`
   - Primary storage location
   - Per-PDF project structure
   - Contains all annotations for a specific PDF

2. **Application State:** `~/.zetdcdiagrammatics-state.json`
   - Backup state file
   - Contains list of open files
   - Contains annotations for all files
   - Used for application restart recovery

### Persistence Strategy

#### Automatic Saving
- Annotations saved immediately when added/modified
- No manual save required for data safety
- Changes persist across application restarts

#### Save Triggers
1. **Immediate:** When annotation is added/modified
2. **File Switch:** Before switching to another PDF
3. **Application Exit:** Before closing application
4. **Manual:** User-initiated save via menu

#### Data Format
- **Format:** JSON (JavaScript Object Notation)
- **Encoding:** UTF-8
- **Structure:** Hierarchical (File → Page → Annotations)

### Backup and Recovery

#### Automatic Backup
- Application state backed up to user home directory
- Project folders serve as primary backup
- Both locations updated on save

#### Recovery Process
1. On application start, scan Desktop project folders
2. Load annotations from project folders
3. Fall back to application state file if needed
4. Restore file list and open last used file

---

## Troubleshooting

### Common Issues

#### Issue: PDF Won't Open

**Symptoms:**
- Error message when opening PDF
- PDF viewer shows error text

**Solutions:**
1. Verify PDF file is not corrupted
2. Check file permissions (read access required)
3. Ensure PDF is not password-protected
4. Try opening PDF in another viewer to verify validity
5. Check available memory (large PDFs require more RAM)

#### Issue: Annotations Not Saving

**Symptoms:**
- Annotations disappear after closing application
- Save operation fails

**Solutions:**
1. Check Desktop folder permissions
2. Verify disk space available
3. Check for file system errors
4. Review application logs for errors
5. Manually save using File → Save Changes Permanently

#### Issue: Slow Performance

**Symptoms:**
- Slow page rendering
- Laggy UI interactions
- High memory usage

**Solutions:**
1. Close other applications to free memory
2. Reduce zoom level for faster rendering
3. Close unused PDF files
4. Restart application to clear cache
5. Increase JVM memory: `java -Xmx2g -jar zetdcschemmatics.jar`

#### Issue: Touch Screen Not Working

**Symptoms:**
- Touch gestures not recognized
- Context menu doesn't appear

**Solutions:**
1. Verify touch screen drivers are installed
2. Check system touch screen settings
3. Try longer press duration (1000ms for edit mode)
4. Ensure application window has focus

#### Issue: Export PDF Fails

**Symptoms:**
- Export operation shows error
- Exported PDF missing annotations

**Solutions:**
1. Check write permissions for export location
2. Verify sufficient disk space
3. Check if source PDF is corrupted
4. Review error message for specific issue
5. Companion file method used as fallback

### Error Messages

#### "Failed to load PDF file"
- **Cause:** PDF file is corrupted, password-protected, or inaccessible
- **Solution:** Verify file integrity and permissions

#### "Failed to save annotations"
- **Cause:** Disk full, permission denied, or file system error
- **Solution:** Check disk space and folder permissions

#### "PDFBox library not available"
- **Cause:** Missing PDFBox JAR files
- **Solution:** Ensure all JAR files are in classpath

### Logging

Application logs are written to console. For production troubleshooting:
```bash
java -jar zetdcschemmatics.jar > app.log 2>&1
```

---

## Future Enhancements

### Planned Features

1. **Multi-User Support**
   - User authentication
   - Per-user annotation storage
   - Collaboration features

2. **Advanced Annotation Tools**
   - Rectangle and ellipse shapes
   - Freehand drawing
   - Highlighting tool
   - Sticky notes

3. **Export Enhancements**
   - Export to image formats (PNG, JPEG)
   - Export to other PDF annotation formats
   - Batch export capabilities

4. **Performance Improvements**
   - Multi-threaded rendering
   - Progressive page loading
   - Improved caching strategies

5. **UI/UX Enhancements**
   - Dark mode theme
   - Customizable toolbars
   - Keyboard shortcuts customization
   - Undo/redo for annotations

6. **Integration Features**
   - Cloud storage integration
   - Version control for annotations
   - PDF comparison tools
   - Print support

### Technical Improvements

1. **Code Refactoring**
   - Extract annotation logic to separate service
   - Implement proper dependency injection
   - Improve error handling

2. **Testing**
   - Unit tests for core components
   - Integration tests for file operations
   - UI automation tests

3. **Documentation**
   - API documentation (JavaDoc)
   - Video tutorials
   - User manual with screenshots

---

## Appendix

### A. Glossary

- **Annotation:** Visual mark added to PDF (marker, line, or text)
- **Marker:** Red circular indicator with optional text label
- **Polyline:** Line composed of multiple connected segments
- **Project Folder:** Directory created for each PDF containing annotations
- **State File:** Backup file containing application state and annotations

### B. Version History

**Version 1.0.0** (Current)
- Initial release
- PDF viewing and rendering
- Marker, line, and text annotations
- Project folder management
- Export functionality
- Touch screen support

### C. References

- **Apache PDFBox Documentation:** https://pdfbox.apache.org/
- **Java Swing Tutorial:** https://docs.oracle.com/javase/tutorial/uiswing/
- **Java Documentation:** https://docs.oracle.com/javase/11/

### D. Support and Contact

For technical support, bug reports, or feature requests:
- Check application logs for error details
- Review this documentation for troubleshooting
- Contact development team with specific issue details

### E. Development Credits

**Developer:** Nigel Onai Rodrick Sibanda  
**Organization:** ZETDC Southern Region  
**Development Location:** ZETDC Southern Region

This application was developed by Nigel Onai Rodrick Sibanda at ZETDC Southern Region as a specialized tool for PDF annotation and technical document management.

### F. License

This software is proprietary. All rights reserved.

---

**End of Document**

