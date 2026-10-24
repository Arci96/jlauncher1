package mclauncher.ui;

import mclauncher.Downloads;

import javax.swing.*;
import java.awt.*;

/** Eine Zeile für eine Hintergrund-Aufgabe (Download, Installation, Spielstart). */
public class TaskRow extends Widgets.RoundPanel {
    private final Downloads.Task task;
    private final JLabel title = new JLabel();
    private final JLabel info = new JLabel();
    private final JLabel pct = new JLabel();
    private final JProgressBar bar = new JProgressBar(0, 1000);
    private final Widgets.GlowButton cancel = new Widgets.GlowButton("\u2715", Widgets.Style.DARK);

    public TaskRow(Downloads.Task task) {
        super(new BorderLayout(14, 0));
        this.task = task;
        pad(12, 14, 12, 14);
        fill(Theme.PANEL2);

        JLabel icon = new JLabel("\u21E9", SwingConstants.CENTER);
        icon.setFont(Theme.symbolFont(22f));
        icon.setForeground(Theme.ACCENT_HI);
        icon.setPreferredSize(new Dimension(48, 48));
        Widgets.RoundPanel iconBox = new Widgets.RoundPanel(new BorderLayout()).fill(new Color(0x1E3A2A)).pad(0, 0, 0, 0);
        iconBox.add(icon);
        iconBox.setPreferredSize(new Dimension(48, 48));
        add(iconBox, BorderLayout.WEST);

        title.setFont(Theme.font(Font.BOLD, 15f));
        info.setForeground(Theme.MUTED);
        bar.setPreferredSize(new Dimension(100, 8));
        JPanel mid = new JPanel();
        mid.setOpaque(false);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        title.setAlignmentX(LEFT_ALIGNMENT);
        bar.setAlignmentX(LEFT_ALIGNMENT);
        info.setAlignmentX(LEFT_ALIGNMENT);
        mid.add(title);
        mid.add(Box.createVerticalStrut(6));
        mid.add(bar);
        mid.add(Box.createVerticalStrut(5));
        mid.add(info);
        add(mid, BorderLayout.CENTER);

        pct.setFont(Theme.font(Font.BOLD, 14f));
        pct.setHorizontalAlignment(SwingConstants.RIGHT);
        pct.setPreferredSize(new Dimension(80, 20));
        cancel.addActionListener(e -> Downloads.cancel(task));
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        east.setOpaque(false);
        east.add(pct);
        east.add(cancel);
        add(east, BorderLayout.EAST);

        refresh();
    }

    public Downloads.Task task() { return task; }

    public void refresh() {
        Downloads.State st = task.state;
        title.setText(task.title);
        double f = task.fraction;
        bar.setForeground(st == Downloads.State.FAILED ? Theme.DANGER : Theme.ACCENT);
        if (st == Downloads.State.RUNNING && f < 0) {
            bar.setIndeterminate(true);
        } else {
            bar.setIndeterminate(false);
            bar.setValue((int) Math.round(Math.max(0, f) * 1000));
        }
        switch (st) {
            case RUNNING -> {
                pct.setText(f >= 0 ? Math.round(f * 100) + "%" : "…");
                double mb = task.bytes() / (1024.0 * 1024.0);
                info.setText(task.message + "  ·  " + String.format("%.0f MB geladen (%.1f MB/s)", mb, Downloads.speedMbPerSec()));
            }
            case DONE -> { pct.setText("Fertig"); info.setText(task.message); }
            case FAILED -> { pct.setText("Fehler"); info.setText(task.message); }
            default -> { pct.setText("Abgebrochen"); info.setText(task.message); }
        }
        cancel.setVisible(st == Downloads.State.RUNNING);
    }
}
