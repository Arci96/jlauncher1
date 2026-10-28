package mclauncher.ui;

import mclauncher.I18n;
import mclauncher.Downloads;
import mclauncher.GameRunner;
import mclauncher.Instance;
import mclauncher.Util;
import mclauncher.Worlds;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** Welten des aktuellen Profils: sichern, wiederherstellen, löschen. */
public class WorldsPage extends Page {
    private final DefaultListModel<Worlds.World> worldModel = new DefaultListModel<>();
    private final JList<Worlds.World> worldList = new JList<>(worldModel);
    private final DefaultListModel<Path> backupModel = new DefaultListModel<>();
    private final JList<Path> backupList = new JList<>(backupModel);
    private final JLabel status = new JLabel(" ");
    private final JPanel headerHolder = new JPanel(new BorderLayout());
    private int loadId;

    public WorldsPage(MainWindow win) {
        super(win);
        headerHolder.setOpaque(false);
        add(headerHolder, BorderLayout.NORTH);

        JPanel body = new JPanel(new GridLayout(1, 2, 16, 0));
        body.setOpaque(false);
        body.add(worldsCard());
        body.add(backupsCard());
        add(body, BorderLayout.CENTER);
        status.setForeground(Theme.MUTED);
        add(status, BorderLayout.SOUTH);
        reload();
    }

    @Override public void onShow() { reload(); }
    @Override protected void onProfileChanged() { if (isShowing()) reload(); }

    // ------------------------------------------------------------------ Aufbau

    private JComponent worldsCard() {
        Widgets.RoundPanel card = new Widgets.RoundPanel(new BorderLayout(0, 10));
        card.add(Widgets.title(I18n.t("Welten"), 18f), BorderLayout.NORTH);

        worldList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                Worlds.World w = (Worlds.World) v;
                setText("<html><b>" + esc(w.name()) + "</b><br><span style='color:gray'>" + fmtSize(w.size()) + I18n.t("  ·  zuletzt ")
                        + new SimpleDateFormat(I18n.t("dd.MM.yyyy HH:mm")).format(new Date(w.modified())) + "</span></html>");
                setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                return this;
            }
        });
        JScrollPane sp = new JScrollPane(worldList);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        card.add(sp, BorderLayout.CENTER);

        Widgets.GlowButton backup = new Widgets.GlowButton(I18n.t("Sichern"), Widgets.Style.PRIMARY);
        Widgets.GlowButton del = new Widgets.GlowButton(I18n.t("Löschen"), Widgets.Style.DANGER);
        Widgets.GlowButton open = new Widgets.GlowButton(I18n.t("Ordner"), Widgets.Style.DARK);
        backup.addActionListener(e -> backupSelected());
        del.addActionListener(e -> deleteSelected());
        open.addActionListener(e -> {
            Instance cur = AppState.current();
            if (cur != null) Widgets.openFolder(this, Worlds.savesDir(cur));
        });
        card.add(buttons(backup, del, open), BorderLayout.SOUTH);
        return card;
    }

    private JComponent backupsCard() {
        Widgets.RoundPanel card = new Widgets.RoundPanel(new BorderLayout(0, 10));
        card.add(Widgets.title("Backups", 18f), BorderLayout.NORTH);

        backupList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                Path p = (Path) v;
                setText("<html><b>" + esc(p.getFileName().toString().replace(".zip", "")) + "</b><br><span style='color:gray'>"
                        + fmtSize(p.toFile().length()) + "  ·  "
                        + new SimpleDateFormat(I18n.t("dd.MM.yyyy HH:mm")).format(new Date(p.toFile().lastModified())) + "</span></html>");
                setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                return this;
            }
        });
        JScrollPane sp = new JScrollPane(backupList);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        card.add(sp, BorderLayout.CENTER);

        Widgets.GlowButton restore = new Widgets.GlowButton(I18n.t("Wiederherstellen"), Widgets.Style.PRIMARY);
        Widgets.GlowButton del = new Widgets.GlowButton(I18n.t("Löschen"), Widgets.Style.DANGER);
        Widgets.GlowButton open = new Widgets.GlowButton(I18n.t("Ordner"), Widgets.Style.DARK);
        restore.addActionListener(e -> restoreSelected());
        del.addActionListener(e -> deleteBackup());
        open.addActionListener(e -> {
            Instance cur = AppState.current();
            if (cur != null) Widgets.openFolder(this, Worlds.backupDir(cur));
        });
        card.add(buttons(restore, del, open), BorderLayout.SOUTH);
        return card;
    }

    private static JPanel buttons(JComponent... c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        for (JComponent x : c) p.add(x);
        return p;
    }

    // ------------------------------------------------------------------ Laden

    private void reload() {
        Instance cur = AppState.current();
        headerHolder.removeAll();
        headerHolder.add(header(I18n.t("Welten"), cur == null ? I18n.t("Kein Profil ausgewählt") : I18n.t("Profil: ") + cur.name
                + I18n.t("  ·  Backups liegen außerhalb des Profils und bleiben auch nach dem Löschen des Profils erhalten")));
        headerHolder.revalidate();
        headerHolder.repaint();

        worldModel.clear();
        backupModel.clear();
        if (cur == null) return;
        int id = ++loadId;
        status.setText(I18n.t("Lade Welten …"));
        Util.async(() -> {
            try {
                List<Worlds.World> worlds = Worlds.list(cur);
                List<Path> backups = Worlds.backups(cur);
                SwingUtilities.invokeLater(() -> {
                    if (id != loadId) return;
                    worlds.forEach(worldModel::addElement);
                    backups.forEach(backupModel::addElement);
                    status.setText(worlds.size() + I18n.t(" Welten, ") + backups.size() + I18n.t(" Backups"));
                });
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> status.setText(I18n.t("Fehler: ") + ex.getMessage()));
            }
        });
    }

    // ------------------------------------------------------------------ Aktionen

    private boolean gameRunsOn(Instance inst) {
        if (GameRunner.state() != GameRunner.State.IDLE && GameRunner.running() == inst) {
            JOptionPane.showMessageDialog(this, I18n.t("Beende Minecraft zuerst. Während des Spielens können Welten nicht sicher gesichert oder verändert werden."));
            return true;
        }
        return false;
    }

    private void backupSelected() {
        Instance cur = AppState.current();
        Worlds.World w = worldList.getSelectedValue();
        if (cur == null || w == null || gameRunsOn(cur)) return;
        Downloads.start(I18n.t("Welt sichern: ") + w.name(), p -> Worlds.backup(cur, w, p),
                err -> SwingUtilities.invokeLater(() -> {
                    reload();
                    if (err != null) JOptionPane.showMessageDialog(this, err.getMessage(), I18n.t("Sichern fehlgeschlagen"), JOptionPane.ERROR_MESSAGE);
                }));
    }

    private void restoreSelected() {
        Instance cur = AppState.current();
        Path zip = backupList.getSelectedValue();
        if (cur == null || zip == null || gameRunsOn(cur)) return;
        Downloads.start(I18n.t("Backup wiederherstellen: ") + zip.getFileName(), p -> Worlds.restore(cur, zip, p),
                err -> SwingUtilities.invokeLater(() -> {
                    reload();
                    if (err != null) JOptionPane.showMessageDialog(this, err.getMessage(), I18n.t("Wiederherstellen fehlgeschlagen"), JOptionPane.ERROR_MESSAGE);
                }));
    }

    private void deleteSelected() {
        Instance cur = AppState.current();
        Worlds.World w = worldList.getSelectedValue();
        if (cur == null || w == null || gameRunsOn(cur)) return;
        int r = JOptionPane.showConfirmDialog(this,
                I18n.t("Welt \"") + w.name() + I18n.t("\" unwiderruflich löschen?\nTipp: Sichere sie vorher als Backup."),
                I18n.t("Welt löschen"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        Util.async(() -> {
            try {
                Worlds.delete(cur, w);
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, ex.getMessage(), I18n.t("Fehler"), JOptionPane.ERROR_MESSAGE));
            }
            SwingUtilities.invokeLater(this::reload);
        });
    }

    private void deleteBackup() {
        Path zip = backupList.getSelectedValue();
        if (zip == null) return;
        if (JOptionPane.showConfirmDialog(this, I18n.t("Backup \"") + zip.getFileName() + I18n.t("\" löschen?"), I18n.t("Backup löschen"),
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            java.nio.file.Files.deleteIfExists(zip);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), I18n.t("Fehler"), JOptionPane.ERROR_MESSAGE);
        }
        reload();
    }

    private static String fmtSize(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        return mb >= 1024 ? String.format("%.1f GB", mb / 1024) : String.format("%.1f MB", mb);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
