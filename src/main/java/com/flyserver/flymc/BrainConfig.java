package com.flyserver.flymc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class BrainConfig {
    public String baseUrl = "http://127.0.0.1:8321";
    public String username = "admin";
    public String password = "flyserver";
    /** Brain region stimulated each call (the "senses"). */
    public String stimRegion = "visual_projection";
    /** Brain ticks simulated per drive call. */
    public int brainSteps = 50;
    /** Game ticks between drive calls (20 = 1 second). */
    public int intervalTicks = 20;
    /** Entity type driven by the brain (mojmap id). */
    public String targetType = "minecraft:zombie";
    public int maxEntities = 8;
    public double driveRadius = 128.0;
    public double attackRateThreshold = 0.85;
    public double turnRateThreshold = 0.6;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("flybrain.json");
    }

    public static BrainConfig load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                return GSON.fromJson(Files.readString(p), BrainConfig.class);
            }
            BrainConfig def = new BrainConfig();
            save(def);
            return def;
        } catch (Exception e) {
            System.err.println("[flybrain] config load failed, using defaults: " + e);
            return new BrainConfig();
        }
    }

    public static void save(BrainConfig cfg) {
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(cfg));
        } catch (IOException e) {
            System.err.println("[flybrain] config save failed: " + e);
        }
    }
}
