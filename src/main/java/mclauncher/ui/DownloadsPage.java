package mclauncher.ui;

import mclauncher.I18n;
import mclauncher.Downloads;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fortschritt und Status aller Downloads und Installationen. */
public class DownloadsPage extends Page {
    private final Widgets.VPanel list = new Widgets.VPanel();
    private final Map<Downloads.Task, TaskRow> rows = new HashMap<>();
    private final JLabel empty = Widgets.muted(I18n.t("Keine Downloads."));

    public DownloadsPage(MainWindow win) {
        super(win);
        Widgets.GlowButton clear = new Widgets.GlowButton(I18n.t("Verlauf leeren"), Widgets.Style.DARK);
        clear.addActionListener(e -> Downloads.clearFinished());
        add(header("Downloads", I18n.t("Installationen, Downloads und Spielstart"), clear), BorderLayout.NORTH);
        add(Widgets.scroll(list), BorderLayout.CENTER);
        Downloads.addListener(() -> SwingUtilities.invokeLater(this::sync));
        new Timer(500, e -> { if (isShowing()) sync(); }).start();
        sync();
    }

    @Override public void onShow() { sync(); }

    private void sync() {
        List<Downloads.Task> tasks = new ArrayList<>(Downloads.tasks());
        boolean same = tasks.size() == rows.size() && rows.keySet().containsAll(tasks);
        if (!same) {
            list.removeAll();
            rows.clear();
            if (tasks.isEmpty()) {
                empty.setAlignmentX(LEFT_ALIGNMENT);
                list.add(empty);
            }
            for (int i = tasks.size() - 1; i >= 0; i--) {   // neueste zuerst
                TaskRow r = new TaskRow(tasks.get(i));
                r.setAlignmentX(LEFT_ALIGNMENT);
                r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));
                rows.put(tasks.get(i), r);
                list.add(r);
                list.add(Box.createVerticalStrut(10));
            }
            list.revalidate();
            list.repaint();
        } else {
            for (TaskRow r : rows.values()) r.refresh();
        }
    }
}
