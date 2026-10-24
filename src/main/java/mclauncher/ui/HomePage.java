package mclauncher.ui;

import mclauncher.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Startseite: Titelbild, Spielen-Knopf, Auswahl, Neuigkeiten, Downloads, Schnellzugriff, Server. */
public class HomePage extends Page {
    private final Widgets.GlowButton playBtn = new Widgets.GlowButton("SPIELEN", Widgets.Style.PRIMARY);
    private final JComboBox<Instance> profileBox = new JComboBox<>();
    private final JComboBox<String> versionBox = new JComboBox<>();
    private final JComboBox<Loaders.Type> loaderBox = new JComboBox<>(Loaders.Type.values());
    private final JLabel hint = new JLabel(" ");
    private final JPanel newsRow = new JPanel(new GridLayout(1, 3, 12, 0));
    private final JPanel stripHolder = new JPanel(new BorderLayout());
    private final JPanel serverBox = new JPanel();
    private boolean updating;
    private Downloads.Task stripTask;
    private TaskRow stripRow;
    private List<String> releases = new ArrayList<>();
    private boolean newsLoaded;

    public HomePage(MainWindow win) {
        super(win);
        setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        setLayout(new BorderLayout(18, 0));

        // ---------------- linke Spalte
        Widgets.VPanel left = new Widgets.VPanel();

        HeroPanel hero = new HeroPanel("JLAUNCHER", "ENTDECKEN  \u00B7  BAUEN  \u00B7  \u00DCBERLEBEN");
        hero.setPreferredSize(new Dimension(100, 330));
        hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 330));
        playBtn.setFont(Theme.font(Font.BOLD, 24f));
        playBtn.setPreferredSize(new Dimension(360, 66));
        playBtn.arc(18);
        JPanel playWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        playWrap.setOpaque(false);
        playWrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 34, 0));
        playWrap.add(playBtn);
        hero.add(playWrap, BorderLayout.SOUTH);
        align(hero);
        left.add(hero);
        left.add(Box.createVerticalStrut(12));

        JPanel selectors = new JPanel(new BorderLayout(10, 0));
        selectors.setOpaque(false);
        JPanel three = new JPanel(new GridLayout(1, 3, 10, 0));
        three.setOpaque(false);
        three.add(selector("Version", versionBox));
        three.add(selector("Mod-Loader", loaderBox));
        three.add(selector("Profil", profileBox));
        selectors.add(three, BorderLayout.CENTER);
        Widgets.GlowButton gear = new Widgets.GlowButton("\u2699", Widgets.Style.DARK);
        gear.setFont(Theme.symbolFont(22f));
        gear.setPreferredSize(new Dimension(60, 60));
        gear.setToolTipText("Profil bearbeiten (Version, RAM, Java, Spielverzeichnis)");
        gear.addActionListener(e -> {
            Instance cur = AppState.current();
            if (cur == null) win.newProfile(); else win.editProfile(cur);
        });
        selectors.add(gear, BorderLayout.EAST);
        selectors.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
        align(selectors);
        left.add(selectors);
        hint.setForeground(Theme.MUTED);
        hint.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 0));
        align(hint);
        left.add(hint);
        left.add(Box.createVerticalStrut(10));

        left.add(sectionHeader("Neuigkeiten", null));
        newsRow.setOpaque(false);
        newsRow.setPreferredSize(new Dimension(100, 150));
        newsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        newsRow.add(placeholderCard("Lade Neuigkeiten …"));
        align(newsRow);
        left.add(newsRow);
        left.add(Box.createVerticalStrut(14));

        left.add(sectionHeader("Downloads", "downloads"));
        stripHolder.setOpaque(false);
        stripHolder.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        align(stripHolder);
        left.add(stripHolder);

        add(Widgets.scroll(left), BorderLayout.CENTER);

        // ---------------- rechte Spalte
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setPreferredSize(new Dimension(300, 100));

        Widgets.RoundPanel quick = new Widgets.RoundPanel(new BorderLayout(0, 10));
        quick.add(Widgets.title("Schnellzugriff", 18f), BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridLayout(2, 2, 8, 8));
        grid.setOpaque(false);
        grid.add(tile("Versionen", "versions"));
        grid.add(tile("Mods", "mods"));
        grid.add(tile("Ressourcen", "resourcepacks"));
        grid.add(tile("Shader", "shaders"));
        quick.add(grid, BorderLayout.CENTER);
        quick.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        quick.setAlignmentX(LEFT_ALIGNMENT);
        right.add(quick);
        right.add(Box.createVerticalStrut(14));

        Widgets.RoundPanel servers = new Widgets.RoundPanel(new BorderLayout(0, 10)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        JPanel sh = new JPanel(new BorderLayout());
        sh.setOpaque(false);
        sh.add(Widgets.title("Server", 18f), BorderLayout.WEST);
        Widgets.GlowButton more = new Widgets.GlowButton("Mehr anzeigen \u2192", Widgets.Style.GHOST);
        more.addActionListener(e -> win.navigate("servers"));
        sh.add(more, BorderLayout.EAST);
        servers.add(sh, BorderLayout.NORTH);
        serverBox.setOpaque(false);
        serverBox.setLayout(new BoxLayout(serverBox, BoxLayout.Y_AXIS));
        servers.add(serverBox, BorderLayout.CENTER);
        servers.setAlignmentX(LEFT_ALIGNMENT);
        right.add(servers);
        right.add(Box.createVerticalGlue());
        add(right, BorderLayout.EAST);

        // ---------------- Verhalten
        profileBox.addActionListener(e -> {
            if (updating) return;
            Instance sel = (Instance) profileBox.getSelectedItem();
            if (sel != null && sel != AppState.current()) SwingUtilities.invokeLater(() -> AppState.setCurrent(sel));
        });
        versionBox.addActionListener(e -> {
            Instance cur = AppState.current();
            if (updating || cur == null || versionBox.getSelectedItem() == null) return;
            changeSelection(cur, (String) versionBox.getSelectedItem(), cur.type());
        });
        loaderBox.addActionListener(e -> {
            Instance cur = AppState.current();
            if (updating || cur == null || loaderBox.getSelectedItem() == null) return;
            changeSelection(cur, cur.mcVersion, (Loaders.Type) loaderBox.getSelectedItem());
        });
        playBtn.addActionListener(e -> {
            if (GameRunner.state() == GameRunner.State.IDLE) {
                Instance cur = AppState.current();
                if (cur == null) win.newProfile(); else win.play(cur, null);
            } else {
                GameRunner.stop();
            }
        });

        GameRunner.addStateListener(() -> SwingUtilities.invokeLater(this::updatePlayButton));
        Downloads.addListener(() -> SwingUtilities.invokeLater(this::refreshStrip));
        new Timer(500, e -> { if (isShowing()) refreshStrip(); }).start();

        Util.async(() -> {
            try {
                List<String> ids = new ArrayList<>();
                for (Versions.Entry v : Versions.manifest()) if ("release".equals(v.type())) ids.add(v.id());
                SwingUtilities.invokeLater(() -> { releases = ids; refreshSelectors(); });
            } catch (Exception ignored) { }
        });

        refreshSelectors();
        refreshStrip();
        rebuildServers();
    }

    @Override
    public void onShow() {
        refreshSelectors();
        refreshStrip();
        rebuildServers();
        ServerPing.refreshAll(() -> SwingUtilities.invokeLater(this::rebuildServers));
        if (!newsLoaded) loadNews();
    }

    @Override
    protected void onProfileChanged() {
        refreshSelectors();
    }

    // ------------------------------------------------------------------ Auswahl

    private void refreshSelectors() {
        updating = true;
        Instance cur = AppState.current();
        profileBox.removeAllItems();
        for (Instance i : Store.data.instances) profileBox.addItem(i);
        if (cur != null) profileBox.setSelectedItem(cur);

        versionBox.removeAllItems();
        List<String> items = new ArrayList<>(releases);
        if (cur != null && !items.contains(cur.mcVersion)) items.add(0, cur.mcVersion);
        for (String v : items) versionBox.addItem(v);
        if (cur != null) versionBox.setSelectedItem(cur.mcVersion);
        loaderBox.setSelectedItem(cur == null ? Loaders.Type.VANILLA : cur.type());
        versionBox.setEnabled(cur != null);
        loaderBox.setEnabled(cur != null);
        updating = false;
        updatePlayButton();
    }

    /** Wechselt Version und/oder Loader des aktuellen Profils (Loader-Version = neueste verfügbare). */
    private void changeSelection(Instance inst, String mc, Loaders.Type type) {
        if (mc.equals(inst.mcVersion) && type == inst.type()) return;
        if (GameRunner.state() != GameRunner.State.IDLE) {
            hint.setText("Während des Spielens kann die Version nicht geändert werden.");
            refreshSelectors();
            return;
        }
        if (type == Loaders.Type.VANILLA) {
            apply(inst, mc, type, "");
            return;
        }
        hint.setText("Suche " + type.label + "-Version für " + mc + " …");
        Util.async(() -> {
            try {
                List<String> versions = Loaders.versions(type, mc);
                SwingUtilities.invokeLater(() -> {
                    if (versions.isEmpty()) {
                        hint.setText(type.label + " ist für Minecraft " + mc + " nicht verfügbar.");
                        refreshSelectors();
                    } else {
                        apply(inst, mc, type, versions.get(0));
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    hint.setText("Fehler: " + ex.getMessage());
                    refreshSelectors();
                });
            }
        });
    }

    private void apply(Instance inst, String mc, Loaders.Type type, String loaderVersion) {
        inst.mcVersion = mc;
        inst.loader = type.name();
        inst.loaderVersion = loaderVersion;
        inst.versionId = null;
        Store.save();
        hint.setText("Profil \"" + inst.name + "\": " + inst.label()
                + (loaderVersion.isBlank() ? "" : " (" + type.label + " " + loaderVersion + ")"));
        AppState.fire();
    }

    private void updatePlayButton() {
        switch (GameRunner.state()) {
            case IDLE -> {
                playBtn.setText(Store.data.instances.isEmpty() ? "PROFIL ERSTELLEN" : "SPIELEN");
                playBtn.style(Widgets.Style.PRIMARY);
                playBtn.setEnabled(true);
            }
            case PREPARING -> {
                playBtn.setText("ABBRECHEN \u2013 wird vorbereitet …");
                playBtn.style(Widgets.Style.DARK);
                playBtn.setEnabled(true);
            }
            default -> {
                playBtn.setText("BEENDEN");
                playBtn.style(Widgets.Style.DANGER);
                playBtn.setEnabled(true);
            }
        }
    }

    // ------------------------------------------------------------------ Downloads-Leiste

    private void refreshStrip() {
        Downloads.Task latest = null;
        for (Downloads.Task t : Downloads.tasks()) {
            if (t.state == Downloads.State.RUNNING) { latest = t; break; }
            latest = t;
        }
        if (latest == null) {
            if (stripTask != null || stripHolder.getComponentCount() == 0) {
                stripHolder.removeAll();
                stripHolder.add(placeholderCard("Keine aktiven Downloads"), BorderLayout.CENTER);
                stripTask = null;
                stripRow = null;
                stripHolder.revalidate();
                stripHolder.repaint();
            }
            return;
        }
        if (latest != stripTask) {
            stripHolder.removeAll();
            stripRow = new TaskRow(latest);
            stripHolder.add(stripRow, BorderLayout.CENTER);
            stripTask = latest;
            stripHolder.revalidate();
            stripHolder.repaint();
        } else if (stripRow != null) {
            stripRow.refresh();
        }
    }

    // ------------------------------------------------------------------ Server

    private void rebuildServers() {
        serverBox.removeAll();
        List<Store.ServerEntry> list = Store.data.servers;
        if (list.isEmpty()) {
            serverBox.add(Widgets.muted("<html>Noch keine Server gespeichert.<br>Füge welche unter \"Server\" hinzu.</html>"));
        }
        for (int i = 0; i < Math.min(5, list.size()); i++) {
            Store.ServerEntry s = list.get(i);
            ServerPing.Result r = ServerPing.cached(s.address);
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
            JLabel name = new JLabel(s.name.isBlank() ? s.address : s.name);
            name.setFont(Theme.font(Font.BOLD, 14f));
            row.add(name, BorderLayout.CENTER);
            JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            east.setOpaque(false);
            JLabel players = new JLabel(r == null ? "…" : r.online() ? r.players() + " / " + r.max() : "offline");
            players.setForeground(Theme.MUTED);
            Widgets.SignalBars bars = new Widgets.SignalBars();
            bars.setLevel(r == null ? 0 : r.signalLevel());
            east.add(players);
            east.add(bars);
            row.add(east, BorderLayout.EAST);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            serverBox.add(row);
        }
        serverBox.revalidate();
        serverBox.repaint();
    }

    // ------------------------------------------------------------------ Neuigkeiten

    private void loadNews() {
        newsLoaded = true;
        Util.async(() -> {
            try {
                List<News.Item> items = News.latest(3);
                SwingUtilities.invokeLater(() -> showNews(items));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    newsRow.removeAll();
                    newsRow.add(placeholderCard("Neuigkeiten konnten nicht geladen werden."));
                    newsRow.revalidate();
                    newsRow.repaint();
                });
            }
        });
    }

    private void showNews(List<News.Item> items) {
        newsRow.removeAll();
        if (items.isEmpty()) newsRow.add(placeholderCard("Keine Neuigkeiten."));
        for (News.Item it : items) {
            Widgets.RoundPanel card = new Widgets.RoundPanel(new BorderLayout(0, 6)).pad(10, 12, 10, 12);
            JLabel img = new JLabel("", SwingConstants.CENTER);
            img.setPreferredSize(new Dimension(100, 56));
            card.add(img, BorderLayout.NORTH);
            JLabel text = new JLabel("<html><body style='width:190px'><b>" + esc(it.title()) + "</b><br>"
                    + "<span style='color:#8B9C91'>" + esc(it.subtitle()) + "</span></body></html>");
            text.setVerticalAlignment(SwingConstants.TOP);
            card.add(text, BorderLayout.CENTER);
            newsRow.add(card);
            if (it.imageUrl() != null) {
                Util.async(() -> {
                    try {
                        BufferedImage bi = ImageIO.read(URI.create(it.imageUrl()).toURL());
                        if (bi == null) return;
                        Image scaled = bi.getScaledInstance(-1, 56, Image.SCALE_SMOOTH);
                        SwingUtilities.invokeLater(() -> img.setIcon(new ImageIcon(scaled)));
                    } catch (Exception ignored) { }
                });
            }
        }
        newsRow.revalidate();
        newsRow.repaint();
    }

    // ------------------------------------------------------------------ Bausteine

    private static Widgets.RoundPanel placeholderCard(String text) {
        Widgets.RoundPanel p = new Widgets.RoundPanel(new BorderLayout());
        JLabel l = Widgets.muted(text);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        p.add(l);
        return p;
    }

    private static JComponent selector(String label, JComboBox<?> box) {
        Widgets.RoundPanel p = new Widgets.RoundPanel(new BorderLayout(0, 0)).pad(8, 14, 8, 8);
        JLabel l = Widgets.muted(label);
        l.setFont(Theme.font(Font.PLAIN, 12f));
        box.setFont(Theme.font(Font.BOLD, 15f));
        p.add(l, BorderLayout.NORTH);
        p.add(box, BorderLayout.CENTER);
        return p;
    }

    private JComponent tile(String text, String page) {
        Widgets.GlowButton b = new Widgets.GlowButton(text, Widgets.Style.DARK);
        b.setFont(Theme.font(Font.BOLD, 13f));
        b.addActionListener(e -> win.navigate(page));
        return b;
    }

    private JComponent sectionHeader(String title, String page) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(Widgets.title(title, 18f), BorderLayout.WEST);
        if (page != null) {
            Widgets.GlowButton b = new Widgets.GlowButton("Mehr anzeigen \u2192", Widgets.Style.GHOST);
            b.addActionListener(e -> win.navigate(page));
            p.add(b, BorderLayout.EAST);
        }
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        align(p);
        return p;
    }

    private static void align(JComponent c) { c.setAlignmentX(LEFT_ALIGNMENT); }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
