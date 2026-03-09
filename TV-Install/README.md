# ZetdcDiagrammatics

A modern desktop diagramming application built with JavaFX. Create, edit, and manage diagrams with an intuitive user interface.

## Features

- **Drawing Tools**: Rectangle, Circle, Line, and Text tools
- **Selection & Editing**: Select, move, and delete shapes
- **Undo/Redo**: Full undo/redo functionality
- **Customizable Properties**: Adjust fill color, stroke color, and stroke width
- **Modern UI**: Clean, responsive interface with professional styling
- **File Operations**: Save and load diagram files

## Requirements

- Java 11 or higher
- JavaFX 17.0.2 or higher

## Building and Running

### Using Gradle

1. **Build the application:**
   ```bash
   ./gradlew build
   ```

2. **Run the application:**
   ```bash
   ./gradlew run
   ```

3. **Create a distribution:**
   ```bash
   ./gradlew distZip
   ```

### Using Eclipse IDE

1. Import the project as a Gradle project
2. Ensure Java 11+ is configured
3. Run the `Main.java` class directly

## Project Structure

```
src/
├── com/zetdc/diagrammatics/
│   ├── Main.java                    # Application entry point
│   ├── controllers/
│   │   └── MainController.java      # Main UI controller
│   ├── models/
│   │   ├── DrawingCanvas.java       # Custom drawing canvas
│   │   ├── DrawableShape.java       # Shape representation
│   │   └── ShapeType.java           # Shape type enumeration
│   ├── fxml/
│   │   └── MainView.fxml            # Main UI layout
│   └── css/
│       └── styles.css               # Application styling
```

## Usage

1. **Select a Tool**: Choose from Select, Rectangle, Circle, Line, or Text tools
2. **Draw Shapes**: Click and drag to create shapes on the canvas
3. **Edit Properties**: Use the left panel to adjust colors and stroke width
4. **Select & Edit**: Use the Select tool to select and manipulate shapes
5. **Undo/Redo**: Use the toolbar buttons or keyboard shortcuts
6. **Save/Load**: Use the File menu to save and load your diagrams

## Keyboard Shortcuts

- `Ctrl+N`: New file
- `Ctrl+O`: Open file
- `Ctrl+S`: Save file
- `Ctrl+Shift+S`: Save as
- `Ctrl+Z`: Undo
- `Ctrl+Y`: Redo
- `Delete`: Delete selected shapes

## Development

This project uses:
- **Java 11+** for the core application
- **JavaFX 17** for the user interface
- **Gradle** for build management
- **FXML** for UI layout
- **CSS** for styling

## License

This project is licensed under the MIT License.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## Support

For issues and questions, please create an issue in the project repository.


