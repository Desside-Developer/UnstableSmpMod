package org.unstableSMP.unstablesmpmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages custom display names (nicks) for players.
 * Nick affects: tab list, chat, nametag above head.
 * Data is persisted to nicks.json in the config directory.
 */
public class NickManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DATA_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("unstablesmpmod_nicks.json");

    // UUID -> custom nick
    private final Map<UUID, String> nicks = new HashMap<>();

    public NickManager() {
        load();
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Set a custom nick for a player and immediately update their display name. */
    public void setNick(ServerPlayer player, String nick) {
        nicks.put(player.getUUID(), nick);
        applyNick(player, nick);
        save();
    }

    /** Remove the custom nick, restoring the original Minecraft username. */
    public void resetNick(ServerPlayer player) {
        nicks.remove(player.getUUID());
        applyNick(player, player.getGameProfile().name());
        save();
    }

    /** Returns the custom nick if set, otherwise the real username. */
    public String getDisplayName(ServerPlayer player) {
        return nicks.getOrDefault(player.getUUID(), player.getGameProfile().name());
    }

    /** Returns true if the player has a custom nick. */
    public boolean hasNick(UUID uuid) {
        return nicks.containsKey(uuid);
    }

    /**
     * Called on player join — re-applies stored nick so the player sees
     * correct name immediately and the tab list reflects it from the start.
     */
    public void onPlayerJoin(ServerPlayer player) {
        String nick = nicks.get(player.getUUID());
        if (nick != null) {
            applyNick(player, nick);
        }
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    // Cached reflection field for ServerPlayer.tabListDisplayName
    private static java.lang.reflect.Field tabListDisplayNameField;

    static {
        try {
            tabListDisplayNameField = net.minecraft.server.level.ServerPlayer.class
                    .getDeclaredField("tabListDisplayName");
            tabListDisplayNameField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            // Field name may differ across mappings — will be handled at runtime
            UnstableSMPMod.LOGGER.warn("Could not find tabListDisplayName field: " + e.getMessage());
        }
    }

    /**
     * Apply the display-name component to the player entity.
     *  - nametag (setCustomName + setCustomNameVisible)
     *  - tab list: set private tabListDisplayName field via reflection,
     *              then broadcast UPDATE_DISPLAY_NAME packet
     *  - chat messages are intercepted in ChatListener using getDisplayName()
     */
    private void applyNick(ServerPlayer player, String displayName) {
        Component nameComponent = Component.literal(displayName);
        // Nametag above head
        player.setCustomName(nameComponent);
        player.setCustomNameVisible(true);

        // Tab list — set private field via reflection
        if (tabListDisplayNameField != null) {
            try {
                tabListDisplayNameField.set(player, nameComponent);
            } catch (IllegalAccessException e) {
                UnstableSMPMod.LOGGER.warn("Could not set tabListDisplayName: " + e.getMessage());
            }
        }

        // Broadcast the display name change to all online players
        MinecraftServer server = player.createCommandSourceStack().getServer();
        if (server != null) {
            // Use UPDATE_DISPLAY_NAME action — lighter than full re-init
            ClientboundPlayerInfoUpdatePacket packet =
                    ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player));
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                online.connection.send(packet);
            }
        }
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void load() {
        if (!Files.exists(DATA_PATH)) return;
        try (Reader reader = Files.newBufferedReader(DATA_PATH)) {
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> raw = GSON.fromJson(reader, type);
            if (raw != null) {
                raw.forEach((k, v) -> nicks.put(UUID.fromString(k), v));
            }
        } catch (IOException e) {
            UnstableSMPMod.LOGGER.error("Failed to load nicks data", e);
        }
    }

    private void save() {
        Map<String, String> raw = new HashMap<>();
        nicks.forEach((k, v) -> raw.put(k.toString(), v));
        try (Writer writer = Files.newBufferedWriter(DATA_PATH)) {
            GSON.toJson(raw, writer);
        } catch (IOException e) {
            UnstableSMPMod.LOGGER.error("Failed to save nicks data", e);
        }
    }
}