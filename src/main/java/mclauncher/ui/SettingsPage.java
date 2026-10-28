package mclauncher.ui;

import mclauncher.AppPaths;
import mclauncher.I18n;
import mclauncher.Restart;
import mclauncher.Instance;
import mclauncher.JavaFinder;
import mclauncher.Store;
import mclauncher.Util;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Einstellungen: Standardwerte für neue Profile, Java, Konto, Daten. */
public class SettingsPage extends Page {
    private final JLabel javaList = new JLabel(I18n.t("Suche …"));
    private final JLabel currentProfile = new JLabel();
    private final JSpinner profFps = new JSpinner(new SpinnerNumberModel(0, 0, 260, 5));
    private boolean syncing;
    private final Timer saveTimer = new Timer(400, e -> Store.save());

    public SettingsPage(MainWindow win) {
        super(win);
        saveTimer.setRepeats(false);
        add(header(I18n.t("Einstellungen"), I18n.t("Launcher, Standardwerte und Java")), BorderLayout.NORTH);

        Widgets.VPanel col = new Widgets.VPanel();
        Store.Config cfg = Store.data.config;

        // ---- Allgemein
        JCheckBox minimize = new JCheckBox(I18n.t("Launcher beim Spielstart minimieren"));
        minimize.setOpaque(false);
        minimize.setSelected(cfg.minimizeOnLaunch);
        minimize.addActionListener(e -> { cfg.minimizeOnLaunch = minimize.isSelected(); saveTimer.restart(); });
        col.add(card(I18n.t("Allgemein"), minimize));

        // ---- Aussehen und Sprache
        JPanel scenes = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        scenes.setOpaque(false);
        Map<HeroPanel.Scene, Widgets.RoundPanel> sceneCards = new EnumMap<>(HeroPanel.Scene.class);
        Runnable mark = () -> {
            for (Map.Entry<HeroPanel.Scene, Widgets.RoundPanel> en : sceneCards.entrySet()) {
                en.getValue().line(en.getKey().name().equals(cfg.heroScene) ? Theme.ACCENT : Theme.BORDER);
            }
        };
        for (HeroPanel.Scene sc : HeroPanel.Scene.values()) {
            Widgets.RoundPanel card = new Widgets.RoundPanel(new BorderLayout(0, 6)).fill(Theme.PANEL2).pad(8, 8, 8, 8).arc(14);
            HeroPanel preview = new HeroPanel(sc);
            preview.setPreferredSize(new Dimension(150, 84));
            JLabel lbl = new JLabel(sc.label(), SwingConstants.CENTER);
            card.add(preview, BorderLayout.CENTER);
            card.add(lbl, BorderLayout.SOUTH);
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter pick = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (sc == HeroPanel.Scene.CUSTOM) {
                        JFileChooser fc = new JFileChooser();
                        fc.setFileFilter(new FileNameExtensionFilter(I18n.t("Bilder (PNG, JPG, GIF, BMP)"), "png", "jpg", "jpeg", "gif", "bmp"));
                        if (fc.showOpenDialog(SettingsPage.this) != JFileChooser.APPROVE_OPTION) return;
                        cfg.heroImage = fc.getSelectedFile().getAbsolutePath();
                    }
                    cfg.heroScene = sc.name();
                    Store.save();
                    HeroPanel.refreshAll();
                    mark.run();
                }
            };
            card.addMouseListener(pick);
            preview.addMouseListener(pick);
            sceneCards.put(sc, card);
            scenes.add(card);
        }
        mark.run();

        JComboBox<String> langBox = new JComboBox<>(new String[]{"Deutsch", "English"});
        langBox.setSelectedIndex(I18n.english() ? 1 : 0);
        langBox.addActionListener(e -> {
            String code = langBox.getSelectedIndex() == 1 ? "en" : "de";
            if (code.equals(I18n.code())) return;
            cfg.language = code;
            Store.save();
            int r = JOptionPane.showConfirmDialog(this, "Neustart erforderlich / Restart required\n\nJetzt neu starten? / Restart now?",
                    "Sprache / Language", JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) {
                if (Restart.restart()) System.exit(0);
                JOptionPane.showMessageDialog(this, "Bitte starte den Launcher manuell neu. / Please restart the launcher manually.");
            }
        });
        JPanel langRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        langRow.setOpaque(false);
        langRow.add(new JLabel("Sprache / Language"));
        langRow.add(langBox);
        JPanel lookBox = new JPanel(new BorderLayout(0, 12));
        lookBox.setOpaque(false);
        lookBox.add(scenes, BorderLayout.CENTER);
        lookBox.add(langRow, BorderLayout.SOUTH);
        col.add(Box.createVerticalStrut(12));
        col.add(card(I18n.t("Titelbild und Sprache"), lookBox));

        // ---- Standardwerte
        int total = Widgets.totalMemoryMb();
        Widgets.RamControl ramMin = new Widgets.RamControl(512, total, cfg.defaultRamMin);
        Widgets.RamControl ramMax = new Widgets.RamControl(1024, total, cfg.defaultRamMax);
        ramMin.addChangeListener(e -> {
            if (ramMin.getMb() > ramMax.getMb()) ramMax.setMb(ramMin.getMb());
            cfg.defaultRamMin = ramMin.getMb();
            cfg.defaultRamMax = ramMax.getMb();
            saveTimer.restart();
        });
        ramMax.addChangeListener(e -> {
            if (ramMax.getMb() < ramMin.getMb()) ramMin.setMb(ramMax.getMb());
            cfg.defaultRamMin = ramMin.getMb();
            cfg.defaultRamMax = ramMax.getMb();
            saveTimer.restart();
        });
        JTextField javaField = new JTextField(cfg.defaultJavaPath);
        javaField.putClientProperty("JTextField.placeholderText", I18n.t("automatisch – passende Version wird gesucht"));
        javaField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void upd() { cfg.defaultJavaPath = javaField.getText().trim(); saveTimer.restart(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { upd(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { upd(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { upd(); }
        });
        JButton browse = new Widgets.GlowButton("…", Widgets.Style.DARK);
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) javaField.setText(fc.getSelectedFile().getAbsolutePath());
        });
        JPanel javaRow = new JPanel(new BorderLayout(6, 0));
        javaRow.setOpaque(false);
        javaRow.add(javaField, BorderLayout.CENTER);
        javaRow.add(browse, BorderLayout.EAST);
        javaList.setForeground(Theme.MUTED);
        Widgets.GlowButton rescan = new Widgets.GlowButton(I18n.t("Java neu suchen"), Widgets.Style.DARK);
        rescan.addActionListener(e -> scanJava());

        JPanel defaults = new JPanel(new GridBagLayout());
        defaults.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 0, 5, 12);
        c.fill = GridBagConstraints.HORIZONTAL;
        row(defaults, c, 0, I18n.t("RAM minimal"), ramMin);
        row(defaults, c, 1, I18n.t("RAM maximal"), ramMax);
        row(defaults, c, 2, I18n.t("Java-Pfad"), javaRow);
        JSpinner defFps = new JSpinner(new SpinnerNumberModel(Math.max(0, Math.min(260, cfg.defaultFpsLimit)), 0, 260, 5));
        defFps.addChangeListener(e -> {
            int v = (Integer) defFps.getValue();
            cfg.defaultFpsLimit = v > 0 && v < 10 ? 10 : v;
            saveTimer.restart();
        });
        JPanel fpsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        fpsRow.setOpaque(false);
        fpsRow.add(defFps);
        fpsRow.add(Widgets.muted(I18n.t("0 = nicht ändern, 260 = unbegrenzt")));
        row(defaults, c, 3, "FPS-Limit", fpsRow);
        c.gridx = 1; c.gridy = 4; c.weightx = 1;
        defaults.add(javaList, c);
        c.gridy = 5; c.fill = GridBagConstraints.NONE; c.anchor = GridBagConstraints.WEST;
        defaults.add(rescan, c);
        col.add(Box.createVerticalStrut(12));
        col.add(card(I18n.t("Standard für neue Profile"), defaults));

        // ---- Aktuelles Profil
        Widgets.GlowButton editProfile = new Widgets.GlowButton(I18n.t("Profil bearbeiten …"), Widgets.Style.DARK);
        editProfile.addActionListener(e -> {
            Instance cur = AppState.current();
            if (cur != null) win.editProfile(cur); else win.newProfile();
        });
        JPanel prof = new JPanel(new BorderLayout(12, 0));
        prof.setOpaque(false);
        prof.add(currentProfile, BorderLayout.CENTER);
        prof.add(editProfile, BorderLayout.EAST);
        JPanel fpsLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        fpsLine.setOpaque(false);
        fpsLine.add(new JLabel("FPS-Limit"));
        fpsLine.add(profFps);
        fpsLine.add(Widgets.muted(I18n.t("0 = nicht ändern, 260 = unbegrenzt (gilt beim nächsten Start)")));
        prof.add(fpsLine, BorderLayout.SOUTH);
        profFps.addChangeListener(e -> {
            Instance cur = AppState.current();
            if (syncing || cur == null) return;
            int v = (Integer) profFps.getValue();
            cur.fpsLimit = v > 0 && v < 10 ? 10 : v;
            saveTimer.restart();
        });
        col.add(Box.createVerticalStrut(12));
        col.add(card(I18n.t("Aktuelles Profil (Spielverzeichnis, RAM, Java, Version)"), prof));

        // ---- CurseForge
        JTextField cfKey = new JTextField(cfg.curseForgeKey);
        cfKey.putClientProperty("JTextField.placeholderText", I18n.t("CurseForge-API-Schlüssel einfügen"));
        cfKey.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void upd() { cfg.curseForgeKey = cfKey.getText().trim(); saveTimer.restart(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { upd(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { upd(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { upd(); }
        });
        Widgets.GlowButton cfLink = new Widgets.GlowButton(I18n.t("Schlüssel beantragen (kostenlos)"), Widgets.Style.DARK);
        cfLink.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(java.net.URI.create("https://console.curseforge.com/"));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, I18n.t("Öffne https://console.curseforge.com/ im Browser."));
            }
        });
        JPanel cf = new JPanel(new BorderLayout(0, 8));
        cf.setOpaque(false);
        cf.add(Widgets.muted(fit(I18n.t("<html>Damit kannst du Mods auch von CurseForge installieren. Melde dich auf der Seite an und erstelle unter ")
                + I18n.t("\"API keys\" einen Schlüssel. Mods, deren Autor Downloads durch andere Programme nicht erlaubt, musst du manuell laden.</html>"))),
                BorderLayout.NORTH);
        cf.add(cfKey, BorderLayout.CENTER);
        JPanel cfBtn = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        cfBtn.setOpaque(false);
        cfBtn.add(cfLink);
        cf.add(cfBtn, BorderLayout.SOUTH);
        col.add(Box.createVerticalStrut(12));
        col.add(card("CurseForge", cf));

        // ---- Konto
        Widgets.GlowButton account = new Widgets.GlowButton(I18n.t("Konto verwalten …"), Widgets.Style.DARK);
        account.addActionListener(e -> win.openAccountDialog());
        col.add(Box.createVerticalStrut(12));
        col.add(card(I18n.t("Konto"), account));

        // ---- Updates
        JCheckBox autoCheck = new JCheckBox(I18n.t("Beim Start nach Updates suchen"));
        autoCheck.setOpaque(false);
        autoCheck.setSelected(cfg.checkUpdates);
        autoCheck.addActionListener(e -> { cfg.checkUpdates = autoCheck.isSelected(); saveTimer.restart(); });
        JTextField repoField = new JTextField(cfg.updateRepo);
        repoField.putClientProperty("JTextField.placeholderText", I18n.t("Benutzer/Repository, z. B. meinname/jlauncher"));
        repoField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void upd() { cfg.updateRepo = repoField.getText().trim(); saveTimer.restart(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { upd(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { upd(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { upd(); }
        });
        Widgets.GlowButton checkNow = new Widgets.GlowButton(I18n.t("Jetzt nach Updates suchen"), Widgets.Style.DARK);
        checkNow.addActionListener(e -> {
            cfg.updateRepo = repoField.getText().trim();
            Store.save();
            win.checkForUpdates(true);
        });
        JPanel upd = new JPanel(new GridLayout(0, 1, 0, 8));
        upd.setOpaque(false);
        upd.add(new JLabel(I18n.t("Installierte Version: ") + mclauncher.Updates.currentVersion()));
        upd.add(autoCheck);
        upd.add(repoField);
        JPanel updBtn = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        updBtn.setOpaque(false);
        updBtn.add(checkNow);
        upd.add(updBtn);
        col.add(Box.createVerticalStrut(12));
        col.add(card(I18n.t("Updates"), upd));

        // ---- Daten
        Widgets.GlowButton openData = new Widgets.GlowButton(I18n.t("Datenordner öffnen"), Widgets.Style.DARK);
        openData.addActionListener(e -> Widgets.openFolder(this, AppPaths.ROOT));
        JPanel data = new JPanel(new BorderLayout(12, 0));
        data.setOpaque(false);
        data.add(Widgets.muted(AppPaths.ROOT.toString()), BorderLayout.CENTER);
        data.add(openData, BorderLayout.EAST);
        col.add(Box.createVerticalStrut(12));
        col.add(card(I18n.t("Daten"), data));

        add(Widgets.scroll(col), BorderLayout.CENTER);
        scanJava();
        updateProfileLabel();
    }

    @Override public void onShow() { updateProfileLabel(); }
    @Override protected void onProfileChanged() { updateProfileLabel(); }

    private void updateProfileLabel() {
        Instance cur = AppState.current();
        syncing = true;
        profFps.setValue(cur == null ? 0 : Math.max(0, Math.min(260, cur.fpsLimit)));
        profFps.setEnabled(cur != null);
        syncing = false;
        currentProfile.setText(cur == null ? I18n.t("Kein Profil") : "<html><b>" + cur.name + "</b>  ·  " + cur.label()
                + "<br><span style='color:#8B9C91'>" + cur.dir() + "</span></html>");
    }

    private void scanJava() {
        javaList.setText(I18n.t("Suche …"));
        Util.async(() -> {
            List<JavaFinder.Found> found = JavaFinder.all();
            StringBuilder sb = new StringBuilder("<html>");
            if (found.isEmpty()) sb.append(I18n.t("Keine Java-Installation gefunden."));
            for (JavaFinder.Found f : found) sb.append("Java ").append(f.major()).append("  \u2013  ").append(f.home()).append("<br>");
            sb.append("</html>");
            SwingUtilities.invokeLater(() -> javaList.setText(sb.toString()));
        });
    }

    /** Gibt einem HTML-Text eine feste Breite, damit er umbricht statt abgeschnitten zu werden. */
    private static String fit(String html) {
        return html.replace("<html>", "<html><body style='width:640px'>");
    }

    private static void row(JPanel p, GridBagConstraints c, int row, String label, JComponent comp) {
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        p.add(new JLabel(label), c);
        c.gridx = 1; c.weightx = 1;
        p.add(comp, c);
    }

    private static JComponent card(String title, JComponent content) {
        Widgets.RoundPanel p = new Widgets.RoundPanel(new BorderLayout(0, 10)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        p.add(Widgets.title(title, 16f), BorderLayout.NORTH);
        p.add(content, BorderLayout.CENTER);
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }
}
