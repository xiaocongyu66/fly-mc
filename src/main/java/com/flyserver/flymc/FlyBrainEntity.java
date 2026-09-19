package com.flyserver.flymc;

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
}
