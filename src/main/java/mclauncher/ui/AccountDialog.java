package mclauncher.ui;

import mclauncher.I18n;
import mclauncher.Auth;
import mclauncher.SecretStore;
import mclauncher.Store;
import mclauncher.Util;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.net.URI;

public class AccountDialog extends JDialog {
    private final JRadioButton offlineBtn = new JRadioButton(I18n.t("Offline (freier Name)"));
    private final JRadioButton msBtn = new JRadioButton(I18n.t("Microsoft-Konto"));
    private final JTextField nameField = new JTextField(20);
    private final JTextField clientIdField = new JTextField(30);
    private final JLabel msStatus = new JLabel(" ");
    private final JButton loginBtn = new JButton(I18n.t("Mit Microsoft anmelden"));
    private final JButton logoutBtn = new JButton(I18n.t("Abmelden"));
    private final JLabel tokenInfo = new JLabel(" ");
    private volatile boolean cancelled;

    public AccountDialog(Frame owner) {
        super(owner, I18n.t("Konto"), true);
        Store.Config cfg = Store.data.config;

        ButtonGroup g = new ButtonGroup();
        g.add(offlineBtn);
        g.add(msBtn);
        (("microsoft".equals(cfg.accountType)) ? msBtn : offlineBtn).setSelected(true);

        nameField.setText(cfg.offlineName);
        clientIdField.setText(cfg.msClientId);
        clientIdField.putClientProperty("JTextField.placeholderText", "Azure Application (Client) ID");
        refreshStatus();

        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.gridx = 0;

        c.gridy = 0; p.add(offlineBtn, c);
        c.gridy = 1; p.add(labeled(I18n.t("Spielername"), nameField), c);
        c.gridy = 2; c.insets = new Insets(14, 4, 4, 4); p.add(msBtn, c);
        c.insets = new Insets(4, 4, 4, 4);
        c.gridy = 3; p.add(labeled(I18n.t("Client-ID"), clientIdField), c);
        JPanel loginRow = new JPanel(new GridLayout(1, 2, 8, 0));
        loginRow.add(loginBtn);
        loginRow.add(logoutBtn);
        c.gridy = 4; p.add(loginRow, c);
        c.gridy = 5; p.add(msStatus, c);
        tokenInfo.setForeground(Theme.MUTED);
        c.gridy = 6; p.add(tokenInfo, c);

        loginBtn.addActionListener(e -> startLogin());
        logoutBtn.addActionListener(e -> {
            logoutBtn.setEnabled(false);
            Util.async(() -> {
                Auth.signOut();
                SwingUtilities.invokeLater(() -> {
                    offlineBtn.setSelected(true);
                    refreshStatus();
                });
            });
        });
        // Wo das Token liegt, wird im Hintergrund ermittelt (startet ggf. ein Systemprogramm)
        Util.async(() -> {
            String where = SecretStore.backendLabel();
            SwingUtilities.invokeLater(() -> tokenInfo.setText(I18n.t("Token gespeichert in: ") + where));
        });

        JButton ok = new JButton(I18n.t("Übernehmen"));
        JButton cancel = new JButton(I18n.t("Schließen"));
        ok.addActionListener(e -> {
            cfg.accountType = msBtn.isSelected() ? "microsoft" : "offline";
            String n = nameField.getText().trim();
            cfg.offlineName = n.isEmpty() ? "Player" : n;
            cfg.msClientId = clientIdField.getText().trim();
            Store.save();
            cancelled = true;
            dispose();
        });
        cancel.addActionListener(e -> { cancelled = true; dispose(); });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(cancel);
        buttons.add(ok);

        add(p, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setMinimumSize(new Dimension(480, getHeight()));
        setLocationRelativeTo(owner);
    }

    private void refreshStatus() {
        Store.Config cfg = Store.data.config;
        boolean in = Auth.signedIn() && !cfg.msName.isBlank();
        msStatus.setText(in ? I18n.t("Angemeldet als ") + cfg.msName : I18n.t("Nicht angemeldet"));
        logoutBtn.setEnabled(Auth.signedIn());
    }

    private void startLogin() {
        String clientId = clientIdField.getText().trim();
        Store.data.config.msClientId = clientId;
        loginBtn.setEnabled(false);
        cancelled = false;
        msStatus.setText(I18n.t("Verbinde …"));
        Util.async(() -> {
            try {
                Auth.DeviceCode dc = Auth.startDeviceFlow(clientId);
                SwingUtilities.invokeLater(() -> {
                    msStatus.setText(I18n.t("<html>Öffne <b>") + dc.verificationUri() + I18n.t("</b> und gib den Code <b>")
                            + dc.userCode() + I18n.t("</b> ein (in der Zwischenablage).</html>"));
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(dc.userCode()), null);
                    try {
                        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(dc.verificationUri()));
                    } catch (Exception ignored) { }
                });
                Auth.Account acc = Auth.finishDeviceFlow(clientId, dc, () -> cancelled);
                Store.data.config.accountType = "microsoft";
                Store.save();
                SwingUtilities.invokeLater(() -> {
                    msBtn.setSelected(true);
                    refreshStatus();
                    loginBtn.setEnabled(true);
                    JOptionPane.showMessageDialog(this, I18n.t("Angemeldet als ") + acc.name(), I18n.t("Erfolg"), JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    msStatus.setText(" ");
                    loginBtn.setEnabled(true);
                    if (!cancelled) {
                        JOptionPane.showMessageDialog(this, ex.getMessage(), I18n.t("Anmeldung fehlgeschlagen"), JOptionPane.ERROR_MESSAGE);
                    }
                });
            }
        });
    }

    private static JPanel labeled(String label, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(90, 20));
        p.add(l, BorderLayout.WEST);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }
}
