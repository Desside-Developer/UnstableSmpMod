package org.unstableSMP.unstablesmpmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /immortal <player>          — toggle immortality
 * /immortal <player> true     — force enable immortality
 * /immortal <player> false    — force disable immortality
 *
 * Requires OP.
 * Immortal players cannot drop below half a heart (0.5f HP) unless holding a Totem of Undying.
 */
public class ImmortalCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ImmortalManager immortalManager) {
        dispatcher.register(
                Commands.literal("immortal")
                        .requires(source -> !source.isPlayer() ||
                                source.getServer().getPlayerList().isOp(source.getPlayer().nameAndId()))
                        .then(Commands.argument("player", StringArgumentType.word())

                                // /immortal <player>  — toggle
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer target = resolveTarget(source,
                                            StringArgumentType.getString(context, "player"));
                                    if (target == null) return 0;

                                    boolean newState = !immortalManager.hasImmortal(target);
                                    applyImmortal(source, target, immortalManager, newState);
                                    return 1;
                                })

                                // /immortal <player> true|false  — explicit
                                .then(Commands.argument("state", BoolArgumentType.bool())
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            ServerPlayer target = resolveTarget(source,
                                                    StringArgumentType.getString(context, "player"));
                                            if (target == null) return 0;

                                            boolean newState = BoolArgumentType.getBool(context, "state");
                                            applyImmortal(source, target, immortalManager, newState);
                                            return 1;
                                        })
                                )
                        )
        );
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static void applyImmortal(CommandSourceStack source, ServerPlayer target,
                                      ImmortalManager immortalManager, boolean enable) {
        String name = target.getName().getString();

        if (enable) {
            if (immortalManager.hasImmortal(target)) {
                source.sendFailure(Component.literal("§e" + name + " §calready has immortality!"));
                return;
            }
            immortalManager.addImmortal(target);
            source.sendSuccess(
                    () -> Component.literal("§aImmortality §2ENABLED§a for §e" + name),
                    true);
            target.sendSystemMessage(Component.literal(
                    "§aYou have been granted §2immortality§a! " +
                            "§7Your HP will not drop below half a heart (unless you hold a Totem)."));
        } else {
            if (!immortalManager.hasImmortal(target)) {
                source.sendFailure(Component.literal("§e" + name + " §cdoes not have immortality!"));
                return;
            }
            immortalManager.removeImmortal(target);
            source.sendSuccess(
                    () -> Component.literal("§aImmortality §4DISABLED§a for §e" + name),
                    true);
            target.sendSystemMessage(Component.literal("§cYour immortality has been removed."));
        }
    }

    private static ServerPlayer resolveTarget(CommandSourceStack source, String playerName) {
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(Component.literal("§cPlayer §e" + playerName + "§c is not online!"));
        }
        return target;
    }
}