package mclauncher.ui;

import mclauncher.I18n;
import mclauncher.Instance;
import mclauncher.Util;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Screenshots des aktuellen Profils als Vorschaubilder. */
public class ScreenshotsPage extends Page {
    private final DefaultListModel<Path> model = new DefaultListModel<>();
    private final JList<Path> list = new JList<>(model);
    private final Map<Path, ImageIcon> thumbs = new ConcurrentHashMap<>();
    private final Set<Path> loading = ConcurrentHashMap.newKeySet();
    private final JLabel status = new JLabel(" ");

    public ScreenshotsPage(MainWindow win) {
        super(win);
        Widgets.GlowButton open = new Widgets.GlowButton(I18n.t("Ordner öffnen"), Widgets.Style.DARK);
        Widgets.GlowButton reload = new Widgets.GlowButton(I18n.t("Aktualisieren"), Widgets.Style.DARK);
        open.addActionListener(e -> {
            Instance cur = AppState.current();
            if (cur != null) Widgets.openFolder(this, cur.screenshotsDir());
        });
        reload.addActionListener(e -> reload());
        add(header("Screenshots", I18n.t("Aufgenommen mit F2 im Spiel – Doppelklick zum Öffnen"), reload, open), BorderLayout.NORTH);

        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);
        list.setFixedCellWidth(214);
        list.setFixedCellHeight(150);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                super.getListCellRendererComponent(l, v, i, sel, foc);
                Path p = (Path) v;
                setHorizontalAlignment(SwingConstants.CENTER);
                setVerticalTextPosition(SwingConstants.BOTTOM);
                setHorizontalTextPosition(SwingConstants.CENTER);
                String n = p.getFileName().toString();
                setText(n.length() > 26 ? n.substring(0, 24) + "…" : n);
                ImageIcon icon = thumbs.get(p);
                setIcon(icon);
                if (icon == null) loadThumb(p);
                return this;
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && list.getSelectedValue() != null) {
                    try {
                        Desktop.getDesktop().open(list.getSelectedValue().toFile());
                    } catch (Exception ignored) { }
                }
            }
        });
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        status.setForeground(Theme.MUTED);
        add(sp, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    @Override public void onShow() { reload(); }
    @Override protected void onProfileChanged() { if (isShowing()) reload(); }

    private void reload() {
        model.clear();
        Instance cur = AppState.current();
        if (cur == null) {
            status.setText(I18n.t("Kein Profil ausgewählt."));
            return;
        }
        Path dir = cur.screenshotsDir();
        if (!Files.isDirectory(dir)) {
            status.setText(I18n.t("Noch keine Screenshots in \"") + cur.name + "\".");
            return;
        }
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> files = s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                    .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                    .limit(200).toList();
            files.forEach(model::addElement);
            status.setText(files.size() + I18n.t(" Screenshots in \"") + cur.name + "\"");
        } catch (IOException ex) {
            status.setText(I18n.t("Fehler: ") + ex.getMessage());
        }
    }

    private void loadThumb(Path p) {
        if (!loading.add(p)) return;
        Util.async(() -> {
            try {
                BufferedImage img = ImageIO.read(p.toFile());
                if (img != null) {
                    Image scaled = img.getScaledInstance(190, 106, Image.SCALE_SMOOTH);
                    thumbs.put(p, new ImageIcon(scaled));
                }
            } catch (IOException ignored) {
            } finally {
                thumbs.putIfAbsent(p, new ImageIcon());   // verhindert endloses Neuladen bei Fehlern
                loading.remove(p);
                SwingUtilities.invokeLater(list::repaint);
            }
        });
    }
}
