package burp;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

public class LineNumberTextArea extends JPanel {
    private JTextArea textArea;
    private JTextArea lineNumberArea;

    public LineNumberTextArea() {
        setLayout(new BorderLayout());

        // Main text area (right side)
        textArea = new JTextArea();
        textArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        textArea.setEditable(false);
        textArea.setBackground(new Color(245, 245, 245));  // Light gray default
        textArea.setForeground(Color.BLACK);  // BLACK text
        textArea.getCaret().setVisible(true);
        textArea.getCaret().setSelectionVisible(true);

        // Line numbers area (left side)
        lineNumberArea = new JTextArea();
        lineNumberArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        lineNumberArea.setBackground(new Color(240, 240, 240));  // Gray background
        lineNumberArea.setForeground(new Color(100, 100, 100));  // Dark gray text
        lineNumberArea.setEditable(false);
        lineNumberArea.setFocusable(false);
        lineNumberArea.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));

        // Create scroll pane
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setRowHeaderView(lineNumberArea);  // Line numbers on left
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        add(scrollPane, BorderLayout.CENTER);

        // Update line numbers when text changes
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateLineNumbers(); }
            public void removeUpdate(DocumentEvent e) { updateLineNumbers(); }
            public void changedUpdate(DocumentEvent e) { updateLineNumbers(); }
        });
    }

    public void setText(String text) {
        textArea.setText(text);
        textArea.setCaretPosition(0);
        updateLineNumbers();
    }

    public String getText() {
        return textArea.getText();
    }

    public JTextArea getTextArea() {
        return textArea;
    }

    public JTextArea getLineNumberArea() {
        return lineNumberArea;
    }

    // Method to set edit mode
    public void setEditMode(boolean editMode) {
        textArea.setEditable(editMode);

        if (editMode) {
            // Edit mode - white background for main text
            textArea.setBackground(Color.WHITE);
            textArea.setForeground(Color.BLACK);  // BLACK text - visible!
        } else {
            // Read-only mode - light gray background
            textArea.setBackground(new Color(245, 245, 245));
            textArea.setForeground(Color.BLACK);  // BLACK text
        }

        // Line numbers ALWAYS gray - never white!
        lineNumberArea.setBackground(new Color(240, 240, 240));
        lineNumberArea.setForeground(new Color(100, 100, 100));
    }

    private void updateLineNumbers() {
        int lines = textArea.getLineCount();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines; i++) {
            sb.append(String.format("%4d", i)).append("\n");
        }
        lineNumberArea.setText(sb.toString());
    }
}
