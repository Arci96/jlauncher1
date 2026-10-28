package mclauncher.ui;

import mclauncher.I18n;
import mclauncher.Store;
import mclauncher.Util;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/** Titelbild: selbst gezeichnete Block-Landschaften oder ein eigenes Bild. */
public class HeroPanel extends JPanel {
    public enum Scene {
        SUNSET(I18n.t("Sonnenuntergang")), NIGHT(I18n.t("Nacht")), DAY(I18n.t("Tag")), NETHER("Nether"), CUSTOM(I18n.t("Eigenes Bild"));

        private final String label;

        Scene(String label) { this.label = label; }

        public String label() { return I18n.t(label); }
    }

    private static final List<HeroPanel> ALL = new CopyOnWriteArrayList<>();
    private static volatile BufferedImage customImg;
    private static volatile String customPath = "";
    private static volatile boolean loading;

    private final String title;
    private final String subtitle;
    private final Scene fixed;
    private final boolean showText;
    private double k = 1;   // Skalierung, damit auch kleine Vorschauen gut aussehen

    public HeroPanel(String title, String subtitle) {
        this(title, subtitle, null, true);
    }

    /** Kleine Vorschau einer festen Szene (ohne Text). */
    public HeroPanel(Scene fixed) {
        this("", "", fixed, false);
    }

    private HeroPanel(String title, String subtitle, Scene fixed, boolean showText) {
        super(new BorderLayout());
        this.title = title;
        this.subtitle = subtitle;
        this.fixed = fixed;
        this.showText = showText;
        setOpaque(false);
        ALL.add(this);
    }

    /** Alle Titelbilder neu zeichnen (nach Änderung der Auswahl). */
    public static void refreshAll() {
        SwingUtilities.invokeLater(() -> ALL.forEach(Component::repaint));
    }

    private Scene scene() {
        if (fixed != null) return fixed;
        try {
            return Scene.valueOf(Store.data.config.heroScene);
        } catch (Exception e) {
            return Scene.SUNSET;
        }
    }

    private int s(int v) { return (int) Math.max(1, Math.round(v * k)); }

    // ------------------------------------------------------------------ Eigenes Bild

    private static BufferedImage customImage() {
        String path = Store.data.config.heroImage;
        if (path == null || path.isBlank()) return null;
        if (!path.equals(customPath) && !loading) {
            customPath = path;
            customImg = null;
            loading = true;
            Util.async(() -> {
                BufferedImage out = null;
                try {
                    BufferedImage in = ImageIO.read(new File(path));
                    if (in != null) out = downscale(in, 1800);
                } catch (Exception ignored) { }
                customImg = out;
                loading = false;
                refreshAll();
            });
        }
        return path.equals(customPath) ? customImg : null;
    }

    private static BufferedImage downscale(BufferedImage in, int maxW) {
        if (in.getWidth() <= maxW) return in;
        int h = Math.max(1, in.getHeight() * maxW / in.getWidth());
        BufferedImage out = new BufferedImage(maxW, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(in, 0, 0, maxW, h, null);
        g.dispose();
        return out;
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Widgets.aa(g);
        int w = getWidth(), h = getHeight();
        k = Math.max(0.3, Math.min(1.2, w / 700.0));
        int arc = (int) Math.round(22 * Math.max(0.5, k));
        g.setClip(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));

        Scene sc = scene();
        BufferedImage img = null;
        boolean placeholder = false;
        if (sc == Scene.CUSTOM) {
            img = customImage();
            if (img == null) {
                if (fixed != null) placeholder = true;   // Vorschau ohne Bild
                else sc = Scene.SUNSET;                  // noch nicht geladen / fehlt
            }
        }
        if (placeholder) {
            placeholder(g, w, h);
        } else {
            switch (sc) {
                case NIGHT -> night(g, w, h);
                case DAY -> day(g, w, h);
                case NETHER -> nether(g, w, h);
                case CUSTOM -> cover(g, w, h, img);
                default -> sunset(g, w, h);
            }
        }

        // weicher Übergang nach unten
        g.setPaint(new GradientPaint(0, h * 0.5f, new Color(9, 14, 11, 0), 0, h, new Color(9, 14, 11, 235)));
        g.fillRect(0, 0, w, h);

        if (showText) text(g, w, h);
        g.dispose();
    }

    private void sunset(Graphics2D g, int w, int h) {
        g.setPaint(new LinearGradientPaint(0, 0, 0, h, new float[]{0f, 0.42f, 0.68f, 1f},
                new Color[]{new Color(0x141C36), new Color(0x7A3B5E), new Color(0xE8833A), new Color(0xF4B860)}));
        g.fillRect(0, 0, w, h);
        int sx = (int) (w * 0.74), sy = (int) (h * 0.47);
        glow(g, sx, sy, s(110), new Color(255, 236, 170, 230));
        g.setColor(new Color(0xFFF2B8));
        g.fillRect(sx - s(22), sy - s(22), s(44), s(44));
        layer(g, w, h, 0.60, 0.06, new Color(0x3B2A52), s(22), 1.3);
        castle(g, w, h, new Color(0x2A1E44), true);
        layer(g, w, h, 0.68, 0.07, new Color(0x1F3B3A), s(18), 2.1);
        layer(g, w, h, 0.77, 0.08, new Color(0x14502C), s(16), 3.7);
        layer(g, w, h, 0.89, 0.06, new Color(0x0C3A20), s(16), 5.2);
    }

    private void night(Graphics2D g, int w, int h) {
        g.setPaint(new LinearGradientPaint(0, 0, 0, h, new float[]{0f, 0.6f, 1f},
                new Color[]{new Color(0x04061A), new Color(0x1B1F4A), new Color(0x3A3A78)}));
        g.fillRect(0, 0, w, h);
        Random rnd = new Random(11);
        for (int i = 0; i < 90; i++) {
            int x = rnd.nextInt(Math.max(1, w)), y = rnd.nextInt(Math.max(1, (int) (h * 0.55)));
            int size = rnd.nextBoolean() ? s(2) : s(3);
            g.setColor(new Color(255, 255, 255, 120 + rnd.nextInt(135)));
            g.fillRect(x, y, size, size);
        }
        int mx = (int) (w * 0.78), my = (int) (h * 0.22);
        glow(g, mx, my, s(90), new Color(200, 215, 255, 150));
        g.setColor(new Color(0xF1F1E6));
        g.fillRect(mx - s(20), my - s(20), s(40), s(40));
        g.setColor(new Color(0xCFCFC2));
        g.fillRect(mx - s(8), my - s(10), s(10), s(10));
        layer(g, w, h, 0.60, 0.06, new Color(0x14183A), s(22), 1.3);
        castle(g, w, h, new Color(0x0E1030), true);
        layer(g, w, h, 0.68, 0.07, new Color(0x10263A), s(18), 2.1);
        layer(g, w, h, 0.77, 0.08, new Color(0x0B3A24), s(16), 3.7);
        layer(g, w, h, 0.89, 0.06, new Color(0x07281A), s(16), 5.2);
    }

    private void day(Graphics2D g, int w, int h) {
        g.setPaint(new LinearGradientPaint(0, 0, 0, h, new float[]{0f, 0.6f, 1f},
                new Color[]{new Color(0x3E8EDE), new Color(0xA8D8FF), new Color(0xE6F5FF)}));
        g.fillRect(0, 0, w, h);
        int sx = (int) (w * 0.2), sy = (int) (h * 0.2);
        glow(g, sx, sy, s(100), new Color(255, 240, 150, 200));
        g.setColor(new Color(0xFFE45C));
        g.fillRect(sx - s(23), sy - s(23), s(46), s(46));
        cloud(g, (int) (w * 0.38), (int) (h * 0.2));
        cloud(g, (int) (w * 0.65), (int) (h * 0.12));
        cloud(g, (int) (w * 0.86), (int) (h * 0.3));
        layer(g, w, h, 0.60, 0.06, new Color(0x8FB7D9), s(22), 1.3);
        castle(g, w, h, new Color(0x7E92B8), false);
        layer(g, w, h, 0.68, 0.07, new Color(0x4F9B58), s(18), 2.1);
        layer(g, w, h, 0.77, 0.08, new Color(0x3C8A44), s(16), 3.7);
        layer(g, w, h, 0.89, 0.06, new Color(0x2C6B34), s(16), 5.2);
    }

    private void nether(Graphics2D g, int w, int h) {
        g.setPaint(new LinearGradientPaint(0, 0, 0, h, new float[]{0f, 0.5f, 0.78f, 1f},
                new Color[]{new Color(0x160303), new Color(0x5A0E06), new Color(0xB3300C), new Color(0xFF7A1F)}));
        g.fillRect(0, 0, w, h);
        layer(g, w, h, 0.58, 0.07, new Color(0x2B0A0A), s(22), 1.7);
        // Festung
        g.setColor(new Color(0x1A0707));
        int cx = (int) (w * 0.5), by = (int) (h * 0.64), bw = s(34);
        g.fillRect(cx - 5 * bw - bw / 2, by - s(30), 11 * bw, s(40));
        for (int i = -5; i <= 5; i++) {
            int hh = s(40) + ((i * i * 7) % 5) * s(14);
            g.fillRect(cx + i * bw - bw / 2, by - hh, bw - s(4), hh + s(10));
        }
        layer(g, w, h, 0.68, 0.07, new Color(0x3D0F0C), s(18), 2.9);
        layer(g, w, h, 0.78, 0.08, new Color(0x5A1812), s(16), 3.1);
        // Lava
        int ly = (int) (h * 0.86);
        g.setPaint(new GradientPaint(0, ly, new Color(0xFF9A1F), 0, h, new Color(0xFF4A0A)));
        g.fillRect(0, ly, w, h - ly);
        layer(g, w, h, 0.93, 0.03, new Color(0x7A2216), s(16), 5.3);
        Random rnd = new Random(5);
        for (int i = 0; i < 40; i++) {
            int x = rnd.nextInt(Math.max(1, w)), y = (int) (h * (0.2 + rnd.nextDouble() * 0.7));
            g.setColor(new Color(255, 150 + rnd.nextInt(80), 40, 120 + rnd.nextInt(120)));
            g.fillRect(x, y, s(3), s(3));
        }
    }

    private void cover(Graphics2D g, int w, int h, BufferedImage img) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        double scale = Math.max((double) w / img.getWidth(), (double) h / img.getHeight());
        int dw = (int) Math.ceil(img.getWidth() * scale), dh = (int) Math.ceil(img.getHeight() * scale);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
        // etwas abdunkeln, damit der Text lesbar bleibt
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRect(0, 0, w, h);
    }

    private void placeholder(Graphics2D g, int w, int h) {
        g.setPaint(new GradientPaint(0, 0, new Color(0x233028), w, h, new Color(0x121A15)));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(255, 255, 255, 120));
        int cx = w / 2, cy = h / 2, a = s(18);
        g.fillRect(cx - a, cy - s(3), 2 * a, s(6));
        g.fillRect(cx - s(3), cy - a, s(6), 2 * a);
    }

    private void text(Graphics2D g, int w, int h) {
        g.setFont(Theme.font(Font.BOLD, Math.min(80f, w / 8.5f)));
        FontMetrics fm = g.getFontMetrics();
        int tx = (w - fm.stringWidth(title)) / 2;
        int ty = (int) (h * 0.40);
        g.setColor(new Color(15, 15, 15));
        for (int d = 1; d <= 6; d++) g.drawString(title, tx + d, ty + d);
        g.setPaint(new GradientPaint(0, ty - fm.getAscent(), new Color(0xF6F6F6), 0, ty, new Color(0xA5A5A5)));
        g.drawString(title, tx, ty);

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
    }

    // ------------------------------------------------------------------ Bausteine

    private static void glow(Graphics2D g, int x, int y, int r, Color c) {
        g.setPaint(new RadialGradientPaint(x, y, r, new float[]{0f, 1f}, new Color[]{c, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fillOval(x - r, y - r, 2 * r, 2 * r);
    }

    private void cloud(Graphics2D g, int x, int y) {
        g.setColor(new Color(255, 255, 255, 235));
        g.fillRect(x, y, s(110), s(22));
        g.fillRect(x + s(20), y - s(16), s(60), s(16));
        g.fillRect(x + s(50), y + s(22), s(70), s(12));
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

    private void castle(Graphics2D g, int w, int h, Color c, boolean windows) {
        g.setColor(c);
        int cx = (int) (w * 0.52), by = (int) (h * 0.62);
        int[] xs = {-150, -100, -40, 0, 40, 100, 150};
        int[] hs = {70, 110, 90, 150, 90, 110, 70};
        g.fillRect(cx - s(150), by - s(50), s(300), s(60));
        for (int i = 0; i < xs.length; i++) {
            int tx = cx + s(xs[i]), top = by - s(hs[i]);
            g.fillRect(tx - s(17), top, s(34), s(hs[i]) + s(10));
            g.fillPolygon(new int[]{tx - s(22), tx + s(22), tx}, new int[]{top, top, top - s(34)}, 3);
        }
        if (windows) {
            g.setColor(new Color(0xFFC96B));
            for (int i = 0; i < xs.length; i++) g.fillRect(cx + s(xs[i]) - s(3), by - s(hs[i]) + s(22), s(6), s(10));
        }
    }
}
