package dev.likip.cbclongrangevisuals.client;

import dev.likip.cbclongrangevisuals.VisualConfig;
import dev.likip.cbclongrangevisuals.network.VisualFrame;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client-owned visual objects, not Minecraft entities; all access happens on the client thread. */
public final class ClientVisuals {
    public static final ClientVisuals INSTANCE = new ClientVisuals();
    final Map<UUID, ProjectileTrack> projectiles = new LinkedHashMap<>();
    final Map<UUID, VisualFrame.Cloud> clouds = new LinkedHashMap<>();
    final ArrayDeque<Puff> puffs = new ArrayDeque<>();
    final ArrayDeque<ActiveEffect> effects = new ArrayDeque<>();
    private final LinkedHashSet<Long> seenEffects = new LinkedHashSet<>();
    private ClientLevel level;
    private long lastServerTick = Long.MIN_VALUE;
    int clock, lastFrame, range = 4096;
    long receivedFrames;
    private int previewUntil, previewStart, previewDistance;
    private Vec3 previewOrigin, previewRight;
    private final UUID[] previewIds = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};

    public void accept(VisualFrame frame) {
        if (previewUntil > clock) return;
        apply(frame);
    }

    private void apply(VisualFrame frame) {
        checkWorld();
        if (level == null || !level.dimension().location().equals(frame.dimension()) || frame.serverTick() <= lastServerTick) return;
        lastServerTick = frame.serverTick();
        lastFrame = clock;
        range = frame.range();
        receivedFrames++;
        if (!VisualConfig.ENABLED.get()) { clearObjects(); return; }
        var current = new LinkedHashSet<UUID>();
        for (var state : frame.projectiles()) {
            current.add(state.uuid());
            var track = projectiles.get(state.uuid());
            if (track == null || track.state.id() != state.id()) {
                projectiles.put(state.uuid(), new ProjectileTrack(state, clock, frame.interval()));
            } else {
                track.motion.update(state.position(), state.velocity(), clock, frame.interval());
                track.state = state;
            }
        }
        projectiles.keySet().retainAll(current);
        clouds.clear();
        for (var cloud : frame.clouds()) clouds.put(cloud.uuid(), cloud);
        for (var effect : frame.effects()) {
            if (!seenEffects.add(effect.id())) continue;
            if (effect.sourceId() >= 0 && level.getEntity(effect.sourceId()) != null) continue;
            effects.add(new ActiveEffect(effect, clock));
        }
        while (seenEffects.size() > 1024) seenEffects.removeFirst();
        while (effects.size() > VisualConfig.EFFECT_BUDGET.get()) effects.removeFirst();
    }

    public void tick(ClientTickEvent.Post event) {
        checkWorld();
        var mc = Minecraft.getInstance();
        if (level == null || mc.isPaused()) return;
        clock++;
        if (previewUntil > 0) {
            if (clock >= previewUntil) {
                clearObjects();
                previewUntil = 0;
                lastServerTick = Long.MIN_VALUE;
            } else if ((clock - previewStart) % 2 == 0) previewFrame();
        }
        if (!VisualConfig.ENABLED.get()) { clearObjects(); return; }
        // Server disappearance / dimension transitions must never leave an immortal projectile or cloud.
        if (clock - lastFrame > 40) { projectiles.clear(); clouds.clear(); }
        puffs.removeIf(p -> clock - p.born >= p.lifetime);
        effects.removeIf(e -> clock - e.born >= effectLifetime(e.state.kind()));
        int budget = 256;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        for (var track : projectiles.values()) {
            Vec3 position = track.motion.sample(clock);
            boolean nativeEntity = isNative(track.state.id(), track.state.uuid());
            if (!nativeEntity) {
                track.history.addLast(position);
                while (track.history.size() > 8) track.history.removeFirst();
            } else track.history.clear();
            if (nativeEntity || !VisualConfig.SMOKE.get() || track.state.trailTicks() == 0 || budget <= 0) {
                track.smokeAnchor = position;
                continue;
            }
            double distance = camera.distanceTo(position);
            double spacing = Math.clamp(distance * 0.002, 2, 10);
            var points = VisualMath.trailSamples(track.smokeAnchor, position, spacing, Math.min(12, budget));
            for (Vec3 point : points) {
                int hash = point.hashCode() ^ track.state.uuid().hashCode() ^ clock;
                double radius = Math.clamp(distance * 0.0015, 0.55, 6);
                if (track.state.kind() == VisualFrame.Kind.AUTOCANNON) radius *= 0.65;
                puffs.add(new Puff(point, radius, clock, track.state.trailTicks(), hash, track.state.light()));
                budget--;
            }
            if (!points.isEmpty() || track.smokeAnchor.distanceToSqr(position) > 1024 * 1024) track.smokeAnchor = position;
        }
        while (puffs.size() > VisualConfig.TRAIL_BUDGET.get()) puffs.removeFirst();
    }

    boolean isNative(int id, UUID uuid) {
        var entity = level == null ? null : level.getEntity(id);
        return entity != null && !entity.isRemoved() && entity.getUUID().equals(uuid);
    }

    public void clear() {
        clearObjects();
        seenEffects.clear();
        lastServerTick = Long.MIN_VALUE;
        clock = lastFrame = 0;
        previewUntil = 0;
        receivedFrames = 0;
        level = null;
    }

    private void checkWorld() {
        var current = Minecraft.getInstance().level;
        if (current != level) { clear(); level = current; }
    }

    private void clearObjects() { projectiles.clear(); clouds.clear(); puffs.clear(); effects.clear(); }

    /** A local-only, twenty-second diagnostic. It never spawns game entities or sends server commands. */
    public void preview(int distance) {
        checkWorld();
        if (level == null) return;
        clearObjects();
        lastServerTick = Long.MIN_VALUE;
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 forward = new Vec3(camera.getLookVector().x, camera.getLookVector().y, camera.getLookVector().z).normalize();
        previewRight = forward.cross(new Vec3(0, 1, 0)).normalize();
        if (previewRight.lengthSqr() < 0.01) previewRight = new Vec3(1, 0, 0);
        previewOrigin = camera.getPosition().add(forward.scale(distance));
        previewDistance = distance;
        previewStart = clock;
        previewUntil = clock + 400;
        previewFrame();
    }

    private void previewFrame() {
        int age = clock - previewStart;
        double offset = (age % 100 - 50) * 1.5;
        var list = new ArrayList<VisualFrame.Projectile>();
        for (int i = 0; i < 3; i++) {
            Vec3 pos = previewOrigin.add(previewRight.scale(offset)).add(0, (i - 1) * 20, 0);
            list.add(new VisualFrame.Projectile(-100 - i, previewIds[i], pos, previewRight.scale(1.5), previewRight,
                    i == 2 ? VisualFrame.Kind.AUTOCANNON : VisualFrame.Kind.BIG, i != 1, 100, 240));
        }
        var demoEffects = new ArrayList<VisualFrame.Effect>();
        if (age % 40 == 0) {
            demoEffects.add(new VisualFrame.Effect(Long.MIN_VALUE + clock, -1,
                    previewOrigin.add(previewRight.scale(80)).add(0, -30, 0), new Vec3(0, 1, 0),
                    VisualFrame.EffectKind.SHELL, 5, 0xB4AB9B, ResourceLocation.parse("minecraft:empty"), 240));
        }
        var cloud = new VisualFrame.Cloud(-200, previewIds[0], previewOrigin.add(previewRight.scale(-70)).add(0, -30, 0), new Vec3(24, 16, 16), 240);
        apply(new VisualFrame(level.dimension().location(), clock, 2, Math.max(4096, previewDistance + 512), list, List.of(cloud), demoEffects));
    }
    static int effectLifetime(VisualFrame.EffectKind kind) {
        return switch (kind) { case RICOCHET -> 15; case IMPACT, FRAGMENTS -> 35; case FLAK, SHRAPNEL -> 90; default -> 110; };
    }

    public String status() {
        return "frames=" + receivedFrames + ", projectiles=" + projectiles.size() + ", smoke=" + puffs.size()
                + ", clouds=" + clouds.size() + ", effects=" + effects.size() + ", range=" + range
                + ", Roxy=" + RoxyDepthBridge.status() + (previewUntil > clock ? ", LOCAL PREVIEW" : "");
    }

    static final class ProjectileTrack {
        VisualFrame.Projectile state;
        final MotionTrack motion;
        final ArrayDeque<Vec3> history = new ArrayDeque<>();
        Vec3 smokeAnchor;
        ProjectileTrack(VisualFrame.Projectile state, int clock, int interval) {
            this.state = state;
            this.motion = new MotionTrack(state.position(), state.velocity(), clock, interval);
            this.smokeAnchor = state.position();
        }
    }
    record Puff(Vec3 position, double radius, int born, int lifetime, int seed, int light) {}
    record ActiveEffect(VisualFrame.Effect state, int born) {}
    private ClientVisuals() {}
}
