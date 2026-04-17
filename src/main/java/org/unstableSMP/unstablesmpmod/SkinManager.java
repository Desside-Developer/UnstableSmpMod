package org.unstableSMP.unstablesmpmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manages custom skins for players.
 *
 * Supports:
 *  - /skin <player> url <url>          — skin from a direct image URL
 *  - /skin <player> player <mojangNick> — skin copied from another Mojang account
 *  - /skin <player> reset              — revert to the player's own skin
 *
 * Uses minetools.eu API (free, no auth required) to fetch textures property.
 * After applying, sends respawn packets to all online players so they see the new skin immediately.
 */
public class SkinManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DATA_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("unstablesmpmod_skins.json");

    // Minetools profile API — returns JSON with textures property
    private static final String MINETOOLS_UUID_API = "https://api.minetools.eu/uuid/%s";
    private static final String MINETOOLS_PROFILE_API = "https://api.minetools.eu/profile/%s";

    // Stored as value+signature pairs
    private static final class SkinData {
        String value;
        String signature;
        SkinData(String value, String signature) {
            this.value = value;
            this.signature = signature;
        }
    }

    private final Map<UUID, SkinData> skins = new HashMap<>();

    public SkinManager() {
        load();
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Set skin from a direct image URL.
     * The skin is uploaded to mineskin.org which signs it properly.
     * Async — callback is called on the server thread via server.execute().
     */
    public void setSkinByUrl(MinecraftServer server, ServerPlayer player, String imageUrl,
                             Runnable onSuccess, java.util.function.Consumer<String> onError) {
        CompletableFuture.runAsync(() -> {
            try {
                // Use mineskin.org API to generate a signed skin from URL
                SkinData data = fetchSkinFromMineskin(imageUrl);
                server.execute(() -> {
                    skins.put(player.getUUID(), data);
                    save();
                    applySkinToPlayer(server, player, data);
                    onSuccess.run();
                });
            } catch (Exception e) {
                server.execute(() -> onError.accept(e.getMessage()));
            }
        });
    }

    /**
     * Set skin copied from a Mojang account by username.
     * Async.
     */
    public void setSkinByPlayer(MinecraftServer server, ServerPlayer player, String mojangNick,
                                Runnable onSuccess, java.util.function.Consumer<String> onError) {
        CompletableFuture.runAsync(() -> {
            try {
                SkinData data = fetchSkinFromMojangByName(mojangNick);
                server.execute(() -> {
                    skins.put(player.getUUID(), data);
                    save();
                    applySkinToPlayer(server, player, data);
                    onSuccess.run();
                });
            } catch (Exception e) {
                server.execute(() -> onError.accept(e.getMessage()));
            }
        });
    }

    /**
     * Reset the skin — remove stored skin and trigger respawn with the
     * player's own original textures (fetched fresh from Mojang session server).
     */
    public void resetSkin(MinecraftServer server, ServerPlayer player,
                          Runnable onSuccess, java.util.function.Consumer<String> onError) {
        CompletableFuture.runAsync(() -> {
            try {
                // Fetch the player's original skin by their real UUID
                SkinData data = fetchSkinFromMojangByUUID(player.getUUID());
                server.execute(() -> {
                    skins.remove(player.getUUID());
                    save();
                    applySkinToPlayer(server, player, data);
                    onSuccess.run();
                });
            } catch (Exception e) {
                server.execute(() -> {
                    // Even if fetch fails, remove stored skin so next respawn is clean
                    skins.remove(player.getUUID());
                    save();
                    onError.accept("Skin reset, but could not reload original: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Called on player join — re-apply stored skin if any.
     */
    public void onPlayerJoin(MinecraftServer server, ServerPlayer player) {
        SkinData data = skins.get(player.getUUID());
        if (data != null) {
            // Small delay so the player fully joins before the respawn packet
            server.execute(() -> applySkinToPlayer(server, player, data));
        }
    }

    // ── Skin application ───────────────────────────────────────────────────────

    /**
     * Injects the textures property into the player's GameProfile and sends
     * ClientboundPlayerInfoRemove + ClientboundPlayerInfoUpdate + ClientboundRespawn
     * to all online players so the skin updates without a disconnect.
     */
    private void applySkinToPlayer(MinecraftServer server, ServerPlayer player, SkinData data) {
        try {
            GameProfile profile = player.getGameProfile();

            // Replace the textures property in the profile
            profile.properties().removeAll("textures");
            profile.properties().put("textures", new Property("textures", data.value, data.signature));

            // Broadcast player info remove + update so all clients reload the skin
            List<ServerPlayer> allPlayers = server.getPlayerList().getPlayers();

            ClientboundPlayerInfoRemovePacket removePacket =
                    new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID()));

            ClientboundPlayerInfoUpdatePacket addPacket =
                    ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player));

            for (ServerPlayer online : allPlayers) {
                online.connection.send(removePacket);
                online.connection.send(addPacket);
            }

            // Send respawn packet to the player themselves so they see the skin in F5
            player.connection.send(new ClientboundRespawnPacket(
                    player.createCommonSpawnInfo((net.minecraft.server.level.ServerLevel) player.level()),
                    (byte) 3 // keep all data
            ));

            // Reposition the player to prevent teleport glitch
            player.connection.teleport(
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot()
            );

        } catch (Exception e) {
            UnstableSMPMod.LOGGER.error("Failed to apply skin to player " + player.getName().getString(), e);
        }
    }

    // ── Skin fetching ──────────────────────────────────────────────────────────

    /** Fetch skin textures property from Mojang API by UUID */
    private SkinData fetchSkinFromMojangByUUID(UUID uuid) throws Exception {
        String uuidStr = uuid.toString().replace("-", "");
        String profileUrl = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuidStr + "?unsigned=false";
        return fetchSkinFromProfileUrl(profileUrl);
    }

    /** Fetch skin textures property from Mojang by player name (name → UUID → profile) */
    private SkinData fetchSkinFromMojangByName(String name) throws Exception {
        // Step 1: name → UUID via Mojang API
        String uuidJson = httpGet("https://api.mojang.com/users/profiles/minecraft/" + name);
        JsonObject uuidObj = GSON.fromJson(uuidJson, JsonObject.class);
        if (uuidObj == null || !uuidObj.has("id")) {
            throw new Exception("Player '" + name + "' not found on Mojang");
        }
        String uuidStr = uuidObj.get("id").getAsString();

        // Step 2: UUID → profile with textures
        String profileUrl = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuidStr + "?unsigned=false";
        return fetchSkinFromProfileUrl(profileUrl);
    }

    /** Parse textures property from a Mojang session server profile URL */
    private SkinData fetchSkinFromProfileUrl(String profileUrl) throws Exception {
        String json = httpGet(profileUrl);
        JsonObject profile = GSON.fromJson(json, JsonObject.class);
        if (profile == null || !profile.has("properties")) {
            throw new Exception("Invalid profile response from Mojang");
        }
        for (var elem : profile.getAsJsonArray("properties")) {
            JsonObject prop = elem.getAsJsonObject();
            if ("textures".equals(prop.get("name").getAsString())) {
                String value = prop.get("value").getAsString();
                String signature = prop.has("signature") ? prop.get("signature").getAsString() : "";
                return new SkinData(value, signature);
            }
        }
        throw new Exception("No textures property found in profile");
    }

    /**
     * Upload a skin image URL to mineskin.org and get back a signed textures property.
     * Mineskin is a free public service that signs Minecraft skins properly.
     */
    private SkinData fetchSkinFromMineskin(String imageUrl) throws Exception {
        // POST to mineskin API
        URL url = new URL("https://api.mineskin.org/generate/url");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "UnstableSMPMod/1.0");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        String body = "{\"url\":\"" + imageUrl + "\",\"variant\":\"classic\"}";
        conn.getOutputStream().write(body.getBytes());

        int code = conn.getResponseCode();
        InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
        String json = new String(is.readAllBytes());

        if (code != 200) {
            throw new Exception("Mineskin API error (" + code + "): " + json);
        }

        JsonObject resp = GSON.fromJson(json, JsonObject.class);
        if (!resp.has("data")) {
            throw new Exception("Unexpected mineskin response: " + json);
        }
        JsonObject texture = resp.getAsJsonObject("data").getAsJsonObject("texture");
        String value = texture.get("value").getAsString();
        String signature = texture.get("signature").getAsString();
        return new SkinData(value, signature);
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    private void load() {
        if (!Files.exists(DATA_PATH)) return;
        try (Reader reader = Files.newBufferedReader(DATA_PATH)) {
            Type type = new TypeToken<Map<String, Map<String, String>>>() {}.getType();
            Map<String, Map<String, String>> raw = GSON.fromJson(reader, type);
            if (raw != null) {
                raw.forEach((k, v) -> skins.put(
                        UUID.fromString(k),
                        new SkinData(v.get("value"), v.get("signature"))
                ));
            }
        } catch (IOException e) {
            UnstableSMPMod.LOGGER.error("Failed to load skins data", e);
        }
    }

    private void save() {
        Map<String, Map<String, String>> raw = new HashMap<>();
        skins.forEach((k, v) -> {
            Map<String, String> entry = new HashMap<>();
            entry.put("value", v.value);
            entry.put("signature", v.signature);
            raw.put(k.toString(), entry);
        });
        try (Writer writer = Files.newBufferedWriter(DATA_PATH)) {
            GSON.toJson(raw, writer);
        } catch (IOException e) {
            UnstableSMPMod.LOGGER.error("Failed to save skins data", e);
        }
    }

    // ── HTTP helper ────────────────────────────────────────────────────────────

    private String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "UnstableSMPMod/1.0");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        int code = conn.getResponseCode();
        InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
        return new String(is.readAllBytes());
    }
}