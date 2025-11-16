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

        textArea = new JTextArea();
        textArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        textArea.setEditable(false);
        textArea.getCaret().setVisible(true);

        lineNumberArea = new JTextArea();
        lineNumberArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        lineNumberArea.setBackground(new Color(240, 240, 240));
        lineNumberArea.setForeground(Color.GRAY);
        lineNumberArea.setEditable(false);
        lineNumberArea.setFocusable(false);
        lineNumberArea.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setRowHeaderView(lineNumberArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        add(scrollPane, BorderLayout.CENTER);

        textArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateLineNumbers(); }
            public void removeUpdate(DocumentEvent e) { updateLineNumbers(); }
            public void changedUpdate(DocumentEvent e) { updateLineNumbers(); }
        });
    }

    public void setText(String text) {
        textArea.setText(text);
        updateLineNumbers();
    }

    private void updateLineNumbers() {
        int lines = textArea.getLineCount();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines; i++) {
            sb.append(String.format(" %4d ", i)).append("\n");
        }
        lineNumberArea.setText(sb.toString());
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
}
