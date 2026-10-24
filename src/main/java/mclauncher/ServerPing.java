package mclauncher;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Minecraft "Server List Ping" - zeigt Spielerzahl und Latenz eines Servers. */
public final class ServerPing {
    private ServerPing() {}

    public record Result(boolean online, int players, int max, long pingMs, String motd) {
        public static final Result OFFLINE = new Result(false, 0, 0, -1, "");

        public int signalLevel() {
            if (!online) return 0;
            if (pingMs < 80) return 4;
            if (pingMs < 160) return 3;
            if (pingMs < 300) return 2;
            return 1;
        }
    }

    private static final Map<String, Result> CACHE = new ConcurrentHashMap<>();

    public static Result cached(String address) { return CACHE.getOrDefault(address, null); }

    /** Pingt alle gespeicherten Server parallel; ruft onUpdate nach jedem Ergebnis auf. */
    public static void refreshAll(Runnable onUpdate) {
        for (Store.ServerEntry s : Store.data.servers) {
            String addr = s.address;
            Util.async(() -> {
                CACHE.put(addr, ping(addr));
                if (onUpdate != null) onUpdate.run();
            });
        }
    }

    public static Result ping(String address) {
        String host = address.trim();
        int port = 25565;
        int idx = host.lastIndexOf(':');
        if (idx > 0 && host.indexOf(':') == idx) {
            try {
                port = Integer.parseInt(host.substring(idx + 1).trim());
                host = host.substring(0, idx).trim();
            } catch (NumberFormatException ignored) { }
        }
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 3000);
            s.setSoTimeout(3000);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));

            ByteArrayOutputStream hs = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(hs);
            writeVarInt(h, 0x00);
            writeVarInt(h, 765);
            byte[] hostBytes = host.getBytes(StandardCharsets.UTF_8);
            writeVarInt(h, hostBytes.length);
            h.write(hostBytes);
            h.writeShort(port);
            writeVarInt(h, 1);
            writeVarInt(out, hs.size());
            out.write(hs.toByteArray());
            writeVarInt(out, 1);
            writeVarInt(out, 0x00);
            out.flush();

            long t0 = System.nanoTime();
            readVarInt(in);                       // Paketlänge
            readVarInt(in);                       // Paket-ID
            int len = readVarInt(in);
            if (len <= 0 || len > (1 << 22)) return Result.OFFLINE;
            byte[] data = new byte[len];
            in.readFully(data);
            long ms = (System.nanoTime() - t0) / 1_000_000;

            // Ping-Paket für genauere Latenz
            try {
                long t1 = System.nanoTime();
                writeVarInt(out, 9);
                writeVarInt(out, 1);
                out.writeLong(t1);
                out.flush();
                readVarInt(in);
                readVarInt(in);
                in.readLong();
                ms = (System.nanoTime() - t1) / 1_000_000;
            } catch (IOException ignored) { }

            JsonObject o = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
            int online = 0, max = 0;
            if (o.has("players")) {
                JsonObject p = o.getAsJsonObject("players");
                if (p.has("online")) online = p.get("online").getAsInt();
                if (p.has("max")) max = p.get("max").getAsInt();
            }
            return new Result(true, online, max, ms, motd(o));
        } catch (Exception e) {
            return Result.OFFLINE;
        }
    }

    private static String motd(JsonObject o) {
        try {
            if (!o.has("description")) return "";
            JsonElement d = o.get("description");
            if (d.isJsonPrimitive()) return strip(d.getAsString());
            JsonObject obj = d.getAsJsonObject();
            StringBuilder sb = new StringBuilder();
            if (obj.has("text")) sb.append(obj.get("text").getAsString());
            if (obj.has("extra")) {
                for (JsonElement e : obj.getAsJsonArray("extra")) {
                    if (e.isJsonPrimitive()) sb.append(e.getAsString());
                    else if (e.getAsJsonObject().has("text")) sb.append(e.getAsJsonObject().get("text").getAsString());
                }
            }
            return strip(sb.toString());
        } catch (Exception e) {
            return "";
        }
    }

    private static String strip(String s) {
        return s.replaceAll("§.", "").replace('\n', ' ').trim();
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int result = 0, shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return result;
            shift += 7;
            if (shift > 35) throw new IOException("VarInt zu lang");
        }
    }
}
