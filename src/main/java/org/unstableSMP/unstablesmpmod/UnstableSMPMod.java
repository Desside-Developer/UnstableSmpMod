package org.unstableSMP.unstablesmpmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnstableSMPMod implements ModInitializer {

    public static final String MOD_ID = "unstablesmpmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ImmortalManager immortalManager;
    private static ModConfig config;

    @Override
    public void onInitialize() {
        config = ModConfig.load();
        immortalManager = new ImmortalManager();

        DeathListener.register(immortalManager, config);
        ImmortalListener.register(immortalManager);
        JoinLeaveListener.register(config);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ImmortalCommand.register(dispatcher, immortalManager));

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            immortalManager.clearAll();
            LOGGER.info("UnstableSMP disabled!");
        });

        LOGGER.info("UnstableSMP enabled!");
    }

    public static ImmortalManager getImmortalManager() { return immortalManager; }
    public static ModConfig getConfig() { return config; }
}