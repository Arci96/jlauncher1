package mclauncher.ui;

import mclauncher.ContentType;
import mclauncher.Downloads;
import mclauncher.Instance;
import mclauncher.Loaders;
import mclauncher.Modrinth;
import mclauncher.Util;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.List;

/** Suche und Installation über Modrinth (Mods, Ressourcenpakete oder Shader). */
public class ModrinthPanel extends JPanel {
    private final Instance inst;
    private final ContentType type;
    private final Runnable onInstalled;

    private final JTextField search = new JTextField();
    private final DefaultListModel<Modrinth.Hit> model = new DefaultListModel<>();
    private final JList<Modrinth.Hit> list = new JList<>(model);
    private final JButton searchBtn = new Widgets.GlowButton("Suchen", Widgets.Style.DARK);
    private final JButton moreBtn = new Widgets.GlowButton("Mehr laden", Widgets.Style.DARK);
    private final JButton installBtn = new Widgets.GlowButton("Installieren", Widgets.Style.PRIMARY);
    private final JLabel status = new JLabel(" ");
    private int offset;
    private String lastQuery = "";

    public ModrinthPanel(Instance inst, ContentType type, Runnable onInstalled) {
        super(new BorderLayout(0, 10));
        this.inst = inst;
        this.type = type;
        this.onInstalled = onInstalled;
        setOpaque(false);

        if (type == ContentType.MOD && inst.type() == Loaders.Type.VANILLA) {
            JLabel hintLabel = new JLabel("<html><div style='text-align:center'>Mods brauchen einen Mod-Loader.<br>"
                    + "Ändere das Profil auf Fabric, Quilt, Forge oder NeoForge.</div></html>", SwingConstants.CENTER);
            hintLabel.setForeground(Theme.MUTED);
            add(hintLabel, BorderLayout.CENTER);
            return;
        }

        search.putClientProperty("JTextField.placeholderText", type.label + " auf Modrinth suchen …");
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        top.add(search, BorderLayout.CENTER);
        top.add(searchBtn, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        list.setCellRenderer(new HitRenderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        add(sp, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setOpaque(false);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        btns.add(moreBtn);
        btns.add(installBtn);
        status.setForeground(Theme.MUTED);
        bottom.add(status, BorderLayout.CENTER);
        bottom.add(btns, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        installBtn.setEnabled(false);
        moreBtn.setEnabled(false);
        list.addListSelectionListener(e -> installBtn.setEnabled(list.getSelectedValue() != null));
        search.addActionListener(e -> runSearch(true));
        searchBtn.addActionListener(e -> runSearch(true));
        moreBtn.addActionListener(e -> runSearch(false));
        installBtn.addActionListener(e -> install());

        runSearch(true);   // beliebteste Einträge zeigen
    }

    private void runSearch(boolean fresh) {
        if (fresh) {
            offset = 0;
            lastQuery = search.getText().trim();
        }
        final String query = lastQuery;
        final int off = offset;
        status.setText("Suche …");
        searchBtn.setEnabled(false);
        moreBtn.setEnabled(false);
        Util.async(() -> {
            try {
                List<Modrinth.Hit> hits = Modrinth.search(query, inst, type, off);
                SwingUtilities.invokeLater(() -> {
                    if (fresh) model.clear();
                    hits.forEach(model::addElement);
                    offset += hits.size();
                    status.setText(model.isEmpty() ? "Keine Treffer für " + inst.label() : model.size() + " Treffer");
                    searchBtn.setEnabled(true);
                    moreBtn.setEnabled(hits.size() >= 20);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    status.setText("Fehler: " + ex.getMessage());
                    searchBtn.setEnabled(true);
                });
            }
        });
    }

    private void install() {
        Modrinth.Hit hit = list.getSelectedValue();
        if (hit == null) return;
        installBtn.setEnabled(false);
        status.setText("Installiere " + hit.title() + " … (siehe Downloads)");
        Downloads.start(hit.title() + " installieren", p -> {
            p.update("Lade " + hit.title(), -1);
            Modrinth.install(hit.id(), inst, type, new HashSet<>(), msg -> p.update(msg, -1));
        }, err -> SwingUtilities.invokeLater(() -> {
            installBtn.setEnabled(list.getSelectedValue() != null);
            status.setText(err == null ? hit.title() + " wurde installiert." : "Fehler: " + err.getMessage());
            onInstalled.run();
        }));
    }

    private static class HitRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
            super.getListCellRendererComponent(l, v, i, sel, focus);
            Modrinth.Hit h = (Modrinth.Hit) v;
            setText("<html><b>" + esc(h.title()) + "</b> <span style='color:gray'>von " + esc(h.author())
                    + " · " + String.format("%,d", h.downloads()) + " Downloads</span><br>"
                    + "<span style='color:gray'>" + esc(h.description()) + "</span></html>");
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            return this;
        }

        private static String esc(String s) {
            return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
