package com.flyserver.flymc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * The fly-brain android: no vanilla AI goals at all — its movement comes
 * from the connectome simulation (game state in, VNC motor actions out).
 */
public class FlyBrainEntity extends PathfinderMob {
    public FlyBrainEntity(EntityType<? extends FlyBrainEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void hurt(DamageSource source, float amount) {
        float before = getHealth();
        super.hurt(source, amount);
        if (getHealth() < before && !level().isClientSide() && level() instanceof ServerLevel) {
            FlyBrainMod.onPain(this, amount);
        }
    }
}
