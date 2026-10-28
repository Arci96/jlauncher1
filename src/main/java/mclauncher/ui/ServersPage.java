package mclauncher.ui;

import mclauncher.I18n;
import mclauncher.GameRunner;
import mclauncher.Instance;
import mclauncher.ServerPing;
import mclauncher.Store;

import javax.swing.*;
import java.awt.*;

/** Eigene Server speichern, Status ansehen und direkt beitreten. */
public class ServersPage extends Page {
    private final Widgets.VPanel list = new Widgets.VPanel();

    public ServersPage(MainWindow win) {
        super(win);
        Widgets.GlowButton add = new Widgets.GlowButton(I18n.t("+  Server hinzufügen"), Widgets.Style.PRIMARY);
        Widgets.GlowButton refresh = new Widgets.GlowButton(I18n.t("Aktualisieren"), Widgets.Style.DARK);
        add.addActionListener(e -> addServer());
        refresh.addActionListener(e -> refreshStatus());
        add(header(I18n.t("Server"), I18n.t("Speichere Server und tritt mit dem aktuellen Profil direkt bei"), refresh, add), BorderLayout.NORTH);
        add(Widgets.scroll(list), BorderLayout.CENTER);
        rebuild();
    }

    @Override
    public void onShow() {
        rebuild();
        refreshStatus();
    }

    @Override protected void onProfileChanged() { rebuild(); }

    private void refreshStatus() {
        ServerPing.refreshAll(() -> SwingUtilities.invokeLater(this::rebuild));
    }

    private void addServer() {
        JTextField name = new JTextField(20);
        JTextField addr = new JTextField(20);
        addr.putClientProperty("JTextField.placeholderText", I18n.t("z. B. mc.beispiel.de oder mc.beispiel.de:25566"));
        JPanel p = new JPanel(new GridLayout(4, 1, 0, 4));
        p.add(new JLabel("Name"));
        p.add(name);
        p.add(new JLabel(I18n.t("Adresse")));
        p.add(addr);
        if (JOptionPane.showConfirmDialog(this, p, I18n.t("Server hinzufügen"), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String a = addr.getText().trim();
        if (a.isEmpty()) return;
        String n = name.getText().trim();
        Store.data.servers.add(new Store.ServerEntry(n.isEmpty() ? a : n, a));
        Store.save();
        rebuild();
        refreshStatus();
    }

    private void rebuild() {
        list.removeAll();
        if (Store.data.servers.isEmpty()) {
            JLabel l = Widgets.muted(I18n.t("Noch keine Server. Klicke oben auf \"Server hinzufügen\"."));
            l.setAlignmentX(LEFT_ALIGNMENT);
            list.add(l);
        }
        for (Store.ServerEntry s : Store.data.servers) {
            list.add(row(s));
            list.add(Box.createVerticalStrut(10));
        }
        list.revalidate();
        list.repaint();
    }

    private JComponent row(Store.ServerEntry s) {
        ServerPing.Result r = ServerPing.cached(s.address);
        Widgets.RoundPanel c = new Widgets.RoundPanel(new BorderLayout(16, 0)).fill(Theme.PANEL2);
        c.setAlignmentX(LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        c.setPreferredSize(new Dimension(100, 82));

        Widgets.Avatar av = new Widgets.Avatar(48);
        av.setName(s.name.isBlank() ? s.address : s.name);
        JPanel avWrap = new JPanel(new GridBagLayout());
        avWrap.setOpaque(false);
        avWrap.add(av);
        c.add(avWrap, BorderLayout.WEST);

        JPanel mid = new JPanel();
        mid.setOpaque(false);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(s.name.isBlank() ? s.address : s.name);
        name.setFont(Theme.font(Font.BOLD, 16f));
        String sub = s.address + (r != null && r.online() && !r.motd().isBlank() ? "  ·  " + r.motd() : "");
        JLabel addr = Widgets.muted(sub.length() > 90 ? sub.substring(0, 90) + "…" : sub);
        mid.add(Box.createVerticalGlue());
        mid.add(name);
        mid.add(addr);
        mid.add(Box.createVerticalGlue());
        c.add(mid, BorderLayout.CENTER);

        JLabel players = new JLabel(r == null ? I18n.t("prüfe …") : r.online() ? r.players() + " / " + r.max() + "  ·  " + r.pingMs() + " ms" : "offline");
        players.setForeground(r != null && r.online() ? Theme.TEXT : Theme.MUTED);
        Widgets.SignalBars bars = new Widgets.SignalBars();
        bars.setLevel(r == null ? 0 : r.signalLevel());
        Widgets.GlowButton join = new Widgets.GlowButton(I18n.t("Beitreten"), Widgets.Style.PRIMARY);
        Widgets.GlowButton del = new Widgets.GlowButton("\u2715", Widgets.Style.DARK);
        join.addActionListener(e -> {
            Instance cur = AppState.current();
            if (cur == null) {
                JOptionPane.showMessageDialog(this, I18n.t("Erstelle zuerst ein Profil."));
            } else if (GameRunner.state() != GameRunner.State.IDLE) {
                JOptionPane.showMessageDialog(this, I18n.t("Minecraft läuft bereits oder wird gerade vorbereitet."));
            } else {
                win.play(cur, s.address);
                win.navigate("home");
            }
        });
        del.addActionListener(e -> {
            Store.data.servers.remove(s);
            Store.save();
            rebuild();
        });
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        east.setOpaque(false);
        east.add(players);
        east.add(bars);
        east.add(join);
        east.add(del);
        JPanel eastWrap = new JPanel(new GridBagLayout());
        eastWrap.setOpaque(false);
        eastWrap.add(east);
        c.add(eastWrap, BorderLayout.EAST);
        return c;
    }
}
