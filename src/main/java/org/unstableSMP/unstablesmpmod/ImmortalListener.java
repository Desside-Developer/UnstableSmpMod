package org.unstableSMP.unstablesmpmod;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

public class ImmortalListener {

    private static final float MIN_HEALTH = 1.0f; // half a heart

    public static void register(ImmortalManager immortalManager) {

        // Cancel death — fires before AFTER_DEATH
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (!(entity instanceof ServerPlayer player)) return true;
            if (!immortalManager.hasImmortal(player)) return true;
            if (isHoldingTotem(player)) return true;

            player.setHealth(MIN_HEALTH);
            return false;
        });

        // Clamp damage so health never drops below MIN_HEALTH.
        // CRITICAL: do NOT call player.hurt() inside this callback — it re-triggers
        // ALLOW_DAMAGE and causes infinite recursion → StackOverflowError crash.
        // Instead: if the hit would kill/floor the player, set health to MIN_HEALTH
        // and cancel the damage entirely (return false).
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player)) return true;
            if (!immortalManager.hasImmortal(player)) return true;
            if (isHoldingTotem(player)) return true;

            float currentHealth = player.getHealth();
            if (currentHealth - amount <= MIN_HEALTH) {
                // Would go to or below floor — pin to floor and cancel the hit
                player.setHealth(MIN_HEALTH);
                return false;
            }
            return true;
        });
    }

    private static boolean isHoldingTotem(ServerPlayer player) {
        return player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
                || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }
}