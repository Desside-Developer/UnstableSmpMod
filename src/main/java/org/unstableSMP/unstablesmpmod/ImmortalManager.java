package org.unstableSMP.unstablesmpmod;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ImmortalManager {

    private final Set<UUID> immortalPlayers = new HashSet<>();

    public void addImmortal(ServerPlayer player) {
        immortalPlayers.add(player.getUUID());
    }

    public void removeImmortal(ServerPlayer player) {
        immortalPlayers.remove(player.getUUID());
    }

    public boolean hasImmortal(ServerPlayer player) {
        return immortalPlayers.contains(player.getUUID());
    }

    public void clearAll() {
        immortalPlayers.clear();
    }
}