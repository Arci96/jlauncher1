package mclauncher.ui;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.lang.management.ManagementFactory;

/** Eigene Swing-Komponenten im Look des Launchers. */
public final class Widgets {
    private Widgets() {}

    public static void aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    public static JScrollPane scroll(Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return sp;
    }

    public static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Theme.MUTED);
        return l;
    }

    public static JLabel title(String text, float size) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.BOLD, size));
        return l;
    }

    // ------------------------------------------------------------------ Karte mit runden Ecken

    public static class RoundPanel extends JPanel {
        private int arc = 16;
        private Color fill = Theme.PANEL;
        private Color line = Theme.BORDER;

        public RoundPanel() { this(new BorderLayout()); }

        public RoundPanel(LayoutManager lm) {
            super(lm);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        }

        public RoundPanel fill(Color c) { fill = c; repaint(); return this; }
        public RoundPanel line(Color c) { line = c; repaint(); return this; }
        public RoundPanel arc(int a) { arc = a; repaint(); return this; }
        public RoundPanel pad(int t, int l, int b, int r) {
            setBorder(BorderFactory.createEmptyBorder(t, l, b, r));
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            if (line != null) {
                g.setColor(line);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            }
            g.dispose();
            super.paintComponent(g0);
        }
    }

    // ------------------------------------------------------------------ Buttons

    public enum Style { PRIMARY, DARK, DANGER, GHOST }

    public static class GlowButton extends JButton {
        private Style style;
        private int arc = 14;

        public GlowButton(String text, Style style) {
            super(text);
            this.style = style;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setForeground(style == Style.GHOST ? Theme.ACCENT_HI : Color.WHITE);
            setFont(Theme.font(Font.BOLD, 13f));
            setMargin(new Insets(8, 16, 8, 16));
        }

        public GlowButton arc(int a) { arc = a; return this; }

        public GlowButton style(Style s) {
            style = s;
            setForeground(s == Style.GHOST ? Theme.ACCENT_HI : Color.WHITE);
            repaint();
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            boolean hover = getModel().isRollover();
            boolean press = getModel().isArmed();
            Color top, bottom, edge;
            switch (style) {
                case PRIMARY -> { top = new Color(0x3DDC73); bottom = new Color(0x16A34A); edge = new Color(0x86EFAC); }
                case DANGER -> { top = new Color(0xEF4444); bottom = new Color(0xB91C1C); edge = new Color(0xFCA5A5); }
                case GHOST -> { top = bottom = new Color(0, 0, 0, 0); edge = null; }
                default -> { top = new Color(0x223228); bottom = new Color(0x182319); edge = Theme.BORDER; }
            }
            if (!isEnabled()) {
                top = new Color(0x1B261F);
                bottom = new Color(0x1B261F);
                edge = Theme.BORDER;
            } else if (style == Style.GHOST) {
                if (hover) top = bottom = new Color(255, 255, 255, 18);
            } else {
                if (hover) { top = top.brighter(); bottom = bottom.brighter(); }
                if (press) { top = top.darker(); bottom = bottom.darker(); }
            }
            RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, arc, arc);
            g.setPaint(new GradientPaint(0, 0, top, 0, getHeight(), bottom));
            g.fill(shape);
            if (edge != null) {
                g.setColor(edge);
                g.draw(shape);
            }
            g.dispose();
            super.paintComponent(g0);
        }
    }

    // ------------------------------------------------------------------ Navigation

    public static class NavButton extends JToggleButton {
        private final String glyph;
        private int badge;

        public NavButton(String glyph, String text) {
            super(text);
            this.glyph = glyph;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(BorderFactory.createEmptyBorder(0, 50, 0, 12));
            setFont(Theme.font(Font.PLAIN, 15f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            Dimension d = new Dimension(224, 46);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        public void setBadge(int n) {
            badge = n;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            int w = getWidth(), h = getHeight();
            if (isSelected()) {
                RoundRectangle2D r = new RoundRectangle2D.Float(0.5f, 2.5f, w - 1, h - 5, 14, 14);
                g.setPaint(new GradientPaint(0, 0, new Color(0x1F6B3A), w, 0, new Color(0x123521)));
                g.fill(r);
                g.setColor(new Color(0x2FA85A));
                g.draw(r);
            } else if (getModel().isRollover()) {
                g.setColor(new Color(255, 255, 255, 14));
                g.fillRoundRect(0, 2, w, h - 4, 14, 14);
            }
            g.setFont(Theme.symbolFont(18f));
            g.setColor(isSelected() ? Theme.ACCENT_HI : Theme.MUTED);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(glyph, 18, (h + fm.getAscent() - fm.getDescent()) / 2);
            if (badge > 0) {
                String s = String.valueOf(badge);
                g.setFont(Theme.font(Font.BOLD, 12f));
                FontMetrics bf = g.getFontMetrics();
                int bw = Math.max(24, bf.stringWidth(s) + 14);
                int bx = w - bw - 12, by = (h - 24) / 2;
                g.setColor(Theme.ACCENT);
                g.fillRoundRect(bx, by, bw, 24, 8, 8);
                g.setColor(Color.BLACK);
                g.drawString(s, bx + (bw - bf.stringWidth(s)) / 2, by + 17);
            }
            g.dispose();
            setForeground(isSelected() ? Theme.TEXT : new Color(0xC3D0C8));
            super.paintComponent(g0);
        }
    }

    // ------------------------------------------------------------------ Kleine Grafik-Komponenten

    /** Balken für Verbindungsqualität (0-4). */
    public static class SignalBars extends JComponent {
        private int level;

        public SignalBars() { setPreferredSize(new Dimension(24, 18)); }

        public void setLevel(int level) {
            this.level = level;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            for (int i = 0; i < 4; i++) {
                int bh = 5 + i * 4;
                g.setColor(i < level ? (level >= 3 ? Theme.ACCENT : new Color(0xEAB308)) : new Color(0x2A3B30));
                g.fillRoundRect(i * 6, getHeight() - bh - 1, 4, bh, 2, 2);
            }
            g.dispose();
        }
    }

    /** Einfaches Würfel-Logo. */
    public static class LogoIcon extends JComponent {
        public LogoIcon() { setPreferredSize(new Dimension(46, 46)); }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            double s = Math.min(getWidth(), getHeight()) / 46.0;
            g.scale(s, s);
            Path2D top = new Path2D.Double();
            top.moveTo(23, 3); top.lineTo(42, 13); top.lineTo(23, 23); top.lineTo(4, 13); top.closePath();
            Path2D left = new Path2D.Double();
            left.moveTo(4, 13); left.lineTo(23, 23); left.lineTo(23, 43); left.lineTo(4, 33); left.closePath();
            Path2D right = new Path2D.Double();
            right.moveTo(42, 13); right.lineTo(23, 23); right.lineTo(23, 43); right.lineTo(42, 33); right.closePath();
            g.setColor(new Color(0x6EE79A)); g.fill(top);
            g.setColor(new Color(0x22A055)); g.fill(left);
            g.setColor(new Color(0x15703B)); g.fill(right);
            g.setColor(new Color(0, 0, 0, 90));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(top); g.draw(left); g.draw(right);
            g.dispose();
        }
    }

    /** Runder Avatar mit Anfangsbuchstabe. */
    public static class Avatar extends JComponent {
        private String name = "?";
        private final int size;

        public Avatar(int size) {
            this.size = size;
            setPreferredSize(new Dimension(size, size));
        }

        public void setName(String n) {
            name = n == null || n.isBlank() ? "?" : n;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            float hue = (Math.abs(name.hashCode()) % 360) / 360f;
            Color c = Color.getHSBColor(hue, 0.45f, 0.65f);
            g.setPaint(new GradientPaint(0, 0, c.brighter(), 0, size, c.darker()));
            g.fillRoundRect(0, 0, size, size, size / 3, size / 3);
            g.setColor(Color.WHITE);
            g.setFont(Theme.font(Font.BOLD, size * 0.5f));
            String s = name.substring(0, 1).toUpperCase();
            FontMetrics fm = g.getFontMetrics();
            g.drawString(s, (size - fm.stringWidth(s)) / 2, (size + fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ Scrollbare Spalte

    /** Vertikale Spalte, die in einem JScrollPane die Breite des Viewports übernimmt. */
    public static class VPanel extends JPanel implements Scrollable {
        public VPanel() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 120; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    public static void openFolder(Component parent, java.nio.file.Path dir) {
        try {
            java.nio.file.Files.createDirectories(dir);
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir.toFile());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ------------------------------------------------------------------ RAM-Regler

    public static class RamControl extends JPanel {
        private final JSlider slider;
        private final JLabel label = new JLabel();

        public RamControl(int lo, int hi, int value) {
            super(new BorderLayout(10, 0));
            setOpaque(false);
            slider = new JSlider(lo, hi, Math.max(lo, Math.min(hi, value)));
            slider.setOpaque(false);
            label.setPreferredSize(new Dimension(80, 20));
            label.setHorizontalAlignment(SwingConstants.RIGHT);
            slider.addChangeListener(e -> label.setText(getMb() + " MB"));
            label.setText(getMb() + " MB");
            add(slider, BorderLayout.CENTER);
            add(label, BorderLayout.EAST);
        }

        public int getMb() { return Math.max(512, slider.getValue() / 256 * 256); }
        public void setMb(int v) { slider.setValue(v); }
        public void addChangeListener(ChangeListener l) { slider.addChangeListener(l); }
    }

    public static int totalMemoryMb() {
        try {
            var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            long mb = os.getTotalMemorySize() / (1024 * 1024);
            return (int) Math.max(2048, Math.min(mb, 65536));
        } catch (Throwable t) {
            return 8192;
        }
    }
}
