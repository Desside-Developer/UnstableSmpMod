package org.unstableSMP.unstablesmpmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /nick <player> <newNick>
 * /nick <player> reset
 *
 * Requires OP. Sets or resets the display name shown in chat, tab list, and nametag.
 */
public class NickCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, NickManager nickManager) {
        dispatcher.register(
                Commands.literal("nick")
                        .requires(source -> !source.isPlayer() ||
                                source.getServer().getPlayerList().isOp(source.getPlayer().nameAndId()))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("nick", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            String playerName = StringArgumentType.getString(context, "player");
                                            String nick = StringArgumentType.getString(context, "nick");

                                            ServerPlayer target = source.getServer()
                                                    .getPlayerList()
                                                    .getPlayerByName(playerName);

                                            if (target == null) {
                                                source.sendFailure(Component.literal(
                                                        "§cPlayer §e" + playerName + "§c is not online!"));
                                                return 0;
                                            }

                                            if (nick.equalsIgnoreCase("reset")) {
                                                nickManager.resetNick(target);
                                                source.sendSuccess(
                                                        () -> Component.literal("§aReset nick for §e" + playerName + "§a to their real name."),
                                                        true);
                                                target.sendSystemMessage(Component.literal("§eYour display name has been reset."));
                                            } else {
                                                if (nick.length() > 32) {
                                                    source.sendFailure(Component.literal("§cNick is too long (max 32 characters)!"));
                                                    return 0;
                                                }
                                                nickManager.setNick(target, nick);
                                                source.sendSuccess(
                                                        () -> Component.literal("§aSet nick for §e" + playerName + "§a to §b" + nick),
                                                        true);
                                                target.sendSystemMessage(Component.literal(
                                                        "§eYour display name has been changed to §b" + nick));
                                            }
                                            return 1;
                                        })
                                )
                        )
        );
    }
}