package mclauncher;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Konten: Offline (frei wählbarer Name) oder Microsoft (Device-Code-Flow).
 * Für Microsoft brauchst du eine eigene Azure-App-Registrierung (siehe README).
 */
public final class Auth {
    private Auth() {}

    public record Account(String name, String uuid, String token, String userType) {}

    public record DeviceCode(String deviceCode, String userCode, String verificationUri, int interval, int expiresIn) {}

    private static final String TENANT = "https://login.microsoftonline.com/consumers/oauth2/v2.0";
    private static final String SCOPE = "XboxLive.signin offline_access";

    // ------------------------------------------------------------------ Offline

    public static Account offline(String name) {
        if (name == null || name.isBlank()) name = "Player";
        String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
        return new Account(name, uuid, "0", "legacy");
    }

    /** Liefert das aktuell gewählte Konto (bei Microsoft mit frisch erneuertem Token). */
    public static Account current() throws IOException {
        Store.Config c = Store.data.config;
        if ("microsoft".equals(c.accountType)) return refreshMicrosoft();
        return offline(c.offlineName);
    }

    // ------------------------------------------------------------------ Microsoft: Device Code

    public static DeviceCode startDeviceFlow(String clientId) throws IOException {
        if (clientId == null || clientId.isBlank()) throw new IOException("Bitte zuerst eine Azure-Client-ID eintragen.");
        Util.Resp r = Util.postForm(TENANT + "/devicecode", Map.of("client_id", clientId, "scope", SCOPE));
        if (!r.ok()) throw new IOException("Device-Code-Anfrage fehlgeschlagen: " + r.body());
        JsonObject o = r.json();
        return new DeviceCode(o.get("device_code").getAsString(), o.get("user_code").getAsString(),
                o.get("verification_uri").getAsString(), o.get("interval").getAsInt(), o.get("expires_in").getAsInt());
    }

    /** Wartet, bis der Benutzer sich im Browser angemeldet hat, und führt dann die komplette Auth-Kette aus. */
    public static Account finishDeviceFlow(String clientId, DeviceCode dc, BooleanSupplier cancelled) throws IOException {
        long deadline = System.currentTimeMillis() + dc.expiresIn() * 1000L;
        int interval = Math.max(dc.interval(), 3);
        while (System.currentTimeMillis() < deadline) {
            if (cancelled.getAsBoolean()) throw new IOException("Anmeldung abgebrochen.");
            sleep(interval * 1000L);
            Util.Resp r = Util.postForm(TENANT + "/token", Map.of(
                    "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id", clientId,
                    "device_code", dc.deviceCode()));
            JsonObject o = r.json();
            if (r.ok()) {
                Store.data.config.msRefreshToken = o.get("refresh_token").getAsString();
                return minecraftLogin(o.get("access_token").getAsString());
            }
            String err = Util.str(o, "error");
            if ("authorization_pending".equals(err)) continue;
            if ("slow_down".equals(err)) { interval += 5; continue; }
            throw new IOException("Microsoft-Anmeldung fehlgeschlagen: " + Util.str(o, "error_description"));
        }
        throw new IOException("Der Code ist abgelaufen. Bitte erneut versuchen.");
    }

    private static Account refreshMicrosoft() throws IOException {
        Store.Config c = Store.data.config;
        if (c.msRefreshToken == null || c.msRefreshToken.isBlank()) {
            throw new IOException("Nicht bei Microsoft angemeldet. Bitte unter 'Konto' anmelden.");
        }
        Util.Resp r = Util.postForm(TENANT + "/token", Map.of(
                "grant_type", "refresh_token",
                "client_id", c.msClientId,
                "refresh_token", c.msRefreshToken,
                "scope", SCOPE));
        if (!r.ok()) throw new IOException("Token-Erneuerung fehlgeschlagen - bitte neu anmelden. " + r.body());
        JsonObject o = r.json();
        if (o.has("refresh_token")) c.msRefreshToken = o.get("refresh_token").getAsString();
        Account acc = minecraftLogin(o.get("access_token").getAsString());
        Store.save();
        return acc;
    }

    /** Microsoft-Token -> Xbox Live -> XSTS -> Minecraft-Token -> Profil */
    private static Account minecraftLogin(String msAccessToken) throws IOException {
        // 1. Xbox Live
        JsonObject xblProps = new JsonObject();
        xblProps.addProperty("AuthMethod", "RPS");
        xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProps.addProperty("RpsTicket", "d=" + msAccessToken);
        JsonObject xblReq = new JsonObject();
        xblReq.add("Properties", xblProps);
        xblReq.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblReq.addProperty("TokenType", "JWT");
        JsonObject xbl = checked(Util.postJson("https://user.auth.xboxlive.com/user/authenticate", xblReq), "Xbox Live");
        String xblToken = xbl.get("Token").getAsString();

        // 2. XSTS
        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        xstsProps.add("UserTokens", userTokens);
        JsonObject xstsReq = new JsonObject();
        xstsReq.add("Properties", xstsProps);
        xstsReq.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsReq.addProperty("TokenType", "JWT");
        Util.Resp xr = Util.postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsReq);
        if (!xr.ok()) throw new IOException(xstsError(xr));
        JsonObject xsts = xr.json();
        String uhs = xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

        // 3. Minecraft
        JsonObject mcReq = new JsonObject();
        mcReq.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xsts.get("Token").getAsString());
        JsonObject mc = checked(Util.postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcReq), "Minecraft");
        String mcToken = mc.get("access_token").getAsString();

        // 4. Profil (404 = Konto besitzt Minecraft Java nicht)
        String body;
        try {
            body = Util.get("https://api.minecraftservices.com/minecraft/profile", Map.of("Authorization", "Bearer " + mcToken));
        } catch (IOException e) {
            throw new IOException("Kein Minecraft-Java-Profil gefunden. Besitzt das Konto das Spiel? (" + e.getMessage() + ")");
        }
        JsonObject profile = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
        String id = profile.get("id").getAsString();
        String name = profile.get("name").getAsString();
        String uuid = id.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");

        Store.data.config.msName = name;
        Store.data.config.msUuid = uuid;
        return new Account(name, uuid, mcToken, "msa");
    }

    private static JsonObject checked(Util.Resp r, String what) throws IOException {
        if (!r.ok()) throw new IOException(what + "-Anmeldung fehlgeschlagen: HTTP " + r.status() + " " + r.body());
        return r.json();
    }

    private static String xstsError(Util.Resp r) {
        try {
            long code = r.json().get("XErr").getAsLong();
            if (code == 2148916233L) return "Dieses Microsoft-Konto hat kein Xbox-Konto. Bitte zuerst auf xbox.com eines anlegen.";
            if (code == 2148916238L) return "Kinderkonto: Ein Erwachsener muss das Konto zu einer Familie hinzufügen.";
            if (code == 2148916235L) return "Xbox Live ist in deinem Land nicht verfügbar.";
        } catch (Exception ignored) { }
        return "XSTS-Anmeldung fehlgeschlagen: " + r.body();
    }

    private static void sleep(long ms) throws IOException {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Abgebrochen", e);
        }
    }
}
