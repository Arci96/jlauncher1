package mclauncher.ui;

import mclauncher.Auth;
import mclauncher.Store;
import mclauncher.Util;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.net.URI;

public class AccountDialog extends JDialog {
    private final JRadioButton offlineBtn = new JRadioButton("Offline (freier Name)");
    private final JRadioButton msBtn = new JRadioButton("Microsoft-Konto");
    private final JTextField nameField = new JTextField(20);
    private final JTextField clientIdField = new JTextField(30);
    private final JLabel msStatus = new JLabel(" ");
    private final JButton loginBtn = new JButton("Mit Microsoft anmelden");
    private volatile boolean cancelled;

    public AccountDialog(Frame owner) {
        super(owner, "Konto", true);
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
        c.gridy = 1; p.add(labeled("Spielername", nameField), c);
        c.gridy = 2; c.insets = new Insets(14, 4, 4, 4); p.add(msBtn, c);
        c.insets = new Insets(4, 4, 4, 4);
        c.gridy = 3; p.add(labeled("Client-ID", clientIdField), c);
        c.gridy = 4; p.add(loginBtn, c);
        c.gridy = 5; p.add(msStatus, c);

        loginBtn.addActionListener(e -> startLogin());

        JButton ok = new JButton("Übernehmen");
        JButton cancel = new JButton("Schließen");
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
        msStatus.setText(cfg.msRefreshToken != null && !cfg.msRefreshToken.isBlank() && !cfg.msName.isBlank()
                ? "Angemeldet als " + cfg.msName : "Nicht angemeldet");
    }

    private void startLogin() {
        String clientId = clientIdField.getText().trim();
        Store.data.config.msClientId = clientId;
        loginBtn.setEnabled(false);
        cancelled = false;
        msStatus.setText("Verbinde …");
        Util.async(() -> {
            try {
                Auth.DeviceCode dc = Auth.startDeviceFlow(clientId);
                SwingUtilities.invokeLater(() -> {
                    msStatus.setText("<html>Öffne <b>" + dc.verificationUri() + "</b> und gib den Code <b>"
                            + dc.userCode() + "</b> ein (in der Zwischenablage).</html>");
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
                    JOptionPane.showMessageDialog(this, "Angemeldet als " + acc.name(), "Erfolg", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    msStatus.setText(" ");
                    loginBtn.setEnabled(true);
                    if (!cancelled) {
                        JOptionPane.showMessageDialog(this, ex.getMessage(), "Anmeldung fehlgeschlagen", JOptionPane.ERROR_MESSAGE);
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
