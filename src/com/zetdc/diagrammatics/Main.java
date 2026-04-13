package com.zetdc.diagrammatics;

import com.zetdc.diagrammatics.controllers.MainController;
import com.zetdc.diagrammatics.components.SplashScreen;
import com.zetdc.diagrammatics.utils.PdfRenderer;
import com.zetdc.diagrammatics.utils.TvMode;
import com.zetdc.diagrammatics.utils.UserService;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Paths;
import java.io.InputStream;

/**
 * Main application class for Schemmatics Digital Chart (ZetdcDiagrammatics)
 * A modern desktop PDF viewing and annotation application built with Swing
 * 
 * Developer: Nigel Onai Rodrick Sibanda
 * Organization: ZETDC Southern Region
 */
public class Main {
    
    public static void main(String[] args) {
        // Automatic setup on first run / installation: create users file and default admin
        ensureInstallationSetup();
        
        // Register shutdown hook to cleanup PDF documents on forced exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                PdfRenderer.clearCache();
                System.out.println("Shutdown hook: All PDF documents closed");
            } catch (Exception e) {
                System.err.println("Shutdown hook: Error closing PDF documents: " + e.getMessage());
            }
        }));
        
        // Show splash screen immediately for fast startup
        SplashScreen splash = showSplashScreen();
        
        // Create and show the main window on EDT in background
        SwingUtilities.invokeLater(() -> {
            try {
                System.out.println("Starting ZetdcDiagrammatics application...");
                
                // Apply touch-friendly UI for TV and laptop touch screens
                com.zetdc.diagrammatics.utils.TouchSupport.applyTouchFriendlyUI();
                
                // No startup login - app opens for viewing. Login required only when Edit is clicked.
                
                // Set system look and feel
                try {
                    // UIManager.setLookAndFeel(UIManager.getSystemLookAndFeel());
                } catch (Exception e) {
                    System.out.println("Could not set system look and feel: " + e.getMessage());
                }
                
                System.out.println("Creating MainController...");
                MainController controller = new MainController();
                System.out.println("Creating GUI...");
                controller.createAndShowGUI();
                System.out.println("GUI created successfully!");
                
                // Hide splash screen after main window is ready
                if (splash != null) {
                    splash.hideSplash();
                }
                
            } catch (Exception e) {
                System.err.println("Error starting application: " + e.getMessage());
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, 
                    "Failed to start application: " + e.getMessage(), 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
                if (splash != null) {
                    splash.hideSplash();
                }
            }
        });
    }
    
    private static SplashScreen showSplashScreen() {
        try {
            // Create splash screen
            SplashScreen splash = new SplashScreen();
            
            // Try to load logo from resources
            ImageIcon logoIcon = loadLogoImage();
            if (logoIcon != null) {
                splash.setLogoImage(logoIcon);
            } else {
                // Create a default logo if image not found
                splash.setLogoImage(createDefaultLogo());
            }
            
            // Show splash screen immediately (no timer)
            splash.showSplash(0);
            return splash;
            
        } catch (Exception e) {
            // If splash screen fails, continue without it
            System.err.println("Splash screen failed: " + e.getMessage());
            return null;
        }
    }
    
    private static ImageIcon loadLogoImage() {
        try {
            // Try different logo file names and extensions
            String[] logoPaths = {
                "/logo.png",
                "/logo.jpg", 
                "/logo.jpeg",
                "/logo.jfif",
                "/logo.gif",
                "/logo.bmp",
                "/resources/logo.png",
                "/resources/logo.jpg",
                "/resources/logo.jpeg", 
                "/resources/logo.jfif",
                "/resources/logo.gif",
                "/resources/logo.bmp",
                "/images/logo.png",
                "/images/logo.jpg",
                "/images/logo.jpeg",
                "/images/logo.jfif",
                "/images/logo.gif",
                "/images/logo.bmp"
            };
            
            for (String logoPath : logoPaths) {
                InputStream logoStream = Main.class.getResourceAsStream(logoPath);
                if (logoStream != null) {
                    byte[] logoBytes = logoStream.readAllBytes();
                    logoStream.close();
                    System.out.println("Logo loaded from: " + logoPath);
                    return new ImageIcon(logoBytes);
                }
            }

            // Fallback: load from local files (project folder or app working directory)
            String[] fileCandidates = {
                "logo.jfif",
                "logo.jpeg",
                "logo.jpg",
                "logo.png",
                Paths.get("src", "resources", "logo.jfif").toString(),
                Paths.get("src", "resources", "logo.jpeg").toString(),
                Paths.get("src", "resources", "logo.jpg").toString(),
                Paths.get("src", "resources", "logo.png").toString()
            };
            for (String candidate : fileCandidates) {
                File file = new File(candidate);
                if (file.exists() && file.isFile()) {
                    System.out.println("Logo loaded from file: " + file.getAbsolutePath());
                    return new ImageIcon(file.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            System.out.println("Error loading logo: " + e.getMessage());
        }
        return null;
    }
    
    private static ImageIcon createDefaultLogo() {
        // Create a simple text-based logo
        JLabel logoLabel = new JLabel("ZetdcDiagrammatics");
        logoLabel.setFont(TvMode.getTitleFont());
        logoLabel.setForeground(new Color(0, 120, 215));
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        // Create an image from the label
        logoLabel.setSize(200, 50);
        BufferedImage logoImage = new BufferedImage(200, 50, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = logoImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        logoLabel.paint(g2d);
        g2d.dispose();
        
        return new ImageIcon(logoImage);
    }
    
    /**
     * Runs automatically on application start. Ensures users file and default admin exist.
     * No user action required - happens on first run / installation.
     */
    private static void ensureInstallationSetup() {
        try {
            new UserService();
        } catch (Exception e) {
            System.err.println("Installation setup: " + e.getMessage());
        }
    }
    
}