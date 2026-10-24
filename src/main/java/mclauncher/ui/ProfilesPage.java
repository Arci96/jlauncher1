package mclauncher.ui;

import mclauncher.GameRunner;
import mclauncher.Instance;
import mclauncher.Store;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Date;

/** "Spielen": Liste aller Profile. */
public class ProfilesPage extends Page {
    private final Widgets.VPanel list = new Widgets.VPanel();

    public ProfilesPage(MainWindow win) {
        super(win);
        Widgets.GlowButton add = new Widgets.GlowButton("+  Neues Profil", Widgets.Style.PRIMARY);
        add.addActionListener(e -> win.newProfile());
        add(header("Profile", "Jedes Profil hat eigene Welten, Mods und Einstellungen", add), BorderLayout.NORTH);
        add(Widgets.scroll(list), BorderLayout.CENTER);
        rebuild();
    }

    @Override public void onShow() { rebuild(); }
    @Override protected void onProfileChanged() { rebuild(); }

    private void rebuild() {
        list.removeAll();
        Instance cur = AppState.current();
        if (Store.data.instances.isEmpty()) {
            JLabel l = Widgets.muted("Noch kein Profil. Klicke oben auf \"Neues Profil\".");
            l.setAlignmentX(LEFT_ALIGNMENT);
            list.add(l);
        }
        for (Instance inst : Store.data.instances) {
            list.add(card(inst, inst == cur));
            list.add(Box.createVerticalStrut(10));
        }
        list.revalidate();
        list.repaint();
    }

    private JComponent card(Instance inst, boolean selected) {
        Widgets.RoundPanel c = new Widgets.RoundPanel(new BorderLayout(16, 0)).fill(Theme.PANEL2);
        c.line(selected ? Theme.ACCENT : Theme.BORDER);
        c.setAlignmentX(LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
        c.setPreferredSize(new Dimension(100, 92));

        Widgets.Avatar av = new Widgets.Avatar(54);
        av.setName(inst.name);
        JPanel avWrap = new JPanel(new GridBagLayout());
        avWrap.setOpaque(false);
        avWrap.add(av);
        c.add(avWrap, BorderLayout.WEST);

        JPanel mid = new JPanel();
        mid.setOpaque(false);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(inst.name);
        name.setFont(Theme.font(Font.BOLD, 17f));
        JLabel ver = new JLabel(inst.label() + (inst.loaderVersion == null || inst.loaderVersion.isBlank() ? "" : "  (" + inst.loaderVersion + ")"));
        String last = inst.lastPlayed == 0 ? "noch nie gespielt" : "zuletzt " + new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(inst.lastPlayed));
        JLabel info = Widgets.muted("RAM " + inst.ramMin + "–" + inst.ramMax + " MB  ·  " + last + (inst.hasCustomDir() ? "  ·  eigener Ordner" : ""));
        mid.add(Box.createVerticalGlue());
        mid.add(name);
        mid.add(ver);
        mid.add(info);
        mid.add(Box.createVerticalGlue());
        c.add(mid, BorderLayout.CENTER);

        Widgets.GlowButton play = new Widgets.GlowButton("Spielen", Widgets.Style.PRIMARY);
        Widgets.GlowButton edit = new Widgets.GlowButton("Bearbeiten", Widgets.Style.DARK);
        Widgets.GlowButton folder = new Widgets.GlowButton("Ordner", Widgets.Style.DARK);
        Widgets.GlowButton del = new Widgets.GlowButton("\u2715", Widgets.Style.DARK);
        play.addActionListener(e -> {
            AppState.setCurrent(inst);
            if (GameRunner.state() == GameRunner.State.IDLE) {
                win.play(inst, null);
                win.navigate("home");
            } else {
                JOptionPane.showMessageDialog(this, "Minecraft läuft bereits oder wird gerade vorbereitet.");
            }
        });
        edit.addActionListener(e -> win.editProfile(inst));
        folder.addActionListener(e -> Widgets.openFolder(this, inst.dir()));
        del.setToolTipText("Profil löschen");
        del.addActionListener(e -> win.deleteProfile(inst));
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        btns.add(play);
        btns.add(edit);
        btns.add(folder);
        btns.add(del);
        JPanel east = new JPanel(new GridBagLayout());
        east.setOpaque(false);
        east.add(btns);
        c.add(east, BorderLayout.EAST);

        c.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { AppState.setCurrent(inst); }
        });
        return c;
    }
}
