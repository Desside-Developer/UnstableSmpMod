package org.unstableSMP.unstablesmpmod;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Intercepts chat messages and replaces the sender's name with their
 * custom nick (if one is set), so it appears correctly in the chat box.
 *
 * Uses the Fabric ServerMessageEvents.ALLOW_CHAT_MESSAGE event to
 * broadcast a custom-formatted message and cancel the original.
 */
public class ChatListener {

    public static void register(NickManager nickManager) {

        // CHAT_MESSAGE fires before the message is sent to players
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            // If no custom nick, let vanilla handle it
            if (!nickManager.hasNick(sender.getUUID())) return true;

            // Build a custom chat message: <CustomNick> text
            String nick = nickManager.getDisplayName(sender);
            String rawText = message.decoratedContent().getString();
            Component formatted = Component.literal("§7<§r" + nick + "§7>§r " + rawText);

            // Send to everyone including the sender
            MinecraftServer server = sender.createCommandSourceStack().getServer();

            if (server != null) {
                for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                    online.sendSystemMessage(formatted);
                }
            }

            // Return false to cancel the original vanilla message
            return false;
        });
    }
}