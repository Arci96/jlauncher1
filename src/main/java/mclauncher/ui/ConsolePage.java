package mclauncher.ui;

import mclauncher.GameRunner;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

/** Ausgabe von Minecraft und Installern. */
public class ConsolePage extends Page {
    private final JTextArea area = new JTextArea();

    public ConsolePage(MainWindow win) {
        super(win);
        Widgets.GlowButton clear = new Widgets.GlowButton("Leeren", Widgets.Style.DARK);
        Widgets.GlowButton copy = new Widgets.GlowButton("Kopieren", Widgets.Style.DARK);
        clear.addActionListener(e -> { area.setText(""); GameRunner.clearHistory(); });
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(area.getText()), null));
        add(header("Konsole", "Spielausgabe und Fehlermeldungen – hier findest du die Ursache, wenn etwas nicht startet", clear, copy), BorderLayout.NORTH);

        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        add(sp, BorderLayout.CENTER);

        StringBuilder sb = new StringBuilder();
        for (String line : GameRunner.history()) sb.append(line).append('\n');
        area.setText(sb.toString());
        GameRunner.addLogListener(line -> SwingUtilities.invokeLater(() -> {
            area.append(line + "\n");
            if (area.getLineCount() > 6000) {
                try {
                    area.replaceRange("", 0, area.getLineEndOffset(1000));
                } catch (Exception ignored) { }
            }
            area.setCaretPosition(area.getDocument().getLength());
        }));
    }
}
