package mclauncher.ui;

import mclauncher.I18n;
import mclauncher.ContentType;
import mclauncher.CurseForge;
import mclauncher.Downloads;
import mclauncher.Instance;
import mclauncher.Loaders;
import mclauncher.Modrinth;
import mclauncher.Util;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.List;

/** Suche und Installation von Mods, Ressourcenpaketen und Shadern über Modrinth oder CurseForge. */
public class ContentBrowser extends JPanel {
    private enum Source {
        MODRINTH("Modrinth"), CURSEFORGE("CurseForge");
        final String label;
        Source(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private final Instance inst;
    private final ContentType type;
    private final Runnable onInstalled;

    private final JComboBox<Source> sourceBox = new JComboBox<>(Source.values());
    private final JTextField search = new JTextField();
    private final DefaultListModel<Modrinth.Hit> model = new DefaultListModel<>();
    private final JList<Modrinth.Hit> list = new JList<>(model);
    private final JButton searchBtn = new Widgets.GlowButton(I18n.t("Suchen"), Widgets.Style.DARK);
    private final JButton moreBtn = new Widgets.GlowButton(I18n.t("Mehr laden"), Widgets.Style.DARK);
    private final JButton installBtn = new Widgets.GlowButton(I18n.t("Installieren"), Widgets.Style.PRIMARY);
    private final JLabel status = new JLabel(" ");
    private int offset;
    private String lastQuery = "";
    private Source lastSource = Source.MODRINTH;
    private int searchId;

    public ContentBrowser(Instance inst, ContentType type, Runnable onInstalled) {
        super(new BorderLayout(0, 10));
        this.inst = inst;
        this.type = type;
        this.onInstalled = onInstalled;
        setOpaque(false);

        if (type == ContentType.MOD && inst.type() == Loaders.Type.VANILLA) {
            JLabel hintLabel = new JLabel(I18n.t("<html><div style='text-align:center'>Mods brauchen einen Mod-Loader.<br>")
                    + I18n.t("Ändere das Profil auf Fabric, Quilt, Forge oder NeoForge.</div></html>"), SwingConstants.CENTER);
            hintLabel.setForeground(Theme.MUTED);
            add(hintLabel, BorderLayout.CENTER);
            return;
        }

        search.putClientProperty("JTextField.placeholderText", type.label + I18n.t(" suchen …"));
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        top.add(sourceBox, BorderLayout.WEST);
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
        sourceBox.addActionListener(e -> runSearch(true));

        runSearch(true);   // beliebteste Einträge zeigen
    }

    private Source source() { return (Source) sourceBox.getSelectedItem(); }

    private void runSearch(boolean fresh) {
        if (fresh) {
            offset = 0;
            lastQuery = search.getText().trim();
            lastSource = source();
            model.clear();
        }
        final String query = lastQuery;
        final int off = offset;
        final Source src = lastSource;
        final int id = ++searchId;

        if (src == Source.CURSEFORGE && !CurseForge.hasKey()) {
            status.setText(I18n.t("CurseForge braucht einen API-Schlüssel (Einstellungen → CurseForge)."));
            moreBtn.setEnabled(false);
            return;
        }
        status.setText(I18n.t("Suche …"));
        searchBtn.setEnabled(false);
        moreBtn.setEnabled(false);
        Util.async(() -> {
            try {
                List<Modrinth.Hit> hits = src == Source.CURSEFORGE
                        ? CurseForge.search(query, inst, type, off)
                        : Modrinth.search(query, inst, type, off);
                SwingUtilities.invokeLater(() -> {
                    if (id != searchId) return;   // veraltete Antwort
                    hits.forEach(model::addElement);
                    offset += hits.size();
                    status.setText(model.isEmpty() ? I18n.t("Keine Treffer für ") + inst.label() : model.size() + I18n.t(" Treffer (") + src.label + ")");
                    searchBtn.setEnabled(true);
                    moreBtn.setEnabled(hits.size() >= 20);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    if (id != searchId) return;
                    status.setText(I18n.t("Fehler: ") + ex.getMessage());
                    searchBtn.setEnabled(true);
                });
            }
        });
    }

    private void install() {
        Modrinth.Hit hit = list.getSelectedValue();
        if (hit == null) return;
        Source src = lastSource;
        installBtn.setEnabled(false);
        status.setText(I18n.t("Installiere ") + hit.title() + I18n.t(" … (siehe Downloads)"));
        Downloads.start(hit.title() + I18n.t(" installieren (") + src.label + ")", p -> {
            p.update(I18n.t("Lade ") + hit.title(), -1);
            if (src == Source.CURSEFORGE) {
                CurseForge.install(hit.id(), inst, type, new HashSet<>(), msg -> p.update(msg, -1));
            } else {
                Modrinth.install(hit.id(), inst, type, new HashSet<>(), msg -> p.update(msg, -1));
            }
        }, err -> SwingUtilities.invokeLater(() -> {
            installBtn.setEnabled(list.getSelectedValue() != null);
            if (err == null) {
                status.setText(hit.title() + I18n.t(" wurde installiert."));
            } else {
                status.setText(I18n.t("Fehler: ") + err.getMessage());
                JOptionPane.showMessageDialog(this, err.getMessage(), I18n.t("Installation nicht möglich"), JOptionPane.WARNING_MESSAGE);
            }
            onInstalled.run();
        }));
    }

    private static class HitRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
            super.getListCellRendererComponent(l, v, i, sel, focus);
            Modrinth.Hit h = (Modrinth.Hit) v;
            setText("<html><b>" + esc(h.title()) + I18n.t("</b> <span style='color:gray'>von ") + esc(h.author())
                    + " · " + String.format("%,d", h.downloads()) + I18n.t(" Downloads</span><br>")
                    + "<span style='color:gray'>" + esc(h.description()) + "</span></html>");
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            return this;
        }

        private static String esc(String s) {
            return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
