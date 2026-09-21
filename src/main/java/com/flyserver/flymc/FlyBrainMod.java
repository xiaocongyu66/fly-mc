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
import java.util.Map;
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
    private static volatile FlyBrainMod instance;
    private static volatile BrainConfig config;
    private static volatile BrainBridge bridge;
    private int tickCounter = 0;
    /** One outstanding drive per entity — results apply when ready. */
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    /** Last brain decision per entity, applied every tick (gait). */
    private final Map<UUID, Gait> gaits = new ConcurrentHashMap<>();
    /** Persistent brain session per entity (membrane state persists). */
    private final Map<UUID, String> sessions = new ConcurrentHashMap<>();
    /** Entities with a live SSE motor stream. */
    private final Set<UUID> streaming = ConcurrentHashMap.newKeySet();
    /** Pain window per entity: strong stimulus + reflex until this wall time. */
    private final Map<UUID, Long> painUntil = new ConcurrentHashMap<>();
    /** Entities whose session already got its individuality signature. */
    private final Set<UUID> seeded = ConcurrentHashMap.newKeySet();

    /** Called from FlyBrainEntity.hurt on the server thread: reflex + pain. */
    public static void onPain(FlyBrainEntity mob, float amount) {
        FlyBrainMod self = instance;
        if (self == null) return;
        long now = System.currentTimeMillis();
        self.painUntil.put(mob.getUUID(), now + 1500);
        // immediate reflex: jolt + random turn + hop (spinal, no brain)
        double turn = java.util.concurrent.ThreadLocalRandom.current().nextBoolean() ? 1.0 : -1.0;
        self.gaits.put(mob.getUUID(), new Gait(0.6, turn, true));
    }

    /** Held motor command between brain updates: think slow, act fast. */
    private record Gait(double forward, double turn, boolean jump) {}
    /** Rolling spike-bucket window per entity, fed by the live SSE stream. */
    private record StreamAcc(double[] buckets, int n) {}

    @Override
    public void onInitialize() {
        instance = this;
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
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        BlockPos origin = BlockPos.containing(
                server.getPlayerList().getPlayers().get(0).position());
        AABB area = new AABB(origin).inflate(config.driveRadius);

        // collect androids once per tick
        List<FlyBrainEntity> androids = new java.util.ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            androids.addAll(level.getEntitiesOfClass(FlyBrainEntity.class, area,
                    net.minecraft.world.entity.Entity::isAlive));
            if (androids.size() >= config.maxEntities) break;
        }

        // act: apply the held gait every tick (continuous locomotion)
        for (FlyBrainEntity mob : androids) {
            Gait g = gaits.get(mob.getUUID());
            if (g != null) applyGait(mob, g);
        }

        // think: schedule a brain update per entity on the interval
        if (++tickCounter < config.intervalTicks) return;
        tickCounter = 0;
        int driven = 0;
        for (FlyBrainEntity mob : androids) {
            if (driven >= config.maxEntities) break;
            driveEntity(mob);
            driven++;
        }
    }

    /** Live motor updates at simulation speed (~130ms/tick) from the SSE
     *  activity stream — gait refreshes continuously while the brain runs. */
    private void startStream(UUID id, String sid, BrainBridge bridgeRef) {
        if (streaming.contains(id)) return;
        streaming.add(id);
        Thread t = new Thread(() -> {
            double[] buckets = new double[3];
            int n = 0;
            final double[][] acc = {buckets};
            final int[] cnt = {n};
            bridgeRef.wsActivityStream(sid, tickIds -> {
                for (long nid : tickIds) acc[0][(int) (nid % 3)] += 1.0;
                cnt[0]++;
                if (cnt[0] >= 8) {  // ~1s of brain time: refresh the gait
                    double f = acc[0][0] / cnt[0], tn = acc[0][1] / cnt[0];
                    boolean j = acc[0][2] / cnt[0] > config.attackRateThreshold;
                    Gait g = new Gait(f, tn, j);
                    var server = java.util.concurrent.CompletableFuture.completedFuture(g);
                    gaits.put(id, g);
                    acc[0] = new double[3];
                    cnt[0] = 0;
                }
            });
            streaming.remove(id);
        }, "flybrain-stream-" + id.toString().substring(0, 8));
        t.setDaemon(true);
        t.start();
    }

    private void applyGait(FlyBrainEntity mob, Gait g) {
        Vec3 look = mob.getLookAngle();
        double speed = Math.max(0.0, Math.min(0.25, g.forward() * 0.06));
        Vec3 v = new Vec3(look.x * speed, mob.getDeltaMovement().y, look.z * speed);
        if (g.turn() > 0.15) {
            mob.setYRot(mob.getYRot() + (float) Math.min(3.0, g.turn() * 3.0));
        }
        if (g.jump() && mob.onGround()) {
            v = new Vec3(v.x, 0.42, v.z);
        }
        mob.setDeltaMovement(v);
    }

    private void driveEntity(FlyBrainEntity mob) {
        // vision: an 8-column azimuth frame of the whole visual field
        // (llama.cpp mtmd analog: frame → columns → per-column injection);
        // each column's current = the strongest stimulus seen in that sector
        double[] sector = new double[8];
        java.util.Arrays.fill(sector, -1.0);
        double nearest = 64.0;
        if (mob.level() != null) {
            Vec3 look = mob.getLookAngle();
            double base = Math.toDegrees(Math.atan2(look.z, look.x));
            for (var p : mob.level().players()) {
                double d = p.distanceTo(mob);
                nearest = Math.min(nearest, d);
                Vec3 toP = p.position().subtract(mob.position());
                double ang = Math.toDegrees(
                        Math.atan2(toP.z, toP.x) - Math.toRadians(base));
                float az = (float) (((ang / 360.0) + 1.0) % 1.0);
                int bin = (int) (az * 8.0) % 8;
                sector[bin] = Math.max(sector[bin], Math.max(5.0, 100.0 - d * 8.0));
            }
        }
        java.util.List<float[]> frame = new java.util.ArrayList<>();
        for (int k = 0; k < 8; k++) {
            float cur = sector[k] < 0 ? 5.0f : (float) sector[k];
            frame.add(new float[]{(k + 0.5f) / 8.0f, cur});
        }
        boolean inPain = painUntil.getOrDefault(mob.getUUID(), 0L) > System.currentTimeMillis();
        final float stimCurrent = inPain ? 150f : (float) Math.max(5.0, 100.0 - nearest * 8.0);
        final java.util.List<float[]> stimFrame = inPain ? java.util.List.of() : frame;
        BrainBridge bridgeRef = bridge;
        BrainConfig cfg = config;
        if (!inFlight.add(mob.getUUID())) return;  // previous drive still running
        CompletableFuture.supplyAsync(() -> {
                    String sid = sessions.computeIfAbsent(mob.getUUID(),
                            u -> bridgeRef.createSession());
                    if (sid == null) return List.<BrainBridge.Action>of();
                    startStream(mob.getUUID(), sid, bridgeRef);
                    if (seeded.add(mob.getUUID())) {
                        // individuality: a one-time random signature stimulus
                        // decorrelates otherwise-identical trajectories
                        float sig = java.util.concurrent.ThreadLocalRandom.current().nextFloat();
                        bridgeRef.drive(sid, cfg.stimRegion,
                                java.util.List.of(new float[]{sig, 60f}), 60f, 20);
                    }
                    List<BrainBridge.Action> a =
                            bridgeRef.drive(sid, cfg.stimRegion, stimFrame, stimCurrent, cfg.brainSteps);
                    if (a.isEmpty()) sessions.remove(mob.getUUID());  // stale session
                    return a;
                })
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

    /** Runs on the server thread: channel-bucket VNC rates into a gait
     *  command that persists (applied every tick) until the next decision. */
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
        gaits.put(mob.getUUID(), new Gait(forward, turn, jump));
    }
}
