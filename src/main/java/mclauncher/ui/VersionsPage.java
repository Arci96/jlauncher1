package mclauncher.ui;

import mclauncher.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Alle verfügbaren Minecraft-Versionen: herunterladen oder als neues Profil anlegen. */
public class VersionsPage extends Page {
    private final DefaultListModel<Versions.Entry> model = new DefaultListModel<>();
    private final JList<Versions.Entry> list = new JList<>(model);
    private final JTextField filter = new JTextField();
    private final JCheckBox snaps = new JCheckBox("Snapshots und alte Versionen");
    private final JLabel status = new JLabel(" ");
    private final Widgets.GlowButton downloadBtn = new Widgets.GlowButton("Herunterladen", Widgets.Style.DARK);
    private final Widgets.GlowButton profileBtn = new Widgets.GlowButton("Neues Profil mit dieser Version", Widgets.Style.PRIMARY);
    private List<Versions.Entry> all = new ArrayList<>();
    private boolean loading;

    public VersionsPage(MainWindow win) {
        super(win);
        add(header("Versionen", "Alle Minecraft-Versionen von Mojang"), BorderLayout.NORTH);

        Widgets.RoundPanel card = new Widgets.RoundPanel(new BorderLayout(0, 10));
        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setOpaque(false);
        filter.putClientProperty("JTextField.placeholderText", "Version suchen, z. B. 1.21");
        top.add(filter, BorderLayout.CENTER);
        top.add(snaps, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(40);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                super.getListCellRendererComponent(l, v, i, sel, foc);
                Versions.Entry e = (Versions.Entry) v;
                boolean installed = Files.exists(AppPaths.VERSIONS.resolve(e.id()).resolve(e.id() + ".jar"));
                setText("<html><b>" + e.id() + "</b> &nbsp;<span style='color:gray'>" + typeName(e.type()) + "</span>"
                        + (installed ? " &nbsp;<span style='color:#4ADE80'>\u2713 heruntergeladen</span>" : "") + "</html>");
                setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
                return this;
            }
        });
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        card.add(sp, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        bottom.setOpaque(false);
        status.setForeground(Theme.MUTED);
        bottom.add(status, BorderLayout.CENTER);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        btns.add(downloadBtn);
        btns.add(profileBtn);
        bottom.add(btns, BorderLayout.EAST);
        card.add(bottom, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);

        downloadBtn.setEnabled(false);
        profileBtn.setEnabled(false);
        list.addListSelectionListener(e -> {
            boolean has = list.getSelectedValue() != null;
            downloadBtn.setEnabled(has);
            profileBtn.setEnabled(has);
        });
        snaps.addActionListener(e -> applyFilter());
        filter.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        downloadBtn.addActionListener(e -> download());
        profileBtn.addActionListener(e -> createProfile());
    }

    @Override
    public void onShow() {
        if (all.isEmpty() && !loading) {
            loading = true;
            status.setText("Lade Versionsliste …");
            Util.async(() -> {
                try {
                    List<Versions.Entry> m = new ArrayList<>(Versions.manifest());
                    SwingUtilities.invokeLater(() -> { all = m; loading = false; applyFilter(); });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> { loading = false; status.setText("Fehler: " + ex.getMessage()); });
                }
            });
        } else {
            list.repaint();
        }
    }

    private void applyFilter() {
        String q = filter.getText().trim().toLowerCase();
        model.clear();
        for (Versions.Entry e : all) {
            if (!snaps.isSelected() && !"release".equals(e.type())) continue;
            if (!q.isEmpty() && !e.id().toLowerCase().contains(q)) continue;
            model.addElement(e);
        }
        status.setText(model.size() + " Versionen");
    }

    private void download() {
        Versions.Entry e = list.getSelectedValue();
        if (e == null) return;
        String id = e.id();
        status.setText("Download von " + id + " gestartet (siehe Downloads).");
        Downloads.start("Minecraft " + id + " herunterladen", p -> {
            Versions.installVanillaJson(id);
            Versions.installFiles(id, p);
        }, err -> SwingUtilities.invokeLater(() -> {
            list.repaint();
            status.setText(err == null ? "Minecraft " + id + " ist bereit." : "Fehler: " + err.getMessage());
        }));
    }

    private void createProfile() {
        Versions.Entry e = list.getSelectedValue();
        if (e == null) return;
        Instance i = new Instance();
        i.name = "Minecraft " + e.id();
        i.mcVersion = e.id();
        i.ramMin = Store.data.config.defaultRamMin;
        i.ramMax = Store.data.config.defaultRamMax;
        i.fpsLimit = Store.data.config.defaultFpsLimit;
        Store.data.instances.add(i);
        Store.save();
        AppState.setCurrent(i);
        win.navigate("home");
    }

    private static String typeName(String t) {
        if (t == null) return "";
        return switch (t) {
            case "release" -> "Release";
            case "snapshot" -> "Snapshot";
            case "old_beta" -> "Beta";
            case "old_alpha" -> "Alpha";
            default -> t;
        };
    }
}
