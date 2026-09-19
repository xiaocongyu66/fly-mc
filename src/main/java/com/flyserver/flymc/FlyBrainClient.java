package com.flyserver.flymc;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/** Client entrypoint: F8 opens the in-game config screen. */
public class FlyBrainClient implements ClientModInitializer {
    private static KeyMapping openConfig;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("flybrain", "config"));
        openConfig = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.flybrain.config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfig.consumeClick()) {
                client.setScreen(new FlyBrainConfigScreen(null));
            }
        });
    }
}
