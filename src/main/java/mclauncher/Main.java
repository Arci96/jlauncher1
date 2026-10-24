package mclauncher;

import com.formdev.flatlaf.FlatDarkLaf;
import mclauncher.ui.MainWindow;
import mclauncher.ui.Theme;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        FlatDarkLaf.setup();
        Theme.install();
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("ProgressBar.arc", 12);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("TabbedPane.showTabSeparators", true);

        Store.load();
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}
