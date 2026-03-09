package com.zetdc.diagrammatics.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple user management and authentication service.
 *
 * Responsibilities:
 * - Persist users to a JSON file in the user's home directory.
 * - Provide verification for login.
 * - Track roles: admin (can manage users) and canEdit (can make changes).
 * - Ensure a default admin/editor account exists: depot / foreman.
 */
public class UserService {

    /** Persisted user record. */
    public static class User {
        public final String username;
        private final String passwordHash;
        private final boolean admin;
        private final boolean canEdit;

        public User(String username, String passwordHash, boolean admin, boolean canEdit) {
            this.username = username;
            this.passwordHash = passwordHash;
            this.admin = admin;
            this.canEdit = canEdit;
        }
    }

    private static final String DEFAULT_ADMIN_USERNAME = "depot";
    private static final String DEFAULT_ADMIN_PASSWORD = "foreman";

    private final Path usersFilePath;
    private final Map<String, User> usersByName = new HashMap<>();

    public UserService() {
        this(Paths.get(System.getProperty("user.home"), ".zetdcdiagrammatics-users.json"));
    }

    // Visible for testing / future extension
    UserService(Path usersFilePath) {
        this.usersFilePath = usersFilePath;
        loadUsers();
        ensureDefaultAdmin();
    }

    /** Verify credentials and return the corresponding user, or null if invalid. */
    public synchronized User verify(String username, String password) {
        if (username == null || password == null) return null;
        User existing = usersByName.get(username.toLowerCase());
        if (existing == null) {
            return null;
        }
        String candidateHash = hashPassword(password);
        if (!existing.passwordHash.equals(candidateHash)) {
            return null;
        }
        return existing;
    }

    public boolean isAdmin(User user) {
        return user != null && user.admin;
    }

    public boolean canEdit(User user) {
        return user != null && user.canEdit;
    }

    /** Add or update a user. Returns the up-to-date user instance. */
    public synchronized User addOrUpdateUser(String username, String password, boolean admin, boolean canEdit) throws IOException {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }

        String key = username.trim().toLowerCase();

        String passwordHash = hashPassword(password);
        User user = new User(username.trim(), passwordHash, admin, canEdit);
        usersByName.put(key, user);
        saveUsers();
        return user;
    }

    /** Remove a user; may not remove the last remaining admin. */
    public synchronized void deleteUser(String username) throws IOException {
        if (username == null) return;
        String key = username.trim().toLowerCase();
        User existing = usersByName.get(key);
        if (existing == null) return;

        if (existing.admin && countAdmins() <= 1) {
            throw new IllegalStateException("Cannot remove the last admin user.");
        }

        usersByName.remove(key);
        saveUsers();
    }

    /** List users in a stable order for the admin dialog. */
    public synchronized List<User> listUsers() {
        List<User> list = new ArrayList<>(usersByName.values());
        // Sort by username for predictable order
        list.sort((a, b) -> a.username.compareToIgnoreCase(b.username));
        return Collections.unmodifiableList(list);
    }

    // ===== Internal helpers =====

    private void ensureDefaultAdmin() {
        if (!usersByName.containsKey(DEFAULT_ADMIN_USERNAME.toLowerCase())) {
            try {
                addOrUpdateUser(DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWORD, true, true);
            } catch (IOException e) {
                // If we cannot persist now, at least keep it in memory for this session
                String hash = hashPassword(DEFAULT_ADMIN_PASSWORD);
                User user = new User(DEFAULT_ADMIN_USERNAME, hash, true, true);
                usersByName.put(DEFAULT_ADMIN_USERNAME.toLowerCase(), user);
            }
        }
    }

    private int countAdmins() {
        int count = 0;
        for (User u : usersByName.values()) {
            if (u.admin) count++;
        }
        return count;
    }

    private void loadUsers() {
        usersByName.clear();
        if (!Files.exists(usersFilePath)) {
            return;
        }
        try {
            String json = new String(Files.readAllBytes(usersFilePath), StandardCharsets.UTF_8);
            parseUsersJson(json);
        } catch (Exception ignored) {
            // Corrupt or unreadable users file – start fresh, default admin will be recreated
        }
    }

    private void saveUsers() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"users\": [");
        List<User> list = new ArrayList<>(usersByName.values());
        list.sort((a, b) -> a.username.compareToIgnoreCase(b.username));
        for (int i = 0; i < list.size(); i++) {
            User u = list.get(i);
            if (i > 0) sb.append(", ");
            sb.append("{\"username\":\"").append(escape(u.username)).append("\"")
              .append(",\"passwordHash\":\"").append(escape(u.passwordHash)).append("\"")
              .append(",\"admin\":").append(u.admin)
              .append(",\"canEdit\":").append(u.canEdit)
              .append("}");
        }
        sb.append("]\n");
        sb.append("}\n");

        Files.write(usersFilePath, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void parseUsersJson(String json) {
        if (json == null || json.isEmpty()) return;
        String look = "\"users\"";
        int i = json.indexOf(look);
        if (i < 0) return;
        int lb = json.indexOf('[', i);
        int rb = json.indexOf(']', lb);
        if (lb < 0 || rb < 0) return;
        String arr = json.substring(lb + 1, rb).trim();
        if (arr.isEmpty()) return;

        String[] items = arr.split("\\},");
        for (String it : items) {
            String item = it.trim();
            if (!item.endsWith("}")) item = item + "}";
            String username = parseStringField(item, "username");
            String passwordHash = parseStringField(item, "passwordHash");
            boolean admin = parseBooleanField(item, "admin");
            boolean canEdit = parseBooleanField(item, "canEdit");
            if (username == null || username.trim().isEmpty() || passwordHash == null || passwordHash.isEmpty()) {
                continue;
            }
            User user = new User(username.trim(), passwordHash, admin, canEdit);
            usersByName.put(username.trim().toLowerCase(), user);
        }
    }

    private static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback: extremely simple (and insecure) hash, but avoids failing login entirely
            return Integer.toHexString(password.hashCode());
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String parseStringField(String json, String field) {
        try {
            String look = "\"" + field + "\":\"";
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

    private static boolean parseBooleanField(String json, String field) {
        try {
            String look = "\"" + field + "\":";
            int i = json.indexOf(look);
            if (i < 0) return false;
            int start = i + look.length();
            int end = start;
            while (end < json.length() && Character.isLetter(json.charAt(end))) {
                end++;
            }
            String raw = json.substring(start, end).trim();
            return "true".equalsIgnoreCase(raw);
        } catch (Exception e) {
            return false;
        }
    }
}

