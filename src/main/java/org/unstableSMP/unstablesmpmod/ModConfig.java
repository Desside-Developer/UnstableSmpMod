package org.unstableSMP.unstablesmpmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("unstablesmpmod.json");

    // Death ban
    public boolean deathBanEnabled = true;
    public String deathBanReason = "You Died with a Hardcore Plugin";

    // Proximity messages
    public boolean proximityMessagesEnabled = true;
    public int proximityDistance = 500;

    // Wither sound
    public boolean witherSoundEnabled = true;
    public int witherSoundDistance = 500;

    public static ModConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                ModConfig cfg = GSON.fromJson(reader, ModConfig.class);
                if (cfg != null) return cfg;
            } catch (IOException e) {
                UnstableSMPMod.LOGGER.error("Failed to load config, using defaults", e);
            }
        }
        ModConfig defaults = new ModConfig();
        defaults.save();
        return defaults;
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            UnstableSMPMod.LOGGER.error("Failed to save config", e);
        }
    }
}