package com.zetdc.diagrammatics.components;

import com.zetdc.diagrammatics.utils.TvMode;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Splash screen component with logo and loading animation
 */
public class SplashScreen extends JWindow {
    
    private JLabel logoLabel;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private Timer animationTimer;
    private int animationStep = 0;
    
    public SplashScreen() {
        initializeComponents();
        setupLayout();
        startAnimation();
    }
    
    private void initializeComponents() {
        // Create logo label (will be set with image)
        logoLabel = new JLabel();
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        logoLabel.setVerticalAlignment(SwingConstants.CENTER);
        
        // Create status label
        statusLabel = new JLabel("Loading ZetdcDiagrammatics...");
        statusLabel.setFont(TvMode.getTitleFont());
        statusLabel.setForeground(new Color(70, 70, 70));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        // Create progress bar
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(false);
        progressBar.setPreferredSize(new Dimension(TvMode.SPLASH_WIDTH - 80, TvMode.ENABLED ? 28 : 20));
        progressBar.setBorderPainted(false);
        progressBar.setBackground(new Color(240, 240, 240));
        progressBar.setForeground(new Color(0, 120, 215));
    }
    
    private void setupLayout() {
        setLayout(new BorderLayout());
        
        // Main panel with gradient background
        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                
                // Create gradient background
                GradientPaint gradient = new GradientPaint(
                    0, 0, new Color(245, 245, 245),
                    0, getHeight(), new Color(220, 220, 220)
                );
                g2d.setPaint(gradient);
                g2d.fillRect(0, 0, getWidth(), getHeight());
                
                // Add subtle border
                g2d.setColor(new Color(200, 200, 200));
                g2d.setStroke(new BasicStroke(1));
                g2d.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                
                g2d.dispose();
            }
        };
        mainPanel.setLayout(new BorderLayout());
        int pad = TvMode.ENABLED ? 60 : 40;
        mainPanel.setBorder(BorderFactory.createEmptyBorder(pad, pad, pad, pad));
        
        // Logo panel
        JPanel logoPanel = new JPanel(new BorderLayout());
        logoPanel.setOpaque(false);
        logoPanel.add(logoLabel, BorderLayout.CENTER);
        
        // Status panel
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setOpaque(false);
        statusPanel.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        statusPanel.add(progressBar, BorderLayout.SOUTH);
        
        // Add components to main panel
        mainPanel.add(logoPanel, BorderLayout.CENTER);
        mainPanel.add(statusPanel, BorderLayout.SOUTH);
        
        add(mainPanel, BorderLayout.CENTER);
    }
    
    private void startAnimation() {
        // Create animated loading text
        animationTimer = new Timer(500, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String[] loadingTexts = {
                    "Loading ZetdcDiagrammatics...",
                    "Initializing PDF engine...",
                    "Setting up annotation tools...",
                    "Preparing user interface...",
                    "Almost ready..."
                };
                
                statusLabel.setText(loadingTexts[animationStep % loadingTexts.length]);
                animationStep++;
            }
        });
        animationTimer.start();
    }
    
    public void setLogoImage(ImageIcon logoIcon) {
        if (logoIcon != null) {
            // Scale the image to fit nicely in the splash screen (TV: larger)
            Image image = logoIcon.getImage();
            int maxWidth = TvMode.ENABLED ? 400 : 200;
            int maxHeight = TvMode.ENABLED ? 280 : 150;
            
            int newWidth = image.getWidth(null);
            int newHeight = image.getHeight(null);
            
            // Maintain aspect ratio
            if (newWidth > maxWidth) {
                newHeight = (newHeight * maxWidth) / newWidth;
                newWidth = maxWidth;
            }
            if (newHeight > maxHeight) {
                newWidth = (newWidth * maxHeight) / newHeight;
                newHeight = maxHeight;
            }
            
            Image scaledImage = image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
            logoLabel.setIcon(new ImageIcon(scaledImage));
        }
    }
    
    public void setStatusText(String text) {
        statusLabel.setText(text);
    }
    
    public void showSplash(int duration) {
        // Center the splash screen
        centerOnScreen();
        
        // Show the splash screen
        setVisible(true);
        
        // Auto-hide after duration
        Timer hideTimer = new Timer(duration, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                hideSplash();
            }
        });
        hideTimer.setRepeats(false);
        hideTimer.start();
    }
    
    public void hideSplash() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
        setVisible(false);
        dispose();
    }
    
    private void centerOnScreen() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension splashSize = getPreferredSize();
        
        int x = (screenSize.width - splashSize.width) / 2;
        int y = (screenSize.height - splashSize.height) / 2;
        
        setBounds(x, y, splashSize.width, splashSize.height);
    }
    
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(TvMode.SPLASH_WIDTH, TvMode.SPLASH_HEIGHT);
    }
}




