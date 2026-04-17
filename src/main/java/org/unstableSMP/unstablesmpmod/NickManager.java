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

    /**
     * Apply the display-name component to the player entity.
     * This covers:
     *  - nametag (setCustomName + setCustomNameVisible)
     *  - tab list (setTabListName)
     *  - chat messages are intercepted in ChatListener using getDisplayName()
     */
    private void applyNick(ServerPlayer player, String displayName) {
        Component nameComponent = Component.literal(displayName);
        // Custom name / nametag above head
        player.setCustomName(nameComponent);
        player.setCustomNameVisible(true);
        // Tab list display name — update via PlayerList which handles the packet broadcast
        MinecraftServer server = player.createCommandSourceStack().getServer();
        if (server != null) {
            // refreshTabListName triggers tab list update for this player on all clients
            server.getPlayerList().broadcastAll(
                    ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player))
            );
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