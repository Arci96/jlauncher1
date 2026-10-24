package mclauncher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Neuigkeiten: die letzten Patch Notes von Mojang. */
public final class News {
    private News() {}

    public record Item(String title, String subtitle, String imageUrl) {}

    private static final String BASE = "https://launchercontent.mojang.com";

    public static List<Item> latest(int n) throws IOException {
        JsonObject o = Util.getJson(BASE + "/v2/javaPatchNotes.json");
        List<Item> out = new ArrayList<>();
        if (!o.has("entries")) return out;
        JsonArray entries = o.getAsJsonArray("entries");
        for (JsonElement e : entries) {
            if (out.size() >= n) break;
            JsonObject entry = e.getAsJsonObject();
            String title = Util.str(entry, "title");
            if (title == null) continue;
            String version = Util.str(entry, "version");
            String text = Util.str(entry, "shortText");
            String sub = (version != null ? "Version " + version : "") + (text != null ? (version != null ? " – " : "") + text : "");
            String img = null;
            if (entry.has("image") && entry.get("image").isJsonObject()) {
                String u = Util.str(entry.getAsJsonObject("image"), "url");
                if (u != null) img = u.startsWith("http") ? u : BASE + (u.startsWith("/") ? "" : "/") + u;
            }
            out.add(new Item(title, sub, img));
        }
        return out;
    }
}
