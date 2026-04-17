package org.unstableSMP.unstablesmpmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commands:
 *  /skin <player> url <imageUrl>          — apply skin from direct image URL (via mineskin.org)
 *  /skin <player> player <mojangUsername> — copy skin from another Mojang account
 *  /skin <player> reset                   — revert to the player's real skin
 *
 * Requires OP. Skin changes are async; a success/failure message is sent when done.
 */
public class SkinCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, SkinManager skinManager) {

        // /skin <player> reset
        var skinBase = Commands.literal("skin")
                .requires(source -> !source.isPlayer() ||
                        source.getServer().getPlayerList().isOp(source.getPlayer().nameAndId()))
                .then(Commands.argument("player", StringArgumentType.word())

                        // reset subcommand
                        .then(Commands.literal("reset")
                                .executes(ctx -> {
                                    CommandSourceStack source = ctx.getSource();
                                    ServerPlayer target = resolveTarget(source, StringArgumentType.getString(ctx, "player"));
                                    if (target == null) return 0;

                                    source.sendSuccess(() -> Component.literal("§eResetting skin for §b" + target.getName().getString() + "§e..."), false);

                                    skinManager.resetSkin(
                                            source.getServer(), target,
                                            () -> source.sendSuccess(
                                                    () -> Component.literal("§aSkin for §b" + target.getName().getString() + "§a has been reset!"),
                                                    true),
                                            err -> source.sendFailure(Component.literal("§c" + err))
                                    );
                                    return 1;
                                })
                        )

                        // url subcommand
                        .then(Commands.literal("url")
                                .then(Commands.argument("imageUrl", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            CommandSourceStack source = ctx.getSource();
                                            String playerName = StringArgumentType.getString(ctx, "player");
                                            String imageUrl = StringArgumentType.getString(ctx, "imageUrl");
                                            ServerPlayer target = resolveTarget(source, playerName);
                                            if (target == null) return 0;

                                            source.sendSuccess(() -> Component.literal("§eApplying skin from URL for §b" + playerName + "§e... (this may take a few seconds)"), false);

                                            skinManager.setSkinByUrl(
                                                    source.getServer(), target, imageUrl,
                                                    () -> source.sendSuccess(
                                                            () -> Component.literal("§aSkin applied successfully to §b" + playerName),
                                                            true),
                                                    err -> source.sendFailure(Component.literal("§cFailed to apply skin: " + err))
                                            );
                                            return 1;
                                        })
                                )
                        )

                        // player subcommand
                        .then(Commands.literal("player")
                                .then(Commands.argument("mojangName", StringArgumentType.word())
                                        .executes(ctx -> {
                                            CommandSourceStack source = ctx.getSource();
                                            String playerName = StringArgumentType.getString(ctx, "player");
                                            String mojangName = StringArgumentType.getString(ctx, "mojangName");
                                            ServerPlayer target = resolveTarget(source, playerName);
                                            if (target == null) return 0;

                                            source.sendSuccess(() -> Component.literal("§eFetching skin of §b" + mojangName + "§e for §b" + playerName + "§e..."), false);

                                            skinManager.setSkinByPlayer(
                                                    source.getServer(), target, mojangName,
                                                    () -> source.sendSuccess(
                                                            () -> Component.literal("§aSkin of §b" + mojangName + "§a applied to §b" + playerName),
                                                            true),
                                                    err -> source.sendFailure(Component.literal("§cFailed to fetch skin: " + err))
                                            );
                                            return 1;
                                        })
                                )
                        )
                );

        dispatcher.register(skinBase);
    }

    private static ServerPlayer resolveTarget(CommandSourceStack source, String playerName) {
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(Component.literal("§cPlayer §e" + playerName + "§c is not online!"));
        }
        return target;
    }
}