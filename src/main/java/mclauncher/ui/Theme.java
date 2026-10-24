package mclauncher.ui;

import javax.swing.*;
import java.awt.*;

/** Farben, Schriften und globale UI-Einstellungen. */
public final class Theme {
    private Theme() {}

    public static final Color BG = new Color(0x090E0B);
    public static final Color SIDEBAR = new Color(0x0C130F);
    public static final Color PANEL = new Color(0x111A14);
    public static final Color PANEL2 = new Color(0x182319);
    public static final Color BORDER = new Color(0x22332A);
    public static final Color ACCENT = new Color(0x22C55E);
    public static final Color ACCENT_HI = new Color(0x4ADE80);
    public static final Color ACCENT_DARK = new Color(0x15803D);
    public static final Color TEXT = new Color(0xE8F1EB);
    public static final Color MUTED = new Color(0x8B9C91);
    public static final Color DANGER = new Color(0xDC2626);

    public static Font font(int style, float size) {
        Font base = UIManager.getFont("Label.font");
        if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        return base.deriveFont(style, size);
    }

    /** Schrift für Symbol-Zeichen (Navigation). */
    public static Font symbolFont(float size) {
        for (String name : new String[]{"Segoe UI Symbol", "DejaVu Sans", "Arial Unicode MS", Font.DIALOG}) {
            Font f = new Font(name, Font.PLAIN, 12);
            if (f.canDisplay('\u2699') && f.canDisplay('\u25B6')) return f.deriveFont(size);
        }
        return new Font(Font.DIALOG, Font.PLAIN, 12).deriveFont(size);
    }

    public static void install() {
        UIManager.put("Panel.background", BG);
        UIManager.put("Viewport.background", BG);
        UIManager.put("ScrollPane.background", BG);
        UIManager.put("List.background", PANEL);
        UIManager.put("List.selectionBackground", new Color(0x1E3A2A));
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("TextField.background", PANEL2);
        UIManager.put("PasswordField.background", PANEL2);
        UIManager.put("TextArea.background", PANEL);
        UIManager.put("ComboBox.background", PANEL2);
        UIManager.put("Button.background", PANEL2);
        UIManager.put("OptionPane.background", BG);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("ProgressBar.foreground", ACCENT);
        UIManager.put("ProgressBar.background", new Color(0x1B2A21));
        UIManager.put("Component.focusColor", ACCENT);
        UIManager.put("Component.borderColor", BORDER);
        UIManager.put("Slider.thumbColor", ACCENT);
        UIManager.put("Slider.trackValueColor", ACCENT);
        UIManager.put("CheckBox.icon.selectedBackground", ACCENT);
        UIManager.put("CheckBox.icon.checkmarkColor", Color.BLACK);
        UIManager.put("ScrollBar.thumb", new Color(0x2A3B30));
        UIManager.put("ScrollBar.track", BG);
    }
}
