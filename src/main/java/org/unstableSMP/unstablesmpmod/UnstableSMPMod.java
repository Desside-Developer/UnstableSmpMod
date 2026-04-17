package org.unstableSMP.unstablesmpmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnstableSMPMod implements ModInitializer {

    public static final String MOD_ID = "unstablesmpmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ImmortalManager immortalManager;
    private static NickManager nickManager;
    private static SkinManager skinManager;
    private static ModConfig config;

    @Override
    public void onInitialize() {
        config = ModConfig.load();
        immortalManager = new ImmortalManager();
        nickManager = new NickManager();
        skinManager = new SkinManager();

        // --- Event listeners ---
        DeathListener.register(immortalManager, config);
        ImmortalListener.register(immortalManager);
        JoinLeaveListener.register(config);
        ChatListener.register(nickManager);

        // Re-apply nick & skin on every join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            nickManager.onPlayerJoin(handler.player);
            skinManager.onPlayerJoin(server, handler.player);
        });

        // --- Commands ---
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ImmortalCommand.register(dispatcher, immortalManager);
            NickCommand.register(dispatcher, nickManager);
            SkinCommand.register(dispatcher, skinManager);
        });

        // --- Server stop ---
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            immortalManager.clearAll();
            LOGGER.info("UnstableSMP disabled!");
        });

        LOGGER.info("UnstableSMP enabled!");
    }

    public static ImmortalManager getImmortalManager() { return immortalManager; }
    public static NickManager getNickManager()         { return nickManager; }
    public static SkinManager getSkinManager()         { return skinManager; }
    public static ModConfig getConfig()                { return config; }
}