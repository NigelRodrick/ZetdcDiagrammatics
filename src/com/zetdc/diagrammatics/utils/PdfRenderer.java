package com.zetdc.diagrammatics.utils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * PDF renderer that converts PDF pages to images for display
 * Optimized with document caching for better performance
 */
public class PdfRenderer {
    
    // Cache for open PDF documents (key: canonical path for consistent lookup)
    private static final Map<String, Object> documentCache = new HashMap<>();
    private static final Map<String, Object> rendererCache = new HashMap<>();
    
    /**
     * Render a PDF page as an image
     */
    public static BufferedImage renderPage(File pdfFile, int pageNumber, float scale) throws IOException {
        if (pdfFile == null || !pdfFile.exists()) {
            throw new IOException("PDF file is null or does not exist");
        }
        if (pageNumber < 1) {
            throw new IOException("Invalid page number: " + pageNumber);
        }
        if (scale < 0.1f || scale > 10f) {
            scale = Math.max(0.1f, Math.min(10f, scale));
        }
        try {
            return renderPageWithPDFBox(pdfFile, pageNumber, scale);
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            return renderPageBasic(pdfFile, pageNumber, scale);
        }
    }
    
    /**
     * Render page using PDFBox with document caching for performance
     */
    private static BufferedImage renderPageWithPDFBox(File pdfFile, int pageNumber, float scale) throws IOException {
        try {
            Class<?> pdDocumentClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            Class<?> pdfRendererClass = Class.forName("org.apache.pdfbox.rendering.PDFRenderer");
            
            String cacheKey = getCacheKey(pdfFile);
            Object document = getCachedDocument(cacheKey, pdfFile, pdDocumentClass);
            Object renderer = getCachedRenderer(cacheKey, document, pdfRendererClass, pdDocumentClass);
            
            int pageIndex = pageNumber - 1;
            int pageCount = (Integer) pdDocumentClass.getMethod("getNumberOfPages").invoke(document);
            if (pageIndex < 0 || pageIndex >= pageCount) {
                throw new IOException("Page " + pageNumber + " is out of range (document has " + pageCount + " pages)");
            }
            
            // Render the page - use ImageType.RGB to fix red tint (CMYK/BGR color space issues)
            BufferedImage image;
            try {
                Class<?> imageTypeClass = Class.forName("org.apache.pdfbox.rendering.ImageType");
                Object rgbType = imageTypeClass.getMethod("valueOf", String.class).invoke(null, "RGB");
                image = (BufferedImage) pdfRendererClass.getMethod("renderImage", int.class, float.class, imageTypeClass).invoke(renderer, pageIndex, scale, rgbType);
            } catch (NoSuchMethodException e) {
                // Fallback for older PDFBox: 2-arg renderImage
                image = (BufferedImage) pdfRendererClass.getMethod("renderImage", int.class, float.class).invoke(renderer, pageIndex, scale);
            }
            
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new IOException("PDFBox returned invalid image for page " + pageNumber);
            }
            // Ensure standard RGB format to avoid red tint on some systems
            return ensureRgbImage(image);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("password")) {
                throw new IOException("PDF is password-protected. Please remove the password first.");
            }
            if (msg != null && (msg.contains("corrupt") || msg.contains("Invalid"))) {
                throw new IOException("PDF may be corrupted or use an unsupported format: " + msg);
            }
            throw new IOException("Failed to render PDF page: " + msg);
        }
    }
    
    /** Convert to standard RGB if needed to fix color display issues */
    private static BufferedImage ensureRgbImage(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB || src.getType() == BufferedImage.TYPE_INT_ARGB) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }
    
    private static String getCacheKey(File pdfFile) {
        try {
            return pdfFile.getCanonicalPath();
        } catch (IOException e) {
            return pdfFile.getAbsolutePath();
        }
    }
    
    /**
     * Get cached document or create new one
     */
    private static Object getCachedDocument(String cacheKey, File pdfFile, Class<?> pdDocumentClass) throws Exception {
        synchronized (documentCache) {
            Object document = documentCache.get(cacheKey);
            if (document == null) {
                document = pdDocumentClass.getMethod("load", File.class).invoke(null, pdfFile);
                documentCache.put(cacheKey, document);
            }
            return document;
        }
    }
    
    /**
     * Get cached renderer or create new one
     */
    private static Object getCachedRenderer(String cacheKey, Object document, Class<?> pdfRendererClass, Class<?> pdDocumentClass) throws Exception {
        synchronized (rendererCache) {
            Object renderer = rendererCache.get(cacheKey);
            if (renderer == null) {
                renderer = pdfRendererClass.getConstructor(pdDocumentClass).newInstance(document);
                rendererCache.put(cacheKey, renderer);
            }
            return renderer;
        }
    }
    
    /**
     * Close and remove cached document (call when done with PDF)
     */
    public static void closeDocument(File pdfFile) {
        if (pdfFile == null) return;
        String cacheKey = getCacheKey(pdfFile);
        synchronized (documentCache) {
            Object document = documentCache.remove(cacheKey);
            if (document != null) {
                try {
                    Class<?> pdDocumentClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
                    pdDocumentClass.getMethod("close").invoke(document);
                } catch (Exception e) {
                    System.err.println("Error closing PDF document: " + e.getMessage());
                }
            }
            rendererCache.remove(cacheKey);
        }
    }
    
    /**
     * Clear all cached documents (call on application shutdown)
     */
    public static void clearCache() {
        synchronized (documentCache) {
            for (String cacheKey : documentCache.keySet().toArray(new String[0])) {
                Object document = documentCache.remove(cacheKey);
                if (document != null) {
                    try {
                        Class<?> pdDocumentClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
                        pdDocumentClass.getMethod("close").invoke(document);
                    } catch (Exception e) {
                        System.err.println("Error closing PDF: " + e.getMessage());
                    }
                }
                rendererCache.remove(cacheKey);
            }
        }
    }
    
    /**
     * Basic page renderer (fallback)
     */
    private static BufferedImage renderPageBasic(File pdfFile, int pageNumber, float scale) throws IOException {
        // Create a placeholder image
        int width = (int)(800 * scale);
        int height = (int)(600 * scale);
        
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        
        // Set background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        
        // Draw placeholder content
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        
        String[] lines = {
            "PDF Page " + pageNumber,
            "File: " + pdfFile.getName(),
            "",
            "PDF Rendering requires PDFBox library",
            "To view actual PDF content:",
            "1. Add PDFBox JAR files to classpath",
            "2. Restart the application",
            "",
            "Current scale: " + scale + "x"
        };
        
        int y = 50;
        for (String line : lines) {
            g2d.drawString(line, 20, y);
            y += 25;
        }
        
        // Draw a border
        g2d.setStroke(new BasicStroke(2));
        g2d.setColor(Color.GRAY);
        g2d.drawRect(10, 10, width - 20, height - 20);
        
        g2d.dispose();
        return image;
    }
    
    /**
     * Get the number of pages in a PDF
     */
    public static int getPageCount(File pdfFile) throws IOException {
        try {
            return getPageCountWithPDFBox(pdfFile);
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            return getPageCountBasic(pdfFile);
        }
    }
    
    /**
     * Get page count using PDFBox with document caching
     */
    private static int getPageCountWithPDFBox(File pdfFile) throws IOException {
        try {
            if (pdfFile == null || !pdfFile.exists()) {
                throw new IOException("PDF file is null or does not exist");
            }
            Class<?> pdDocumentClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            String cacheKey = getCacheKey(pdfFile);
            Object document = getCachedDocument(cacheKey, pdfFile, pdDocumentClass);
            int pageCount = (Integer) pdDocumentClass.getMethod("getNumberOfPages").invoke(document);
            if (pageCount <= 0) {
                throw new IOException("PDF has no pages");
            }
            return pageCount;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("password")) {
                throw new IOException("PDF is password-protected.");
            }
            throw new IOException("Failed to load PDF: " + msg);
        }
    }
    
    /**
     * Basic page count (fallback)
     */
    private static int getPageCountBasic(File pdfFile) throws IOException {
        // Return 1 as default for basic mode
        return 1;
    }
}


