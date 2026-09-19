package com.flyserver.flymc;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * In-game settings (ModMenu → FlyBrain → config), zero external
 * dependencies. Save writes config/flybrain.json and applies live.
 */
public class FlyBrainConfigScreen extends Screen {
    private final BrainConfig cfg = BrainConfig.load();
    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();

    private record Row(String label, EditBox box, Consumer<String> setter) {
        void apply() {
            setter.accept(box.getValue().trim());
        }
    }

    public FlyBrainConfigScreen(Screen parent) {
        super(Component.literal("FlyBrain config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int colW = 190, labelH = 10, boxH = 18, gap = 4, rowH = labelH + boxH + gap;
        int leftX = this.width / 2 - colW - 6;
        int rightX = this.width / 2 + 6;
        int top = 40;

        rows.clear();
        addRow("baseUrl", leftX, top, colW, v -> cfg.baseUrl = v);
        addRow("brainSteps (1-5000)", rightX, top, colW, v -> cfg.brainSteps = parseInt(v, cfg.brainSteps));
        addRow("username", leftX, top + rowH, colW, v -> cfg.username = v);
        addRow("intervalTicks (1-200)", rightX, top + rowH, colW, v -> cfg.intervalTicks = parseInt(v, cfg.intervalTicks));
        addRow("password", leftX, top + rowH * 2, colW, v -> cfg.password = v);
        addRow("maxEntities (1-64)", rightX, top + rowH * 2, colW, v -> cfg.maxEntities = parseInt(v, cfg.maxEntities));
        addRow("stimRegion", leftX, top + rowH * 3, colW, v -> cfg.stimRegion = v);
        addRow("driveRadius (16-512)", rightX, top + rowH * 3, colW, v -> cfg.driveRadius = parseDouble(v, cfg.driveRadius));
        addRow("targetType", leftX, top + rowH * 4, colW, v -> cfg.targetType = v);
        addRow("turnRateThreshold (0-1)", rightX, top + rowH * 4, colW, v -> cfg.turnRateThreshold = parseDouble(v, cfg.turnRateThreshold));
        addRow("attackRateThreshold (0-1)", leftX, top + rowH * 5, colW, v -> cfg.attackRateThreshold = parseDouble(v, cfg.attackRateThreshold));

        int btnY = top + rowH * 5 + boxH + 8;
        addRenderableWidget(Button.builder(Component.literal("Save & Apply"), b -> saveAndClose())
                .bounds(this.width / 2 - 100, btnY, 200, 20).build());
    }

    private void addRow(String label, int x, int y, int w, Consumer<String> setter) {
        var labelWidget = new EditBox(this.font, x, y + labelH, w, 18, Component.literal(label)) {
            @Override
            public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
                super.renderWidget(g, mouseX, mouseY, delta);
            }
        };
        // label above the box
        addRenderableWidget(new AbstractWidget(x, y, w, 9, Component.literal(label)) {
            @Override
            protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
                g.drawString(FlyBrainConfigScreen.this.font, Component.literal(label), x, y, 0xA0A0A0);
            }
            @Override
            public void onPress() {}
        });
        labelWidget.setMaxLength(256);
        labelWidget.setValue(currentValue(label));
        addRenderableWidget(labelWidget);
        rows.add(new Row(label, labelWidget, setter));
    }

    private String currentValue(String label) {
        return switch (label) {
            case "baseUrl" -> cfg.baseUrl;
            case "username" -> cfg.username;
            case "password" -> cfg.password;
            case "stimRegion" -> cfg.stimRegion;
            case "targetType" -> cfg.targetType;
            case "brainSteps (1-5000)" -> String.valueOf(cfg.brainSteps);
            case "intervalTicks (1-200)" -> String.valueOf(cfg.intervalTicks);
            case "maxEntities (1-64)" -> String.valueOf(cfg.maxEntities);
            case "driveRadius (16-512)" -> String.valueOf(cfg.driveRadius);
            case "turnRateThreshold (0-1)" -> String.valueOf(cfg.turnRateThreshold);
            case "attackRateThreshold (0-1)" -> String.valueOf(cfg.attackRateThreshold);
            default -> "";
        };
    }

    private void saveAndClose() {
        for (Row r : rows) r.apply();
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
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
