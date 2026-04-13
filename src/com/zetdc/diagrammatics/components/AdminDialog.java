package com.zetdc.diagrammatics.components;

import com.zetdc.diagrammatics.utils.TvMode;
import com.zetdc.diagrammatics.utils.UserService;

import javax.swing.*;
import java.awt.*;

/**
 * Simple admin dialog that allows managing users who can access edit mode.
 *
 * Features:
 * - List existing users.
 * - Add or update a user with username, password, and roles.
 * - Delete selected user (except the last remaining admin).
 */
public class AdminDialog extends JDialog {

    private final UserService userService;
    private final DefaultListModel<String> userListModel = new DefaultListModel<>();
    private final JList<String> userList = new JList<>(userListModel);

    private final JTextField usernameField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JCheckBox canEditCheck = new JCheckBox("Can make changes (Editor)", true);
    private final JCheckBox adminCheck = new JCheckBox("Is admin (can manage users)", false);

    public AdminDialog(Frame owner, UserService userService) {
        super(owner, "Manage Users", true);
        this.userService = userService;

        initializeComponents();
        layoutComponents();
        loadUsers();

        pack();
        setLocationRelativeTo(owner);
        setMinimumSize(new Dimension(600, 400));
    }

    private void initializeComponents() {
        if (TvMode.ENABLED) {
            Font labelFont = TvMode.getLabelFont();
            Font inputFont = TvMode.getInputFont();
            userList.setFont(inputFont);
            usernameField.setFont(inputFont);
            passwordField.setFont(inputFont);
            canEditCheck.setFont(labelFont);
            adminCheck.setFont(labelFont);
        }

        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    private void layoutComponents() {
        setLayout(new BorderLayout(10, 10));

        // Left: user list
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        JLabel listLabel = new JLabel("Existing Users");
        if (TvMode.ENABLED) listLabel.setFont(TvMode.getLabelFont());
        leftPanel.add(listLabel, BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(userList), BorderLayout.CENTER);

        // Right: add / edit form
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));

        JPanel userRow = new JPanel(new BorderLayout(5, 5));
        JLabel userLabel = new JLabel("Username:");
        if (TvMode.ENABLED) userLabel.setFont(TvMode.getLabelFont());
        userRow.add(userLabel, BorderLayout.WEST);
        userRow.add(usernameField, BorderLayout.CENTER);

        JPanel passRow = new JPanel(new BorderLayout(5, 5));
        JLabel passLabel = new JLabel("Password:");
        if (TvMode.ENABLED) passLabel.setFont(TvMode.getLabelFont());
        passRow.add(passLabel, BorderLayout.WEST);
        passRow.add(passwordField, BorderLayout.CENTER);

        JPanel rolesRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        rolesRow.add(canEditCheck);
        rolesRow.add(adminCheck);

        JPanel buttonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JButton saveButton = new JButton("Add / Update User");
        JButton deleteButton = new JButton("Delete Selected");
        if (TvMode.ENABLED) {
            TvMode.applyButtonStyle(saveButton);
            TvMode.applyButtonStyle(deleteButton);
        }
        buttonsRow.add(saveButton);
        buttonsRow.add(deleteButton);

        saveButton.addActionListener(e -> onSaveUser());
        deleteButton.addActionListener(e -> onDeleteUser());

        formPanel.add(userRow);
        formPanel.add(Box.createVerticalStrut(8));
        formPanel.add(passRow);
        formPanel.add(Box.createVerticalStrut(8));
        formPanel.add(rolesRow);
        formPanel.add(Box.createVerticalStrut(16));
        formPanel.add(buttonsRow);
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(leftPanel, BorderLayout.CENTER);
        add(formPanel, BorderLayout.EAST);

        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        if (TvMode.ENABLED) TvMode.applyButtonStyle(closeButton);
        closeButton.addActionListener(e -> dispose());
        bottomRow.add(closeButton);
        add(bottomRow, BorderLayout.SOUTH);
    }

    private void loadUsers() {
        userListModel.clear();
        for (UserService.User user : userService.listUsers()) {
            StringBuilder label = new StringBuilder(user.username);
            if (userService.isAdmin(user)) {
                label.append("  (Admin");
                if (userService.canEdit(user)) {
                    label.append(", Can Edit");
                }
                label.append(")");
            } else if (userService.canEdit(user)) {
                label.append("  (Can Edit)");
            }
            userListModel.addElement(label.toString());
        }
    }

    private void onSaveUser() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        boolean canEdit = canEditCheck.isSelected();
        boolean admin = adminCheck.isSelected();

        try {
            userService.addOrUpdateUser(username, password, admin, canEdit);
            loadUsers();
            // Clear password field after save for safety
            passwordField.setText("");
            JOptionPane.showMessageDialog(this, "User saved successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Input Error", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save user: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onDeleteUser() {
        int index = userList.getSelectedIndex();
        if (index < 0) {
            JOptionPane.showMessageDialog(this, "Select a user to delete.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String selected = userListModel.get(index);
        String username = selected.split(" ")[0]; // username is first token

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete user '" + username + "'?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            userService.deleteUser(username);
            loadUsers();
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot Delete", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to delete user: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}

