package com.flyserver.flymc;

import com.google.gson.JsonElement;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives configured mobs with the fly brain: game state → stimulus,
 * brain motor output (VNC actions) → movement channels bucketed by
 * neuron_id (0 = forward, 1 = turn, 2 = jump/attack).
 */
public class FlyBrainMod implements ModInitializer {
    private static volatile BrainConfig config;
    private static volatile BrainBridge bridge;
    private int tickCounter = 0;
    /** One outstanding drive per entity — results apply when ready. */
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    @Override
    public void onInitialize() {
        FlyBrainRegistry.init();
        config = BrainConfig.load();
        bridge = new BrainBridge(config);
        ServerTickEvents.END_SERVER_TICK.register(this::onEndTick);
        System.out.println("[flybrain] initialized — server " + config.baseUrl
                + ", target " + config.targetType + ", region " + config.stimRegion);
    }

    /** Called by the config screen: applies new settings live (no restart). */
    public static void applyConfig(BrainConfig newCfg) {
        config = newCfg;
        bridge = new BrainBridge(newCfg);
        System.out.println("[flybrain] config applied — server " + newCfg.baseUrl
                + ", region " + newCfg.stimRegion);
    }

    private void onEndTick(MinecraftServer server) {
        if (++tickCounter < config.intervalTicks) return;
        tickCounter = 0;
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        BlockPos origin = BlockPos.containing(
                server.getPlayerList().getPlayers().get(0).position());
        AABB area = new AABB(origin).inflate(config.driveRadius);
        int driven = 0;
        for (ServerLevel level : server.getAllLevels()) {
            if (driven >= config.maxEntities) return;
            List<FlyBrainEntity> androids = level.getEntitiesOfClass(FlyBrainEntity.class, area,
                    net.minecraft.world.entity.Entity::isAlive);
            for (FlyBrainEntity mob : androids) {
                if (driven >= config.maxEntities) break;
                driveEntity(mob);
                driven++;
            }
        }
    }

    private void driveEntity(FlyBrainEntity mob) {
        // stimulus strength: closer player → stronger drive (novelty/pressure)
        double nearest = 64.0;
        if (mob.level() != null && !mob.level().players().isEmpty()) {
            for (var p : mob.level().players()) {
                double d = p.distanceTo(mob);
                if (d < nearest) nearest = d;
            }
        }
        float current = (float) Math.max(5.0, 100.0 - nearest * 8.0);
        BrainBridge bridgeRef = bridge;
        BrainConfig cfg = config;
        if (!inFlight.add(mob.getUUID())) return;  // previous drive still running
        CompletableFuture.supplyAsync(
                () -> bridgeRef.drive(cfg.stimRegion, current, cfg.brainSteps))
            .thenAccept(actions -> {
                inFlight.remove(mob.getUUID());
                if (actions.isEmpty() || !mob.isAlive()) return;
                var server = mob.level().getServer();
                if (server != null) {
                    server.execute(() -> applyActions(mob, actions));
                } else {
                    applyActions(mob, actions);
                }
            })
            .exceptionally(ex -> {
                inFlight.remove(mob.getUUID());
                return null;
            });
    }

    /** Runs on the server thread: channel-bucket VNC rates into motion. */
    private void applyActions(FlyBrainEntity mob, List<BrainBridge.Action> actions) {
        if (!mob.isAlive()) return;
        double forward = 0, turn = 0;
        boolean jump = false;
        for (BrainBridge.Action a : actions) {
            int channel = (int) (a.neuronId() % 3);
            switch (channel) {
                case 0 -> forward += a.rate();
                case 1 -> turn += a.rate();
                case 2 -> { if (a.rate() > config.attackRateThreshold) jump = true; }
            }
        }

        Vec3 look = mob.getLookAngle();
        double speed = Math.min(0.3, forward * 0.05);
        Vec3 v = new Vec3(look.x * speed, mob.getDeltaMovement().y, look.z * speed);
        if (turn > config.turnRateThreshold) {
            mob.setYRot(mob.getYRot() + 30f);
        }
        if (jump) {
            v = new Vec3(v.x, 0.42, v.z);
        }
        mob.setDeltaMovement(v);
    }
}
