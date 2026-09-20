package com.flyserver.flymc;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/** Renders the android with the classic Steve player look. */
public class FlyBrainRenderer extends HumanoidMobRenderer<FlyBrainEntity, AvatarRenderState, PlayerModel<AvatarRenderState>> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            "minecraft", "textures/entity/player/wide/steve.png");

    public FlyBrainRenderer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new AvatarRenderState();
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return TEXTURE;
    }
}
