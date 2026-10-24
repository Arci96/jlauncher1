package mclauncher.ui;

import javax.swing.*;
import java.awt.*;

/** Basisklasse für alle Seiten der Hauptansicht. */
public abstract class Page extends JPanel {
    protected final MainWindow win;

    protected Page(MainWindow win) {
        super(new BorderLayout(0, 14));
        this.win = win;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 18, 24));
        AppState.addListener(this::onProfileChanged);
    }

    /** Wird aufgerufen, wenn die Seite angezeigt wird. */
    public void onShow() {}

    /** Wird aufgerufen, wenn sich Profil oder Profilliste ändern. */
    protected void onProfileChanged() {}

    protected static JComponent header(String title, String subtitle, JComponent... actions) {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setOpaque(false);
        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.setFont(Theme.font(Font.BOLD, 26f));
        texts.add(t);
        if (subtitle != null && !subtitle.isBlank()) {
            JLabel s = new JLabel(subtitle);
            s.setForeground(Theme.MUTED);
            s.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
            texts.add(s);
        }
        p.add(texts, BorderLayout.CENTER);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        for (JComponent a : actions) right.add(a);
        p.add(right, BorderLayout.EAST);
        return p;
    }
}
