package com.flyserver.flymc;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * In-game settings (press F8 in-game), zero external dependencies.
 * Save writes config/flybrain.json and applies live.
 */
public class FlyBrainConfigScreen extends Screen {
    private static final int COL_W = 190, BOX_H = 18, GAP = 4, ROW_H = 10 + BOX_H + GAP;
    private final BrainConfig cfg = BrainConfig.load();
    private final Screen parent;
    private final List<Consumer<Void>> apply = new ArrayList<>();

    public FlyBrainConfigScreen(Screen parent) {
        super(Component.literal("FlyBrain config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int leftX = this.width / 2 - COL_W - 6;
        int rightX = this.width / 2 + 6;
        int top = 40;
        apply.clear();

        addBox(leftX, top, cfg.baseUrl, "http://127.0.0.1:8321", v -> cfg.baseUrl = v);
        addBox(rightX, top, String.valueOf(cfg.brainSteps), "brainSteps", v -> cfg.brainSteps = parseInt(v, cfg.brainSteps));
        addBox(leftX, top + ROW_H, cfg.username, "username", v -> cfg.username = v);
        addBox(rightX, top + ROW_H, String.valueOf(cfg.intervalTicks), "intervalTicks", v -> cfg.intervalTicks = parseInt(v, cfg.intervalTicks));
        addBox(leftX, top + ROW_H * 2, cfg.password, "password", v -> cfg.password = v);
        addBox(rightX, top + ROW_H * 2, String.valueOf(cfg.maxEntities), "maxEntities", v -> cfg.maxEntities = parseInt(v, cfg.maxEntities));
        addBox(leftX, top + ROW_H * 3, cfg.stimRegion, "stimRegion", v -> cfg.stimRegion = v);
        addBox(rightX, top + ROW_H * 3, String.valueOf(cfg.driveRadius), "driveRadius", v -> cfg.driveRadius = parseDouble(v, cfg.driveRadius));
        addBox(leftX, top + ROW_H * 4, cfg.targetType, "targetType", v -> cfg.targetType = v);
        addBox(rightX, top + ROW_H * 4, String.valueOf(cfg.turnRateThreshold), "turnRateThr", v -> cfg.turnRateThreshold = parseDouble(v, cfg.turnRateThreshold));
        addBox(leftX, top + ROW_H * 5, String.valueOf(cfg.attackRateThreshold), "attackRateThr", v -> cfg.attackRateThreshold = parseDouble(v, cfg.attackRateThreshold));

        int btnY = top + ROW_H * 5 + BOX_H + 8;
        addRenderableWidget(Button.builder(Component.literal("Save & Apply"), b -> saveAndClose())
                .bounds(this.width / 2 - 100, btnY, 200, 20).build());
    }

    private void addBox(int x, int y, String value, String hint, Consumer<String> setter) {
        EditBox box = new EditBox(this.font, x, y, COL_W, BOX_H, Component.literal(hint));
        box.setMaxLength(256);
        box.setHint(Component.literal(hint));
        box.setValue(value);
        addRenderableWidget(box);
        apply.add(v -> setter.accept(box.getValue().trim()));
    }

    private void saveAndClose() {
        for (Consumer<Void> a : apply) a.accept(null);
        BrainConfig.save(cfg);
        FlyBrainMod.applyConfig(cfg);
        onClose();
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (Exception e) { return fallback; }
    }

    private static double parseDouble(String s, double fallback) {
        try { return Double.parseDouble(s); } catch (Exception e) { return fallback; }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null && parent != null) {
            this.minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }
}
