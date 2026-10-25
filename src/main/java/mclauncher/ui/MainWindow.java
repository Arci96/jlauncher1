package mclauncher.ui;

import mclauncher.ContentType;
import mclauncher.Downloads;
import mclauncher.GameRunner;
import mclauncher.Instance;
import mclauncher.Store;
import mclauncher.Util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Hauptfenster: Seitenleiste mit Navigation und Konto, rechts die jeweilige Seite. */
public class MainWindow extends JFrame {
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Map<String, Widgets.NavButton> nav = new HashMap<>();
    private final ButtonGroup group = new ButtonGroup();
    private final Widgets.Avatar avatar = new Widgets.Avatar(40);
    private final JLabel accountName = new JLabel();
    private final JLabel accountStatus = new JLabel();
    private GameRunner.State lastState = GameRunner.State.IDLE;

    public MainWindow() {
        super("JLauncher");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1280, scr.width - 40), Math.min(820, scr.height - 80));
        setMinimumSize(new Dimension(1000, 600));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        setContentPane(root);
        root.add(buildSidebar(), BorderLayout.WEST);
        content.setOpaque(false);
        root.add(content, BorderLayout.CENTER);

        addPage("home", new HomePage(this));
        addPage("profiles", new ProfilesPage(this));
        addPage("versions", new VersionsPage(this));
        addPage("mods", new ContentPage(this, ContentType.MOD));
        addPage("resourcepacks", new ContentPage(this, ContentType.RESOURCEPACK));
        addPage("shaders", new ContentPage(this, ContentType.SHADER));
        addPage("servers", new ServersPage(this));
        addPage("worlds", new WorldsPage(this));
        addPage("screenshots", new ScreenshotsPage(this));
        addPage("settings", new SettingsPage(this));
        addPage("downloads", new DownloadsPage(this));
        addPage("console", new ConsolePage(this));

        updateAccount();
        navigate("home");

        GameRunner.addStateListener(() -> SwingUtilities.invokeLater(this::onGameState));
        Downloads.addListener(() -> SwingUtilities.invokeLater(() ->
                nav.get("downloads").setBadge(Downloads.active())));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (GameRunner.state() != GameRunner.State.IDLE) {
                    int r = JOptionPane.showConfirmDialog(MainWindow.this,
                            "Minecraft läuft noch oder wird vorbereitet. Launcher trotzdem schließen?\n(Ein laufendes Spiel läuft weiter.)",
                            "Beenden", JOptionPane.YES_NO_OPTION);
                    if (r != JOptionPane.YES_OPTION) return;
                }
                Store.save();
                System.exit(0);
            }
        });
    }

    // ------------------------------------------------------------------ Aufbau

    private JComponent buildSidebar() {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Theme.SIDEBAR);
        side.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER),
                BorderFactory.createEmptyBorder(18, 14, 16, 14)));
        side.setPreferredSize(new Dimension(256, 100));

        JPanel logo = new JPanel(new BorderLayout(12, 0));
        logo.setOpaque(false);
        logo.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        logo.add(new Widgets.LogoIcon(), BorderLayout.WEST);
        JPanel t = new JPanel();
        t.setOpaque(false);
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        JLabel name = new JLabel("JLauncher");
        name.setFont(Theme.font(Font.BOLD, 21f));
        JLabel tag = Widgets.muted("Play \u00B7 Mod \u00B7 Explore");
        t.add(Box.createVerticalGlue());
        t.add(name);
        t.add(tag);
        t.add(Box.createVerticalGlue());
        logo.add(t, BorderLayout.CENTER);
        side.add(logo, BorderLayout.NORTH);

        // Navigation (scrollt, falls das Fenster niedrig ist)
        Widgets.VPanel navPanel = new Widgets.VPanel();
        addNav(navPanel, "home", "\u2302", "Startseite");
        addNav(navPanel, "profiles", "\u25B6", "Spielen");
        addNav(navPanel, "versions", "\u25C6", "Versionen");
        addNav(navPanel, "mods", "\u271A", "Mods");
        addNav(navPanel, "resourcepacks", "\u270E", "Ressourcenpakete");
        addNav(navPanel, "shaders", "\u25D0", "Shader");
        addNav(navPanel, "servers", "\u2630", "Server");
        addNav(navPanel, "worlds", "\u25CE", "Welten");
        addNav(navPanel, "screenshots", "\u25A3", "Screenshots");
        navPanel.add(Box.createVerticalStrut(8));
        JSeparator sep = new JSeparator();
        sep.setForeground(Theme.BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        navPanel.add(sep);
        navPanel.add(Box.createVerticalStrut(8));
        addNav(navPanel, "settings", "\u2699", "Einstellungen");
        addNav(navPanel, "downloads", "\u21E9", "Downloads");
        addNav(navPanel, "console", "\u2328", "Konsole");
        JScrollPane navScroll = Widgets.scroll(navPanel);
        navScroll.setBorder(null);
        side.add(navScroll, BorderLayout.CENTER);

        Widgets.RoundPanel account = new Widgets.RoundPanel(new BorderLayout(10, 0)).fill(Theme.PANEL).pad(10, 10, 10, 12);
        account.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        account.add(avatar, BorderLayout.WEST);
        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        accountName.setFont(Theme.font(Font.BOLD, 14f));
        accountStatus.setForeground(Theme.MUTED);
        texts.add(Box.createVerticalGlue());
        texts.add(accountName);
        texts.add(accountStatus);
        texts.add(Box.createVerticalGlue());
        account.add(texts, BorderLayout.CENTER);
        account.add(new JLabel("\u203A"), BorderLayout.EAST);
        account.setPreferredSize(new Dimension(100, 66));
        account.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { openAccountDialog(); }
        });
        JPanel accountWrap = new JPanel(new BorderLayout());
        accountWrap.setOpaque(false);
        accountWrap.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        accountWrap.add(account);
        side.add(accountWrap, BorderLayout.SOUTH);
        return side;
    }

    private void addNav(JPanel side, String key, String glyph, String text) {
        Widgets.NavButton b = new Widgets.NavButton(glyph, text);
        b.addActionListener(e -> navigate(key));
        group.add(b);
        nav.put(key, b);
        side.add(b);
        side.add(Box.createVerticalStrut(2));
    }

    private void addPage(String key, Page page) {
        pages.put(key, page);
        content.add(page, key);
    }

    // ------------------------------------------------------------------ Navigation und Aktionen

    public void navigate(String key) {
        Page p = pages.get(key);
        if (p == null) return;
        cards.show(content, key);
        Widgets.NavButton b = nav.get(key);
        if (b != null) b.setSelected(true);
        p.onShow();
    }

    public void play(Instance inst, String server) {
        GameRunner.play(inst, server, err -> SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, err.getMessage(), "Start fehlgeschlagen", JOptionPane.ERROR_MESSAGE);
            navigate("console");
        }));
    }

    public void newProfile() {
        Instance created = new InstanceDialog(this, null).showDialog();
        if (created == null) return;
        Store.data.instances.add(created);
        Store.save();
        AppState.setCurrent(created);
    }

    public void editProfile(Instance inst) {
        if (new InstanceDialog(this, inst).showDialog() == null) return;
        Store.save();
        AppState.fire();
    }

    public void deleteProfile(Instance inst) {
        String extra = inst.hasCustomDir()
                ? "\n\nDer eigene Ordner (" + inst.dir() + ") bleibt unangetastet."
                : "\n\nWelten und Mods dieses Profils werden unwiderruflich gelöscht.";
        int r = JOptionPane.showConfirmDialog(this, "Profil \"" + inst.name + "\" löschen?" + extra,
                "Löschen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        Store.data.instances.remove(inst);
        Store.save();
        if (!inst.hasCustomDir()) {
            Util.async(() -> {
                try {
                    Util.deleteRecursive(inst.dir());
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                            "Ordner konnte nicht vollständig gelöscht werden:\n" + ex.getMessage()));
                }
            });
        }
        AppState.setCurrent(null);
    }

    public void openAccountDialog() {
        new AccountDialog(this).setVisible(true);
        updateAccount();
    }

    private void updateAccount() {
        Store.Config c = Store.data.config;
        boolean ms = "microsoft".equals(c.accountType);
        String n = ms ? (c.msName == null || c.msName.isBlank() ? "Nicht angemeldet" : c.msName) : c.offlineName;
        accountName.setText(n);
        avatar.setName(n);
        accountStatus.setText(ms ? "\u25CF Microsoft" : "\u25CF Offline");
        accountStatus.setForeground(ms ? Theme.ACCENT : Theme.MUTED);
    }

    /** Minecraft wurde nicht normal beendet: Hinweis mit Weg zur Konsole und zu den Crash-Reports. */
    private void showCrashHint() {
        Instance inst = GameRunner.lastInstance();
        String[] options = {"Konsole öffnen", "Crash-Reports-Ordner", "Schließen"};
        int c = JOptionPane.showOptionDialog(this,
                "Minecraft wurde unerwartet beendet (Exit-Code " + GameRunner.lastExitCode() + ").\n"
                        + "Die Ursache steht meist in der Konsole oder in einem Crash-Report.\n"
                        + "Häufige Gründe: zu wenig RAM, falsche Java-Version, inkompatible Mods.",
                "Minecraft abgestürzt", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        if (c == 0) navigate("console");
        else if (c == 1 && inst != null) Widgets.openFolder(this, inst.dir().resolve("crash-reports"));
    }

    private void onGameState() {
        GameRunner.State s = GameRunner.state();
        if (s == GameRunner.State.RUNNING && Store.data.config.minimizeOnLaunch) {
            setState(Frame.ICONIFIED);
        } else if (s == GameRunner.State.IDLE && lastState == GameRunner.State.RUNNING) {
            setState(Frame.NORMAL);
            toFront();
            if (GameRunner.lastExitCode() != 0 && !GameRunner.stoppedByUser()) showCrashHint();
        }
        lastState = s;
    }
}
