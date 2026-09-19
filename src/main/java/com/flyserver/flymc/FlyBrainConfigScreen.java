package com.flyserver.flymc;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** In-game settings (ModMenu → FlyBrain → config). Applies live. */
public final class FlyBrainConfigScreen {
    private FlyBrainConfigScreen() {}

    public static Screen build(Screen parent) {
        BrainConfig cfg = BrainConfig.load();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("title.flybrain.config"));
        builder.setSavingRunnable(() -> {
            BrainConfig.save(cfg);
            FlyBrainMod.applyConfig(cfg);
        });
        ConfigEntryBuilder eb = builder.entryBuilder();
        ConfigCategory cat = builder.getOrCreateCategory(Component.literal("fly-server"));

        cat.addEntry(eb.startStrField(Component.literal("baseUrl"), cfg.baseUrl)
                .setTooltip(Component.literal("fly-server 地址，如 http://127.0.0.1:8321"))
                .saveConsumer(v -> cfg.baseUrl = v)
                .build());
        cat.addEntry(eb.startStrField(Component.literal("username"), cfg.username)
                .saveConsumer(v -> cfg.username = v)
                .build());
        cat.addEntry(eb.startStrField(Component.literal("password"), cfg.password)
                .saveConsumer(v -> cfg.password = v)
                .build());
        cat.addEntry(eb.startStrField(Component.literal("stimRegion"), cfg.stimRegion)
                .setTooltip(Component.literal("standard/full 底座的真实脑区名，如 visual_projection"))
                .saveConsumer(v -> cfg.stimRegion = v)
                .build());
        cat.addEntry(eb.startIntField(Component.literal("brainSteps"), cfg.brainSteps)
                .setMin(1).setMax(5000)
                .setTooltip(Component.literal("每次驱动模拟的脑 tick 数"))
                .saveConsumer(v -> cfg.brainSteps = v)
                .build());
        cat.addEntry(eb.startIntField(Component.literal("intervalTicks"), cfg.intervalTicks)
                .setMin(1).setMax(200)
                .setTooltip(Component.literal("游戏 tick 间隔（20 = 每秒驱动一次）"))
                .saveConsumer(v -> cfg.intervalTicks = v)
                .build());
        cat.addEntry(eb.startStrField(Component.literal("targetType"), cfg.targetType)
                .setTooltip(Component.literal("被果蝇脑驱动的生物，如 minecraft:zombie"))
                .saveConsumer(v -> cfg.targetType = v)
                .build());
        cat.addEntry(eb.startIntField(Component.literal("maxEntities"), cfg.maxEntities)
                .setMin(1).setMax(64)
                .saveConsumer(v -> cfg.maxEntities = v)
                .build());
        cat.addEntry(eb.startDoubleField(Component.literal("driveRadius"), cfg.driveRadius)
                .setMin(16.0).setMax(512.0)
                .saveConsumer(v -> cfg.driveRadius = v)
                .build());

        return builder.build();
    }
}
