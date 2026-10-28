package mclauncher.ui;

import mclauncher.I18n;
import mclauncher.ContentType;
import mclauncher.Downloads;
import mclauncher.Instance;
import mclauncher.ModUpdates;
import mclauncher.Modrinth;
import mclauncher.Util;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Mods, Ressourcenpakete oder Shader des aktuellen Profils: installierte Dateien + Modrinth-Suche. */
public class ContentPage extends Page {
    private final ContentType type;
    private final DefaultListModel<Path> model = new DefaultListModel<>();
    private final JList<Path> list = new JList<>(model);
    private final JLabel countLabel = new JLabel();
    private Instance shown;
    private boolean dirty = true;

    public ContentPage(MainWindow win, ContentType type) {
        super(win);
        this.type = type;
    }

    @Override
    public void onShow() {
        if (dirty || shown != AppState.current()) rebuild();
    }

    @Override
    protected void onProfileChanged() {
        dirty = true;
        if (isShowing()) rebuild();
    }

    private void rebuild() {
        removeAll();
        dirty = false;
        Instance cur = AppState.current();
        shown = cur;

        Widgets.GlowButton open = new Widgets.GlowButton(I18n.t("Ordner öffnen"), Widgets.Style.DARK);
        open.addActionListener(e -> { if (cur != null) Widgets.openFolder(this, cur.contentDir(type)); });
        add(header(type.label, cur == null ? I18n.t("Kein Profil ausgewählt") : I18n.t("Profil: ") + cur.name + "  ·  " + cur.label(), open), BorderLayout.NORTH);

        if (cur == null) {
            JLabel l = Widgets.muted(I18n.t("Erstelle zuerst ein Profil."));
            l.setHorizontalAlignment(SwingConstants.CENTER);
            add(l, BorderLayout.CENTER);
        } else {
            JPanel body = new JPanel(new GridLayout(1, 2, 16, 0));
            body.setOpaque(false);
            body.add(installedCard(cur));
            body.add(browserCard(cur));
            add(body, BorderLayout.CENTER);
            refreshInstalled();
        }
        revalidate();
        repaint();
    }

    private JComponent installedCard(Instance cur) {
        Widgets.RoundPanel card = new Widgets.RoundPanel(new BorderLayout(0, 10));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Widgets.title(I18n.t("Installiert"), 18f), BorderLayout.WEST);
        countLabel.setForeground(Theme.MUTED);
        head.add(countLabel, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                Path p = (Path) v;
                String n = p.getFileName().toString();
                boolean off = Modrinth.isDisabled(p);
                setText(off ? n.substring(0, n.length() - ".disabled".length()) + I18n.t("   (deaktiviert)") : n);
                if (off && !s) setForeground(Theme.MUTED);
                setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                return this;
            }
        });
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        card.add(sp, BorderLayout.CENTER);

        Widgets.GlowButton toggle = new Widgets.GlowButton(I18n.t("Ein / Aus"), Widgets.Style.DARK);
        Widgets.GlowButton del = new Widgets.GlowButton(I18n.t("Entfernen"), Widgets.Style.DANGER);
        Widgets.GlowButton reload = new Widgets.GlowButton(I18n.t("Aktualisieren"), Widgets.Style.DARK);
        Widgets.GlowButton updates = new Widgets.GlowButton(I18n.t("Mod-Updates suchen"), Widgets.Style.DARK);
        updates.addActionListener(e -> checkUpdates(cur));
        toggle.addActionListener(e -> {
            Path p = list.getSelectedValue();
            if (p == null) return;
            try { Modrinth.toggle(p); } catch (IOException ex) { error(ex); }
            refreshInstalled();
        });
        del.addActionListener(e -> {
            Path p = list.getSelectedValue();
            if (p == null) return;
            if (JOptionPane.showConfirmDialog(this, p.getFileName() + I18n.t(" wirklich entfernen?"), I18n.t("Entfernen"),
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            try { Modrinth.delete(p); } catch (IOException ex) { error(ex); }
            refreshInstalled();
        });
        reload.addActionListener(e -> refreshInstalled());
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btns.setOpaque(false);
        btns.add(toggle);
        btns.add(del);
        btns.add(reload);
        if (type == ContentType.MOD) btns.add(updates);
        card.add(btns, BorderLayout.SOUTH);
        return card;
    }

    private JComponent browserCard(Instance cur) {
        Widgets.RoundPanel card = new Widgets.RoundPanel(new BorderLayout(0, 8));
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel t = Widgets.title(I18n.t("Suchen und installieren"), 18f);
        t.setAlignmentX(LEFT_ALIGNMENT);
        head.add(t);
        String note = switch (type) {
            case SHADER -> I18n.t("Shader brauchen Iris (Fabric/Quilt) oder OptiFine. Aktivierung im Spiel unter Videoeinstellungen.");
            case RESOURCEPACK -> I18n.t("Aktiviere Ressourcenpakete im Spiel unter Optionen \u2192 Ressourcenpakete.");
            default -> I18n.t("Quelle: Modrinth oder CurseForge. Pflicht-Abhängigkeiten (z. B. Fabric API) werden automatisch mitinstalliert.");
        };
        JLabel n = Widgets.muted("<html>" + note + "</html>");
        n.setAlignmentX(LEFT_ALIGNMENT);
        head.add(n);
        card.add(head, BorderLayout.NORTH);
        card.add(new ContentBrowser(cur, type, () -> SwingUtilities.invokeLater(this::refreshInstalled)), BorderLayout.CENTER);
        return card;
    }

    private void refreshInstalled() {
        model.clear();
        if (shown == null) return;
        try {
            for (Path p : Modrinth.installed(shown, type)) model.addElement(p);
        } catch (IOException ex) {
            error(ex);
        }
        countLabel.setText(model.size() + I18n.t(" Einträge"));
    }

    /** Prüft bei Modrinth auf neuere Versionen der installierten Mods und aktualisiert sie nach Rückfrage. */
    private void checkUpdates(Instance cur) {
        if (cur == null) return;
        countLabel.setText(I18n.t("Suche Updates …"));
        Util.async(() -> {
            try {
                List<ModUpdates.Update> ups = ModUpdates.check(cur);
                SwingUtilities.invokeLater(() -> {
                    refreshInstalled();
                    if (ups.isEmpty()) {
                        JOptionPane.showMessageDialog(this, I18n.t("Keine Updates gefunden.\n(Geprüft werden nur Mods, die auf Modrinth gefunden werden.)"));
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (ModUpdates.Update u : ups) {
                        sb.append(u.oldName()).append("\n    \u2192 ").append(u.newFileName()).append("\n");
                    }
                    JTextArea area = new JTextArea(sb.toString(), 12, 48);
                    area.setEditable(false);
                    int r = JOptionPane.showConfirmDialog(this, new JScrollPane(area),
                            ups.size() + I18n.t(" Update(s) verfügbar – jetzt installieren?"), JOptionPane.YES_NO_OPTION);
                    if (r != JOptionPane.YES_OPTION) return;
                    Downloads.start(ups.size() + I18n.t(" Mods aktualisieren (") + cur.name + ")",
                            p -> ModUpdates.apply(cur, ups, msg -> p.update(msg, -1)),
                            err -> SwingUtilities.invokeLater(() -> {
                                refreshInstalled();
                                if (err != null) error(new Exception(err.getMessage()));
                            }));
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    refreshInstalled();
                    error(ex);
                });
            }
        });
    }

    private void error(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), I18n.t("Fehler"), JOptionPane.ERROR_MESSAGE);
    }
}
