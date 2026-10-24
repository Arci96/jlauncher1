package mclauncher.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Großes Titelbild: selbst gezeichnete Block-Landschaft bei Sonnenuntergang. */
public class HeroPanel extends JPanel {
    private final String title;
    private final String subtitle;

    public HeroPanel(String title, String subtitle) {
        super(new BorderLayout());
        this.title = title;
        this.subtitle = subtitle;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Widgets.aa(g);
        int w = getWidth(), h = getHeight();
        g.setClip(new RoundRectangle2D.Float(0, 0, w, h, 22, 22));

        // Himmel
        g.setPaint(new LinearGradientPaint(0, 0, 0, h,
                new float[]{0f, 0.42f, 0.68f, 1f},
                new Color[]{new Color(0x141C36), new Color(0x7A3B5E), new Color(0xE8833A), new Color(0xF4B860)}));
        g.fillRect(0, 0, w, h);

        // Sonne
        int sx = (int) (w * 0.74), sy = (int) (h * 0.47);
        g.setPaint(new RadialGradientPaint(sx, sy, 110, new float[]{0f, 1f},
                new Color[]{new Color(255, 236, 170, 230), new Color(255, 200, 120, 0)}));
        g.fillOval(sx - 110, sy - 110, 220, 220);
        g.setColor(new Color(0xFFF2B8));
        g.fillRect(sx - 22, sy - 22, 44, 44);

        // Hügel und Burg
        layer(g, w, h, 0.60, 0.06, new Color(0x3B2A52), 22, 1.3);
        castle(g, w, h);
        layer(g, w, h, 0.68, 0.07, new Color(0x1F3B3A), 18, 2.1);
        layer(g, w, h, 0.77, 0.08, new Color(0x14502C), 16, 3.7);
        layer(g, w, h, 0.89, 0.06, new Color(0x0C3A20), 16, 5.2);

        // Verlauf nach unten, damit der Übergang zur Karte weich ist
        g.setPaint(new GradientPaint(0, h * 0.5f, new Color(9, 14, 11, 0), 0, h, new Color(9, 14, 11, 235)));
        g.fillRect(0, 0, w, h);

        // Titel mit "Tiefe"
        g.setFont(Theme.font(Font.BOLD, Math.min(80f, w / 8.5f)));
        FontMetrics fm = g.getFontMetrics();
        int tx = (w - fm.stringWidth(title)) / 2;
        int ty = (int) (h * 0.40);
        g.setColor(new Color(15, 15, 15));
        for (int d = 1; d <= 6; d++) g.drawString(title, tx + d, ty + d);
        g.setPaint(new GradientPaint(0, ty - fm.getAscent(), new Color(0xF6F6F6), 0, ty, new Color(0xA5A5A5)));
        g.drawString(title, tx, ty);

        // Untertitel mit Buchstabenabstand
        g.setFont(Theme.font(Font.BOLD, 15f));
        fm = g.getFontMetrics();
        int spacing = 3, total = 0;
        for (char ch : subtitle.toCharArray()) total += fm.charWidth(ch) + spacing;
        int x = (w - total) / 2, y = ty + 44;
        g.setColor(new Color(0xF0F0F0));
        for (char ch : subtitle.toCharArray()) {
            g.drawString(String.valueOf(ch), x, y);
            x += fm.charWidth(ch) + spacing;
        }

        g.dispose();
    }

    private static void layer(Graphics2D g, int w, int h, double base, double amp, Color c, int step, double seed) {
        g.setColor(c);
        for (int x = 0; x < w; x += step) {
            double n = Math.sin(x * 0.012 + seed) * 0.5 + Math.sin(x * 0.031 + seed * 2) * 0.3 + Math.sin(x * 0.007 + seed * 3) * 0.2;
            int top = (int) (h * (base - amp * n));
            top = top / step * step;
            g.fillRect(x, top, step, h - top);
        }
    }

    private static void castle(Graphics2D g, int w, int h) {
        g.setColor(new Color(0x2A1E44));
        int cx = (int) (w * 0.52), by = (int) (h * 0.62);
        int[] xs = {-150, -100, -40, 0, 40, 100, 150};
        int[] hs = {70, 110, 90, 150, 90, 110, 70};
        g.fillRect(cx - 150, by - 50, 300, 60);
        for (int i = 0; i < xs.length; i++) {
            int tx = cx + xs[i], top = by - hs[i];
            g.fillRect(tx - 17, top, 34, hs[i] + 10);
            g.fillPolygon(new int[]{tx - 22, tx + 22, tx}, new int[]{top, top, top - 34}, 3);
        }
        // erleuchtete Fenster
        g.setColor(new Color(0xFFC96B));
        for (int i = 0; i < xs.length; i++) g.fillRect(cx + xs[i] - 3, by - hs[i] + 22, 6, 10);
    }
}
