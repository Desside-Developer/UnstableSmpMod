package org.unstableSMP.unstablesmpmod;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.scores.PlayerTeam;

public class DeathListener {

    public static void register(ImmortalManager immortalManager, ModConfig config) {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof ServerPlayer player)) return;

            MinecraftServer server = ((net.minecraft.server.level.ServerLevel) player.level()).getServer();
            if (server == null) return;

            BlockPos deathPos = player.blockPosition();

            // Wither sound for nearby players
            if (config.witherSoundEnabled) {
                int dist = config.witherSoundDistance;

                for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                    if (!online.level().equals(player.level())) continue;
                    double d = online.blockPosition().distSqr(deathPos);
                    if (d <= (double) dist * dist) {
                        online.connection.send(new ClientboundSoundPacket(
                                net.minecraft.core.Holder.direct(SoundEvents.WITHER_SPAWN),
                                SoundSource.HOSTILE,
                                online.getX(), online.getY(), online.getZ(),
                                1.0f, 1.0f, 0L
                        ));
                    }
                }
            }

            // Death ban
            if (config.deathBanEnabled) {
                String reason = config.deathBanReason;
                server.getPlayerList().getBans().add(
                        new net.minecraft.server.players.UserBanListEntry(
                                new net.minecraft.server.players.NameAndId(
                                        player.getUUID(), player.getName().getString()
                                ),
                                null, null, null, reason
                        )
                );
                player.connection.disconnect(Component.literal(reason));
            }
        });
    }
}