package org.unstableSMP.unstablesmpmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ImmortalCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ImmortalManager immortalManager) {
        dispatcher.register(
                Commands.literal("immortal")
                        .requires(source -> !source.isPlayer() ||
                                source.getServer().getPlayerList().isOp(source.getPlayer().nameAndId()))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    String playerName = StringArgumentType.getString(context, "player");

                                    ServerPlayer target = source.getServer()
                                            .getPlayerList()
                                            .getPlayerByName(playerName);

                                    if (target == null) {
                                        source.sendFailure(Component.literal("§cPlayer " + playerName + " is not online!"));
                                        return 0;
                                    }

                                    if (immortalManager.hasImmortal(target)) {
                                        immortalManager.removeImmortal(target);
                                        source.sendSuccess(
                                                () -> Component.literal("§aImmortality for " + target.getName().getString() + " has been disabled!"),
                                                true
                                        );
                                        target.sendSystemMessage(Component.literal("§eYour immortality has been disabled!"));
                                    } else {
                                        immortalManager.addImmortal(target);
                                        source.sendSuccess(
                                                () -> Component.literal("§aImmortality for " + target.getName().getString() + " has been enabled!"),
                                                true
                                        );
                                        target.sendSystemMessage(Component.literal(
                                                "§eYou received immortality! Your HP cannot drop below half a heart (unless you hold a totem)!"
                                        ));
                                    }

                                    return 1;
                                })
                        )
        );
    }
}