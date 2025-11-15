package burp;

import javax.swing.*;
import java.awt.*;

public class LineNumberTextArea extends JPanel {
    private JTextArea lineNumbers;
    private JTextArea textArea;

    public LineNumberTextArea() {
        setLayout(new BorderLayout());

        lineNumbers = new JTextArea("1\n");
        lineNumbers.setBackground(Color.LIGHT_GRAY);
        lineNumbers.setEditable(false);
        lineNumbers.setFont(new Font("Consolas", Font.PLAIN, 12));

        textArea = new JTextArea();
        textArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        textArea.setEditable(false);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setRowHeaderView(lineNumbers);

        add(scrollPane, BorderLayout.CENTER);
    }

    public void setText(String text) {
        textArea.setText(text);
        updateLineNumbers();
    }

    private void updateLineNumbers() {
        int lines = textArea.getLineCount();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines; i++) {
            sb.append(String.format("%4d", i)).append("\n");
        }
        lineNumbers.setText(sb.toString());
    }

    public JTextArea getTextArea() {
        return textArea;
    }
}
