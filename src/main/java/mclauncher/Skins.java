package mclauncher;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/** Lädt den Minecraft-Skin und schneidet den Kopf (Gesicht + Hut-Ebene) aus. */
public final class Skins {
    private Skins() {}

    /** Gibt den Kopf als 8x8-Bild (bei HD-Skins entsprechend größer) zurück, oder null. */
    public static BufferedImage head(String url) throws IOException {
        if (url == null || url.isBlank()) return null;
        URI uri = URI.create(url);
        // Skins liegen bei Mojang; fremde Adressen werden nicht geladen
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !uri.getHost().equals("textures.minecraft.net")) return null;
        String file = url.substring(url.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9]", "");
        if (file.isEmpty()) file = Integer.toHexString(url.hashCode());
        Path cache = AppPaths.CACHE.resolve("skins").resolve(file + ".png");
        Util.download(url, cache, null, false);
        return extractHead(ImageIO.read(cache.toFile()));
    }

    public static BufferedImage extractHead(BufferedImage skin) {
        if (skin == null || skin.getWidth() < 64 || skin.getHeight() < 32) return null;
        int f = skin.getWidth() / 64;
        BufferedImage out = new BufferedImage(8 * f, 8 * f, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(skin.getSubimage(8 * f, 8 * f, 8 * f, 8 * f), 0, 0, null);    // Gesicht
        g.drawImage(skin.getSubimage(40 * f, 8 * f, 8 * f, 8 * f), 0, 0, null);   // Hut-Ebene
        g.dispose();
        return out;
    }
}
