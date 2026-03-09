package com.zetdc.diagrammatics.utils;

import com.zetdc.diagrammatics.components.PdfViewerComponent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Manages per-PDF project folders on the Desktop and persists annotations.
 */
public class FileManager {

    public static Path getDesktopPath() {
        String userHome = System.getProperty("user.home");
        Path desktop = Paths.get(userHome, "Desktop");
        if (Files.exists(desktop)) return desktop;
        // Fallback to user home if Desktop not found
        return Paths.get(userHome);
    }

    public static Path getProjectsRoot() throws IOException {
        Path root = getDesktopPath().resolve("ZetdcDiagrammatics");
        if (!Files.exists(root)) {
            Files.createDirectories(root);
        }
        return root;
    }

    public static Path getProjectFolderForPdf(File pdfFile) throws IOException {
        String baseName = stripExtension(pdfFile.getName());
        Path projectFolder = getProjectsRoot().resolve(baseName);
        if (!Files.exists(projectFolder)) {
            Files.createDirectories(projectFolder);
        }
        return projectFolder;
    }

    public static void ensureProjectFolder(File pdfFile) throws IOException {
        getProjectFolderForPdf(pdfFile);
    }

    public static Path getAnnotationsFile(File pdfFile) throws IOException {
        Path project = getProjectFolderForPdf(pdfFile);
        return project.resolve("annotations.json");
    }

    public static void saveAnnotations(File pdfFile,
                                       Map<Integer, List<PdfViewerComponent.MarkerDTO>> markers,
                                       Map<Integer, List<PdfViewerComponent.LineDTO>> lines,
                                       Map<Integer, List<PdfViewerComponent.TextDTO>> texts) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        // source PDF absolute path to aid in re-opening on startup
        sb.append("  ")
          .append("\"sourcePdf\": \"")
          .append(escape(pdfFile.getAbsolutePath()))
          .append("\",\n");
        // markers
        sb.append("  \"markers\": {\n");
        int pi = 0; int pcount;
        pcount = markers == null ? 0 : markers.size();
        if (markers != null) {
            for (Map.Entry<Integer, List<PdfViewerComponent.MarkerDTO>> e : markers.entrySet()) {
                sb.append("    \"").append(e.getKey()).append("\": [");
                List<PdfViewerComponent.MarkerDTO> list = e.getValue();
                for (int i = 0; i < list.size(); i++) {
                    PdfViewerComponent.MarkerDTO m = list.get(i);
                    if (i > 0) sb.append(", ");
                    sb.append("{\"x\":").append(m.x)
                      .append(",\"y\":").append(m.y)
                      .append(",\"text\":\"").append(escape(m.text)).append("\"")
                      .append(",\"colorRgb\":").append(m.colorRgb);
                    if (m.createdBy != null && !m.createdBy.isEmpty()) {
                        sb.append(",\"createdBy\":\"").append(escape(m.createdBy)).append("\"");
                    }
                    sb.append("}");
                }
                sb.append("]");
                if (++pi < pcount) sb.append(",\n"); else sb.append("\n");
            }
        }
        sb.append("  },\n");

        // lines
        sb.append("  \"lines\": {\n");
        pi = 0; pcount = lines == null ? 0 : lines.size();
        if (lines != null) {
            for (Map.Entry<Integer, List<PdfViewerComponent.LineDTO>> e : lines.entrySet()) {
                sb.append("    \"").append(e.getKey()).append("\": [");
                List<PdfViewerComponent.LineDTO> list = e.getValue();
                for (int i = 0; i < list.size(); i++) {
                    PdfViewerComponent.LineDTO l = list.get(i);
                    if (i > 0) sb.append(", ");
                    sb.append("{\"startX\":").append(l.startX)
                      .append(",\"startY\":").append(l.startY)
                      .append(",\"endX\":").append(l.endX)
                      .append(",\"endY\":").append(l.endY)
                      .append(",\"text\":\"").append(escape(l.text)).append("\"")
                      .append(",\"lineType\":").append(l.lineType)
                      .append(",\"radius\":").append(l.radius);
                    if (l.createdBy != null && !l.createdBy.isEmpty()) {
                        sb.append(",\"createdBy\":\"").append(escape(l.createdBy)).append("\"");
                    }
                    sb.append("}");
                }
                sb.append("]");
                if (++pi < pcount) sb.append(",\n"); else sb.append("\n");
            }
        }
        sb.append("  },\n");

        // texts
        sb.append("  \"texts\": {\n");
        pi = 0; pcount = texts == null ? 0 : texts.size();
        if (texts != null) {
            for (Map.Entry<Integer, List<PdfViewerComponent.TextDTO>> e : texts.entrySet()) {
                sb.append("    \"").append(e.getKey()).append("\": [");
                List<PdfViewerComponent.TextDTO> list = e.getValue();
                for (int i = 0; i < list.size(); i++) {
                    PdfViewerComponent.TextDTO t = list.get(i);
                    if (i > 0) sb.append(", ");
                    sb.append("{\"x\":").append(t.x)
                      .append(",\"y\":").append(t.y)
                      .append(",\"text\":\"").append(escape(t.text)).append("\"")
                      .append(",\"backgroundColorRgb\":").append(t.backgroundColorRgb);
                    if (t.createdBy != null && !t.createdBy.isEmpty()) {
                        sb.append(",\"createdBy\":\"").append(escape(t.createdBy)).append("\"");
                    }
                    sb.append("}");
                }
                sb.append("]");
                if (++pi < pcount) sb.append(",\n"); else sb.append("\n");
            }
        }
        sb.append("  }\n");
        sb.append("}\n");

        Path annotationsPath = getAnnotationsFile(pdfFile);
        Files.write(annotationsPath, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static LoadedAnnotations loadAnnotations(File pdfFile) throws IOException {
        Path annotationsPath = getAnnotationsFile(pdfFile);
        if (!Files.exists(annotationsPath)) {
            return new LoadedAnnotations();
        }
        String json = new String(Files.readAllBytes(annotationsPath), StandardCharsets.UTF_8);
        LoadedAnnotations loaded = new LoadedAnnotations();
        loaded.sourcePdfPath = pdfFile.getAbsolutePath();
        loaded.markers = JsonParsers.parseMarkers(json);
        loaded.lines = JsonParsers.parseLines(json);
        loaded.texts = JsonParsers.parseTexts(json);
        return loaded;
    }

    public static LoadedAnnotations loadAnnotations(Path annotationsPath) throws IOException {
        if (!Files.exists(annotationsPath)) {
            return new LoadedAnnotations();
        }
        String json = new String(Files.readAllBytes(annotationsPath), StandardCharsets.UTF_8);
        LoadedAnnotations loaded = new LoadedAnnotations();
        loaded.sourcePdfPath = JsonParsers.parseTopLevelString(json, "sourcePdf");
        loaded.markers = JsonParsers.parseMarkers(json);
        loaded.lines = JsonParsers.parseLines(json);
        loaded.texts = JsonParsers.parseTexts(json);
        return loaded;
    }

    private static String stripExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    /** Holds parsed annotations per pages */
    public static class LoadedAnnotations {
        public java.util.Map<Integer, java.util.List<PdfViewerComponent.MarkerDTO>> markers = new java.util.HashMap<>();
        public java.util.Map<Integer, java.util.List<PdfViewerComponent.LineDTO>> lines = new java.util.HashMap<>();
        public java.util.Map<Integer, java.util.List<PdfViewerComponent.TextDTO>> texts = new java.util.HashMap<>();
        public String sourcePdfPath; // absolute path if available
    }

    /** Minimal JSON helpers reusing controller parsing style but scoped */
    private static class JsonParsers {
        static String parseTopLevelString(String json, String key) {
            try {
                String look = "\"" + key + "\": \"";
                int i = json.indexOf(look);
                if (i < 0) return null;
                int start = i + look.length();
                int end = json.indexOf('"', start);
                if (end < 0) return null;
                String raw = json.substring(start, end);
                return raw.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
            } catch (Exception e) {
                return null;
            }
        }
        static java.util.Map<Integer, java.util.List<PdfViewerComponent.MarkerDTO>> parseMarkers(String json) {
            java.util.Map<Integer, java.util.List<PdfViewerComponent.MarkerDTO>> out = new java.util.HashMap<>();
            try {
                String look = "\"markers\"";
                int i = json.indexOf(look);
                if (i < 0) return out;
                int lb = json.indexOf('{', i);
                int rb = json.indexOf("}\n", lb);
                if (lb < 0 || rb < 0) return out;
                String block = json.substring(lb + 1, rb).trim();
                int pos = 0;
                while (pos < block.length()) {
                    int pk1 = block.indexOf('"', pos);
                    if (pk1 < 0) break;
                    int pk2 = block.indexOf('"', pk1 + 1);
                    String pStr = block.substring(pk1 + 1, pk2);
                    int arrL = block.indexOf('[', pk2);
                    int arrR = block.indexOf(']', arrL);
                    String arr = block.substring(arrL + 1, arrR);
                    java.util.List<PdfViewerComponent.MarkerDTO> list = new java.util.ArrayList<>();
                    if (!arr.trim().isEmpty()) {
                        String[] items = arr.split("\\},");
                        for (String it : items) {
                            String item = it.trim();
                            if (!item.endsWith("}")) item = item + "}";
                            float x = parseFloatField(item, "x");
                            float y = parseFloatField(item, "y");
                            String t = parseStringField(item, "text");
                            int colorRgb = parseIntField(item, "colorRgb");
                            String createdBy = parseStringField(item, "createdBy");
                            PdfViewerComponent.MarkerDTO dto = new PdfViewerComponent.MarkerDTO(x, y, t, new java.awt.Color(colorRgb, true), createdBy);
                            list.add(dto);
                        }
                    }
                    out.put(Integer.parseInt(pStr), list);
                    pos = arrR + 1;
                    int comma = block.indexOf(',', pos);
                    if (comma < 0) break;
                    pos = comma + 1;
                }
            } catch (Exception ignore) {}
            return out;
        }

        static java.util.Map<Integer, java.util.List<PdfViewerComponent.LineDTO>> parseLines(String json) {
            java.util.Map<Integer, java.util.List<PdfViewerComponent.LineDTO>> out = new java.util.HashMap<>();
            try {
                String look = "\"lines\"";
                int i = json.indexOf(look);
                if (i < 0) return out;
                int lb = json.indexOf('{', i);
                int rb = json.indexOf("}\n", lb);
                if (lb < 0 || rb < 0) return out;
                String block = json.substring(lb + 1, rb).trim();
                int pos = 0;
                while (pos < block.length()) {
                    int pk1 = block.indexOf('"', pos);
                    if (pk1 < 0) break;
                    int pk2 = block.indexOf('"', pk1 + 1);
                    String pStr = block.substring(pk1 + 1, pk2);
                    int arrL = block.indexOf('[', pk2);
                    int arrR = block.indexOf(']', arrL);
                    if (arrL < 0 || arrR < 0) break;
                    String arr = block.substring(arrL + 1, arrR);
                    java.util.List<PdfViewerComponent.LineDTO> list = new java.util.ArrayList<>();
                    if (!arr.trim().isEmpty()) {
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
                            String createdBy = parseStringField(item, "createdBy");
                            PdfViewerComponent.LineDTO dto = new PdfViewerComponent.LineDTO(startX, startY, endX, endY, t, createdBy);
                            dto.lineType = lineType;
                            dto.radius = radius;
                            list.add(dto);
                        }
                    }
                    out.put(Integer.parseInt(pStr), list);
                    pos = arrR + 1;
                    int comma = block.indexOf(',', pos);
                    if (comma < 0) break;
                    pos = comma + 1;
                }
            } catch (Exception ignore) {}
            return out;
        }

        static java.util.Map<Integer, java.util.List<PdfViewerComponent.TextDTO>> parseTexts(String json) {
            java.util.Map<Integer, java.util.List<PdfViewerComponent.TextDTO>> out = new java.util.HashMap<>();
            try {
                String look = "\"texts\"";
                int i = json.indexOf(look);
                if (i < 0) return out;
                int lb = json.indexOf('{', i);
                int rb = json.indexOf("}\n", lb);
                if (lb < 0 || rb < 0) return out;
                String block = json.substring(lb + 1, rb).trim();
                int pos = 0;
                while (pos < block.length()) {
                    int pk1 = block.indexOf('"', pos);
                    if (pk1 < 0) break;
                    int pk2 = block.indexOf('"', pk1 + 1);
                    String pStr = block.substring(pk1 + 1, pk2);
                    int arrL = block.indexOf('[', pk2);
                    int arrR = block.indexOf(']', arrL);
                    if (arrL < 0 || arrR < 0) break;
                    String arr = block.substring(arrL + 1, arrR);
                    java.util.List<PdfViewerComponent.TextDTO> list = new java.util.ArrayList<>();
                    if (!arr.trim().isEmpty()) {
                        String[] items = arr.split("\\},");
                        for (String it : items) {
                            String item = it.trim();
                            if (!item.endsWith("}")) item = item + "}";
                            float x = parseFloatField(item, "x");
                            float y = parseFloatField(item, "y");
                            String t = parseStringField(item, "text");
                            int backgroundColorRgb = parseIntField(item, "backgroundColorRgb");
                            String createdBy = parseStringField(item, "createdBy");
                            PdfViewerComponent.TextDTO dto = new PdfViewerComponent.TextDTO(x, y, t);
                            dto.backgroundColorRgb = backgroundColorRgb;
                            dto.createdBy = createdBy;
                            list.add(dto);
                        }
                    }
                    out.put(Integer.parseInt(pStr), list);
                    pos = arrR + 1;
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
    }
}


