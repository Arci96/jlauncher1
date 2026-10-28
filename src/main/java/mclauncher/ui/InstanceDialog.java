package mclauncher.ui;

import mclauncher.I18n;
import mclauncher.Instance;
import mclauncher.Loaders;
import mclauncher.Store;
import mclauncher.Util;
import mclauncher.Versions;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Dialog zum Erstellen und Bearbeiten eines Profils. */
public class InstanceDialog extends JDialog {
    private final boolean editing;
    private final Instance inst;
    private Instance result;

    private final JTextField nameField = new JTextField(24);
    private final JComboBox<String> mcBox = new JComboBox<>();
    private final JCheckBox snapshots = new JCheckBox(I18n.t("Snapshots anzeigen"));
    private final JComboBox<Loaders.Type> loaderBox = new JComboBox<>(Loaders.Type.values());
    private final JComboBox<String> loaderVersionBox = new JComboBox<>();
    private final Widgets.RamControl ramMin;
    private final Widgets.RamControl ramMax;
    private final JTextField javaField = new JTextField(24);
    private final JTextField jvmField = new JTextField(24);
    private final JTextField dirField = new JTextField(24);
    private final JSpinner fpsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 260, 5));
    private final JLabel hint = new JLabel(" ");

    private volatile int loadToken;
    private boolean adjusting;

    public InstanceDialog(Frame owner, Instance existing) {
        super(owner, existing == null ? I18n.t("Neues Profil") : I18n.t("Profil bearbeiten"), true);
        this.editing = existing != null;
        this.inst = existing != null ? existing : new Instance();
        if (!editing) {
            Store.Config cfg = Store.data.config;
            inst.ramMin = cfg.defaultRamMin;
            inst.ramMax = cfg.defaultRamMax;
            inst.fpsLimit = cfg.defaultFpsLimit;
        }

        int total = Widgets.totalMemoryMb();
        ramMin = new Widgets.RamControl(512, total, inst.ramMin);
        ramMax = new Widgets.RamControl(1024, total, inst.ramMax);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 4, 5, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        nameField.setText(inst.name);
        row = addRow(form, c, row, "Name", nameField);
        row = addRow(form, c, row, I18n.t("Minecraft-Version"), mcBox);
        c.gridx = 1; c.gridy = row++; c.weightx = 1; form.add(snapshots, c);
        row = addRow(form, c, row, I18n.t("Mod-Loader"), loaderBox);
        row = addRow(form, c, row, I18n.t("Loader-Version"), loaderVersionBox);
        c.gridx = 1; c.gridy = row++; form.add(hint, c);
        row = addRow(form, c, row, I18n.t("RAM minimal"), ramMin);
        row = addRow(form, c, row, I18n.t("RAM maximal"), ramMax);

        fpsSpinner.setValue(Math.max(0, Math.min(260, inst.fpsLimit)));
        JPanel fpsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        fpsRow.add(fpsSpinner);
        fpsRow.add(new JLabel(I18n.t("0 = Spiel-Einstellung behalten, 260 = unbegrenzt")));
        row = addRow(form, c, row, "FPS-Limit", fpsRow);

        javaField.setText(inst.javaPath);
        javaField.putClientProperty("JTextField.placeholderText", I18n.t("automatisch"));
        row = addRow(form, c, row, I18n.t("Java-Pfad"), withBrowse(javaField, false));

        jvmField.setText(inst.jvmArgs);
        jvmField.putClientProperty("JTextField.placeholderText", I18n.t("z. B. -XX:+UseG1GC"));
        JButton recommended = new JButton(I18n.t("Empfohlen"));
        recommended.setToolTipText(I18n.t("Setzt bewährte Speicherverwaltungs-Argumente (G1GC)"));
        recommended.addActionListener(e -> jvmField.setText("-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+DisableExplicitGC"));
        JPanel jvmRow = new JPanel(new BorderLayout(6, 0));
        jvmRow.add(jvmField, BorderLayout.CENTER);
        jvmRow.add(recommended, BorderLayout.EAST);
        row = addRow(form, c, row, I18n.t("JVM-Argumente"), jvmRow);

        dirField.setText(inst.gameDirOverride);
        dirField.putClientProperty("JTextField.placeholderText", I18n.t("Standardordner des Launchers"));
        row = addRow(form, c, row, I18n.t("Spielverzeichnis"), withBrowse(dirField, true));

        JButton ok = new Widgets.GlowButton(editing ? I18n.t("Speichern") : I18n.t("Erstellen"), Widgets.Style.PRIMARY);
        JButton cancel = new Widgets.GlowButton(I18n.t("Abbrechen"), Widgets.Style.DARK);
        ok.addActionListener(e -> onOk());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(cancel);
        buttons.add(ok);
        getRootPane().setDefaultButton(ok);

        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        ramMin.addChangeListener(e -> { if (ramMin.getMb() > ramMax.getMb()) ramMax.setMb(ramMin.getMb()); });
        ramMax.addChangeListener(e -> { if (ramMax.getMb() < ramMin.getMb()) ramMin.setMb(ramMax.getMb()); });

        if (editing) {
            mcBox.addItem(inst.mcVersion);
            loaderBox.setSelectedItem(inst.type());
        }
        snapshots.addActionListener(e -> loadMcVersions());
        mcBox.addActionListener(e -> loadLoaderVersions());
        loaderBox.addActionListener(e -> loadLoaderVersions());
        loadMcVersions();

        pack();
        setMinimumSize(new Dimension(560, getHeight()));
        setLocationRelativeTo(owner);
    }

    /** Zeigt den Dialog; gibt das Profil zurück oder null bei Abbruch. */
    public Instance showDialog() {
        setVisible(true);
        return result;
    }

    private JPanel withBrowse(JTextField field, boolean dirsOnly) {
        JButton browse = new JButton("…");
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (dirsOnly) fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) field.setText(fc.getSelectedFile().getAbsolutePath());
        });
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.add(field, BorderLayout.CENTER);
        p.add(browse, BorderLayout.EAST);
        return p;
    }

    // ------------------------------------------------------------------ Laden der Versionen

    private void loadMcVersions() {
        mcBox.setEnabled(false);
        Util.async(() -> {
            try {
                boolean snaps = snapshots.isSelected();
                List<String> ids = new ArrayList<>();
                for (Versions.Entry v : Versions.manifest()) {
                    if (snaps || "release".equals(v.type())) ids.add(v.id());
                }
                SwingUtilities.invokeLater(() -> {
                    Object prev = mcBox.getSelectedItem();
                    String want = prev != null ? prev.toString() : inst.mcVersion;
                    if (!ids.contains(want)) ids.add(0, want);   // z. B. Snapshot-Profil bearbeiten
                    adjusting = true;
                    mcBox.removeAllItems();
                    for (String id : ids) mcBox.addItem(id);
                    mcBox.setSelectedItem(want);
                    adjusting = false;
                    mcBox.setEnabled(true);
                    loadLoaderVersions();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> hint.setText(I18n.t("Versionsliste konnte nicht geladen werden: ") + ex.getMessage()));
            }
        });
    }

    private void loadLoaderVersions() {
        if (adjusting) return;
        Loaders.Type type = (Loaders.Type) loaderBox.getSelectedItem();
        String mc = (String) mcBox.getSelectedItem();
        loaderVersionBox.removeAllItems();
        if (type == null || type == Loaders.Type.VANILLA || mc == null) {
            loaderVersionBox.setEnabled(false);
            hint.setText(" ");
            return;
        }
        int token = ++loadToken;
        loaderVersionBox.setEnabled(false);
        hint.setText(I18n.t("Lade ") + type.label + I18n.t("-Versionen …"));
        Util.async(() -> {
            try {
                List<String> versions = new ArrayList<>(Loaders.versions(type, mc));
                SwingUtilities.invokeLater(() -> {
                    if (token != loadToken) return;   // veraltete Antwort
                    String keep = editing && type == inst.type() && mc.equals(inst.mcVersion) ? inst.loaderVersion : null;
                    if (keep != null && !keep.isBlank() && !versions.contains(keep)) versions.add(0, keep);
                    for (String v : versions) loaderVersionBox.addItem(v);
                    if (keep != null && !keep.isBlank()) loaderVersionBox.setSelectedItem(keep);
                    loaderVersionBox.setEnabled(!versions.isEmpty());
                    hint.setText(versions.isEmpty() ? type.label + I18n.t(" ist für ") + mc + I18n.t(" nicht verfügbar.") : " ");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    if (token == loadToken) hint.setText(I18n.t("Fehler: ") + ex.getMessage());
                });
            }
        });
    }

    // ------------------------------------------------------------------ OK

    private void onOk() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, I18n.t("Bitte gib einen Namen ein."), I18n.t("Hinweis"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        String mc = (String) mcBox.getSelectedItem();
        Loaders.Type type = (Loaders.Type) loaderBox.getSelectedItem();
        if (mc == null || type == null) {
            JOptionPane.showMessageDialog(this, I18n.t("Bitte wähle eine Minecraft-Version."), I18n.t("Hinweis"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        String lv = type == Loaders.Type.VANILLA ? "" : (String) loaderVersionBox.getSelectedItem();
        if (type != Loaders.Type.VANILLA && (lv == null || lv.isBlank())) {
            JOptionPane.showMessageDialog(this, I18n.t("Für diese Kombination gibt es keine Loader-Version."), I18n.t("Hinweis"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean changed = !mc.equals(inst.mcVersion) || type != inst.type() || !lv.equals(inst.loaderVersion == null ? "" : inst.loaderVersion);
        if (editing && changed && JOptionPane.showConfirmDialog(this,
                I18n.t("Version oder Loader ändern?\nBereits installierte Mods passen eventuell nicht mehr."),
                I18n.t("Änderung bestätigen"), JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        if (changed || !editing) {
            inst.mcVersion = mc;
            inst.loader = type.name();
            inst.loaderVersion = lv;
            inst.versionId = null;   // wird beim nächsten Start neu aufgelöst
        }
        inst.name = name;
        inst.ramMin = ramMin.getMb();
        inst.ramMax = Math.max(ramMax.getMb(), inst.ramMin);
        inst.javaPath = javaField.getText().trim();
        inst.jvmArgs = jvmField.getText().trim();
        inst.gameDirOverride = dirField.getText().trim();
        int fps = (Integer) fpsSpinner.getValue();
        inst.fpsLimit = fps > 0 && fps < 10 ? 10 : fps;
        result = inst;
        dispose();
    }

    private static int addRow(JPanel form, GridBagConstraints c, int row, String label, JComponent comp) {
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel(label), c);
        c.gridx = 1; c.weightx = 1;
        form.add(comp, c);
        return row + 1;
    }
}
