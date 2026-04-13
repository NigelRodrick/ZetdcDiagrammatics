package com.zetdc.diagrammatics.utils;

import java.io.File;
import java.io.IOException;

/**
 * PDF reader that extracts actual content from PDF files
 * This version will work with or without PDFBox library
 */
public class PdfReader {
    
    /**
     * Extract text content from a PDF file
     */
    public static String extractText(File pdfFile) throws IOException {
        try {
            // Try to use PDFBox if available
            return extractTextWithPDFBox(pdfFile);
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            // Fallback to basic file reading if PDFBox is not available
            return extractTextBasic(pdfFile);
        }
    }
    
    /**
     * Extract text using PDFBox library
     */
    private static String extractTextWithPDFBox(File pdfFile) throws IOException {
        try {
            // Use reflection to avoid compilation errors if PDFBox is not available
            Class<?> pdDocumentClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            Class<?> textStripperClass = Class.forName("org.apache.pdfbox.text.PDFTextStripper");
            
            Object document = pdDocumentClass.getMethod("load", File.class).invoke(null, pdfFile);
            Object stripper = textStripperClass.newInstance();
            
            String text = (String) textStripperClass.getMethod("getText", pdDocumentClass).invoke(stripper, document);
            
            // Close document
            pdDocumentClass.getMethod("close").invoke(document);
            
            return text;
        } catch (Exception e) {
            throw new IOException("Failed to extract text with PDFBox: " + e.getMessage());
        }
    }
    
    /**
     * Basic text extraction fallback
     */
    private static String extractTextBasic(File pdfFile) throws IOException {
        return "PDF Text Extraction requires PDFBox library.\n" +
               "File: " + pdfFile.getName() + "\n" +
               "Size: " + formatFileSize(pdfFile.length()) + "\n" +
               "To enable full text extraction, please add PDFBox JAR files to the classpath.\n\n" +
               "This is a placeholder for the actual PDF content.";
    }
    
    /**
     * Get PDF document information
     */
    public static String getDocumentInfo(File pdfFile) throws IOException {
        try {
            return getDocumentInfoWithPDFBox(pdfFile);
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            return getDocumentInfoBasic(pdfFile);
        }
    }
    
    /**
     * Get document info using PDFBox
     */
    private static String getDocumentInfoWithPDFBox(File pdfFile) throws IOException {
        try {
            Class<?> pdDocumentClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            Class<?> docInfoClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocumentInformation");
            
            Object document = pdDocumentClass.getMethod("load", File.class).invoke(null, pdfFile);
            Object info = pdDocumentClass.getMethod("getDocumentInformation").invoke(document);
            
            StringBuilder infoText = new StringBuilder();
            infoText.append("PDF Document Information:\n");
            infoText.append("========================\n\n");
            
            infoText.append("File: ").append(pdfFile.getName()).append("\n");
            infoText.append("Path: ").append(pdfFile.getAbsolutePath()).append("\n");
            infoText.append("Size: ").append(formatFileSize(pdfFile.length())).append("\n");
            infoText.append("Pages: ").append(pdDocumentClass.getMethod("getNumberOfPages").invoke(document)).append("\n");
            infoText.append("Last Modified: ").append(new java.util.Date(pdfFile.lastModified())).append("\n\n");
            
            // Get metadata if available
            String title = (String) docInfoClass.getMethod("getTitle").invoke(info);
            String author = (String) docInfoClass.getMethod("getAuthor").invoke(info);
            String subject = (String) docInfoClass.getMethod("getSubject").invoke(info);
            String creator = (String) docInfoClass.getMethod("getCreator").invoke(info);
            String producer = (String) docInfoClass.getMethod("getProducer").invoke(info);
            
            if (title != null && !title.trim().isEmpty()) {
                infoText.append("Title: ").append(title).append("\n");
            }
            if (author != null && !author.trim().isEmpty()) {
                infoText.append("Author: ").append(author).append("\n");
            }
            if (subject != null && !subject.trim().isEmpty()) {
                infoText.append("Subject: ").append(subject).append("\n");
            }
            if (creator != null && !creator.trim().isEmpty()) {
                infoText.append("Creator: ").append(creator).append("\n");
            }
            if (producer != null && !producer.trim().isEmpty()) {
                infoText.append("Producer: ").append(producer).append("\n");
            }
            
            // Close document
            pdDocumentClass.getMethod("close").invoke(document);
            
            return infoText.toString();
        } catch (Exception e) {
            throw new IOException("Failed to get document info with PDFBox: " + e.getMessage());
        }
    }
    
    /**
     * Basic document info fallback
     */
    private static String getDocumentInfoBasic(File pdfFile) throws IOException {
        StringBuilder infoText = new StringBuilder();
        
        infoText.append("PDF Document Information:\n");
        infoText.append("========================\n\n");
        
        infoText.append("File: ").append(pdfFile.getName()).append("\n");
        infoText.append("Path: ").append(pdfFile.getAbsolutePath()).append("\n");
        infoText.append("Size: ").append(formatFileSize(pdfFile.length())).append("\n");
        infoText.append("Last Modified: ").append(new java.util.Date(pdfFile.lastModified())).append("\n\n");
        
        infoText.append("Note: This is a simplified PDF viewer.\n");
        infoText.append("To view actual PDF content and metadata, please add Apache PDFBox library.\n");
        infoText.append("The file appears to be a valid PDF based on file extension and basic properties.\n");
        
        return infoText.toString();
    }
    
    /**
     * Get a preview of PDF content
     */
    public static String getContentPreview(File pdfFile, int maxPages) throws IOException {
        try {
            return getContentPreviewWithPDFBox(pdfFile, maxPages);
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            return getContentPreviewBasic(pdfFile, maxPages);
        }
    }
    
    /**
     * Get content preview using PDFBox
     */
    private static String getContentPreviewWithPDFBox(File pdfFile, int maxPages) throws IOException {
        try {
            Class<?> pdDocumentClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            Class<?> textStripperClass = Class.forName("org.apache.pdfbox.text.PDFTextStripper");
            
            Object document = pdDocumentClass.getMethod("load", File.class).invoke(null, pdfFile);
            Object stripper = textStripperClass.newInstance();
            
            // Set page range
            textStripperClass.getMethod("setStartPage", int.class).invoke(stripper, 1);
            int totalPages = (Integer) pdDocumentClass.getMethod("getNumberOfPages").invoke(document);
            int endPage = Math.min(maxPages, totalPages);
            textStripperClass.getMethod("setEndPage", int.class).invoke(stripper, endPage);
            
            String text = (String) textStripperClass.getMethod("getText", pdDocumentClass).invoke(stripper, document);
            
            // Close document
            pdDocumentClass.getMethod("close").invoke(document);
            
            return text;
        } catch (Exception e) {
            throw new IOException("Failed to get content preview with PDFBox: " + e.getMessage());
        }
    }
    
    /**
     * Basic content preview fallback
     */
    private static String getContentPreviewBasic(File pdfFile, int maxPages) throws IOException {
        return "PDF Content Preview not available without PDFBox library.\n" +
               "File: " + pdfFile.getName() + "\n" +
               "To view actual PDF content, please add Apache PDFBox to the classpath.\n\n" +
               "This is a placeholder for the first " + maxPages + " pages of content.";
    }
    
    /**
     * Check if a file is a valid PDF
     */
    public static boolean isValidPdf(File file) {
        try {
            return isValidPdfWithPDFBox(file);
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            return isValidPdfBasic(file);
        }
    }
    
    /**
     * Check if PDF is valid using PDFBox
     */
    private static boolean isValidPdfWithPDFBox(File file) {
        try {
            Class<?> pdDocumentClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            Object document = pdDocumentClass.getMethod("load", File.class).invoke(null, file);
            int pageCount = (Integer) pdDocumentClass.getMethod("getNumberOfPages").invoke(document);
            pdDocumentClass.getMethod("close").invoke(document);
            return pageCount > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Basic PDF validation
     */
    private static boolean isValidPdfBasic(File file) {
        return file.exists() && 
               file.getName().toLowerCase().endsWith(".pdf") && 
               file.length() > 0 &&
               file.canRead();
    }
    
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}