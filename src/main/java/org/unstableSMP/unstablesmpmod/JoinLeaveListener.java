package org.unstableSMP.unstablesmpmod;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class JoinLeaveListener {

    public static void register(ModConfig config) {

        // Join message
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!config.proximityMessagesEnabled) return;

            ServerPlayer player = handler.player;
            BlockPos pos = player.blockPosition();
            int dist = config.proximityDistance;
            Component message = Component.literal(player.getName().getString() + " joined the game");

            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                if (!online.level().equals(player.level())) continue;
                double d = online.blockPosition().distSqr(pos);
                if (d <= (double) dist * dist) {
                    online.sendSystemMessage(message);
                }
            }
        });

        // Quit message
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (!config.proximityMessagesEnabled) return;

            ServerPlayer player = handler.player;
            BlockPos pos = player.blockPosition();
            int dist = config.proximityDistance;
            Component message = Component.literal(player.getName().getString() + " left the game");

            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                if (online.equals(player)) continue;
                if (!online.level().equals(player.level())) continue;
                double d = online.blockPosition().distSqr(pos);
                if (d <= (double) dist * dist) {
                    online.sendSystemMessage(message);
                }
            }
        });
    }
}