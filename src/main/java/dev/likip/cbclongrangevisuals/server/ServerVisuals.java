package dev.likip.cbclongrangevisuals.server;

import dev.likip.cbclongrangevisuals.VisualConfig;
import dev.likip.cbclongrangevisuals.mixin.ShellExplosionAccess;
import dev.likip.cbclongrangevisuals.mixin.FluidExplosionAccess;
import dev.likip.cbclongrangevisuals.network.VisualFrame;
import dev.likip.cbclongrangevisuals.network.VisualLight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import rbasamoyai.createbigcannons.CBCCompatTransformers;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ImpactExplosion;
import rbasamoyai.createbigcannons.munitions.ShellExplosion;
import rbasamoyai.createbigcannons.munitions.autocannon.AbstractAutocannonProjectile;
import rbasamoyai.createbigcannons.munitions.autocannon.flak.FlakExplosion;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.fluid_shell.FluidExplosion;
import rbasamoyai.createbigcannons.munitions.big_cannon.mortar_stone.MortarStoneExplosion;
import rbasamoyai.createbigcannons.munitions.big_cannon.mortar_stone.MortarStoneProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.shrapnel.ShrapnelExplosion;
import rbasamoyai.createbigcannons.munitions.big_cannon.smoke_shell.SmokeEmitterEntity;
import rbasamoyai.createbigcannons.remix.CustomExplosion;

/** Interest management runs on the server thread and never loads chunks or changes CBC physics. */
public final class ServerVisuals {
    private static final Map<ServerLevel, World> WORLDS = new IdentityHashMap<>();
    private static long nextEffect;
    private static final ResourceLocation NO_FLUID = ResourceLocation.parse("minecraft:empty");

    public static void register(IEventBus bus) {
        bus.addListener(ServerVisuals::join);
        bus.addListener(ServerVisuals::leave);
        bus.addListener(ServerVisuals::tick);
        bus.addListener(ServerVisuals::explosion);
        bus.addListener(ServerVisuals::unload);
        bus.addListener((ServerStoppedEvent event) -> WORLDS.clear());
    }

    private static World world(ServerLevel level) { return WORLDS.computeIfAbsent(level, ignored -> new World()); }
    private static void join(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && (event.getEntity() instanceof AbstractCannonProjectile || event.getEntity() instanceof SmokeEmitterEntity))
            world(level).entities.put(event.getEntity().getId(), event.getEntity());
    }
    private static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && WORLDS.containsKey(level))
            WORLDS.get(level).entities.remove(event.getEntity().getId(), event.getEntity());
    }
    private static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) WORLDS.remove(level);
    }

    private static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        int interval = VisualConfig.INTERVAL.get();
        if (level.getGameTime() % interval != 0) return;
        World world = world(level);
        world.entities.values().removeIf(Entity::isRemoved);
        int range = VisualConfig.RANGE.get();
        var projectiles = new ArrayList<VisualFrame.Projectile>();
        var clouds = new ArrayList<VisualFrame.Cloud>();
        for (Entity entity : world.entities.values()) {
            if (entity instanceof AbstractCannonProjectile projectile && !projectile.isInGround()) {
                var snapshot = snapshot(projectile);
                if (valid(snapshot.position()) && valid(snapshot.velocity()) && valid(snapshot.direction())) projectiles.add(snapshot);
            } else if (entity instanceof SmokeEmitterEntity smoke) {
                Vec3 pos = CBCCompatTransformers.transformVec3(level, smoke.position());
                if (valid(pos)) clouds.add(new VisualFrame.Cloud(smoke.getId(), smoke.getUUID(), pos,
                        new Vec3(Math.min(2048, smoke.getSizeX()), Math.min(2048, smoke.getSizeY()), Math.min(2048, smoke.getSizeZ())), light(level, pos)));
            }
        }
        for (var player : level.players()) {
            // Optional channel: clients without the addon can still use the server normally.
            if (!player.connection.hasChannel(VisualFrame.TYPE)) continue;
            Vec3 observer = CBCCompatTransformers.transformVec3(level, player.position());
            double rangeSqr = (double) range * range;
            var selected = projectiles.stream().filter(p -> p.position().distanceToSqr(observer) <= rangeSqr)
                    .sorted(Comparator.<VisualFrame.Projectile>comparingInt(p -> p.tracer() ? 0 : 1)
                            .thenComparingDouble(p -> p.position().distanceToSqr(observer)))
                    .limit(VisualConfig.PROJECTILES.get()).toList();
            var selectedClouds = clouds.stream().filter(c -> c.position().distanceToSqr(observer) <= rangeSqr)
                    .sorted(Comparator.comparingDouble(c -> c.position().distanceToSqr(observer)))
                    .limit(VisualConfig.CLOUDS.get()).toList();
            var effects = world.effects.stream().filter(e -> {
                double distance = e.position().distanceToSqr(observer);
                return distance <= rangeSqr && (e.sourceId() >= 0 || distance >= nativeEffectRangeSqr(e.kind()));
            }).sorted(Comparator.comparingDouble(e -> e.position().distanceToSqr(observer)))
                    .limit(VisualConfig.EVENTS.get()).toList();
            PacketDistributor.sendToPlayer(player, new VisualFrame(level.dimension().location(), level.getGameTime(),
                    interval, range, selected, selectedClouds, effects));
        }
        world.effects.clear();
    }

    private static VisualFrame.Projectile snapshot(AbstractCannonProjectile projectile) {
        var level = projectile.level();
        Vec3 local = projectile.position();
        Vec3 pos = CBCCompatTransformers.transformVec3(level, local);
        // Transform a displacement, not a point: ship translation must not become projectile velocity.
        Vec3 velocity = CBCCompatTransformers.transformVec3(level, local.add(projectile.getDeltaMovement()), local).subtract(pos);
        velocity = new Vec3(Math.clamp(velocity.x, -1024, 1024), Math.clamp(velocity.y, -1024, 1024), Math.clamp(velocity.z, -1024, 1024));
        Vec3 direction = CBCCompatTransformers.transformVec3(level, local.add(projectile.getOrientation()), local).subtract(pos).normalize();
        boolean tracer = false;
        int trail = 0;
        var kind = VisualFrame.Kind.BIG;
        if (projectile instanceof AbstractBigCannonProjectile big) {
            tracer = big.hasTracer();
            trail = switch (CBCConfigs.server().munitions.bigCannonTrailType.get()) {
                case NONE -> 0; case SHORT -> 100; case LONG -> 300;
            };
            if (projectile instanceof MortarStoneProjectile) kind = VisualFrame.Kind.MORTAR;
        } else if (projectile instanceof AbstractAutocannonProjectile auto) {
            kind = VisualFrame.Kind.AUTOCANNON;
            tracer = auto.isTracer();
            trail = switch (CBCConfigs.server().munitions.autocannonTrailType.get()) {
                case NONE -> 0; case SHORT -> 50; case LONG -> 125;
            };
        }
        return new VisualFrame.Projectile(projectile.getId(), projectile.getUUID(), pos, velocity,
                direction, kind, tracer, trail, light(level, pos));
    }

    public static void impact(AbstractCannonProjectile projectile, HitResult hit, AbstractCannonProjectile.ImpactResult result) {
        if (!(projectile.level() instanceof ServerLevel level) || hit.getType() == HitResult.Type.MISS) return;
        Vec3 pos = CBCCompatTransformers.transformVec3(level, hit.getLocation());
        Vec3 direction = projectile.getDeltaMovement().normalize().reverse();
        if (hit instanceof BlockHitResult blockHit) {
            Vec3 normal = Vec3.atLowerCornerOf(blockHit.getDirection().getNormal());
            direction = CBCCompatTransformers.transformHitNormal(level, blockHit, normal).normalize();
        }
        var kind = result.kinematics() == AbstractCannonProjectile.ImpactResult.KinematicOutcome.BOUNCE
                ? VisualFrame.EffectKind.RICOCHET : VisualFrame.EffectKind.IMPACT;
        enqueue(level, new VisualFrame.Effect(++nextEffect, projectile.getId(), pos, direction, kind,
                projectile instanceof AbstractAutocannonProjectile ? 0.7f : 1.5f, 0xB7A68B, NO_FLUID, light(level, pos)));
    }

    private static void explosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getExplosion() instanceof CustomExplosion)) return;
        var explosion = event.getExplosion();
        VisualFrame.EffectKind kind;
        if (explosion instanceof ShellExplosion shell) {
            if (((ShellExplosionAccess) shell).cbcLongRangeVisuals$noEffects()) return;
            kind = VisualFrame.EffectKind.SHELL;
        } else if (explosion instanceof FlakExplosion) kind = VisualFrame.EffectKind.FLAK;
        else if (explosion instanceof ShrapnelExplosion) kind = VisualFrame.EffectKind.SHRAPNEL;
        else if (explosion instanceof FluidExplosion) kind = VisualFrame.EffectKind.FLUID;
        else if (explosion instanceof MortarStoneExplosion) kind = VisualFrame.EffectKind.STONE;
        else if (explosion instanceof ImpactExplosion) kind = VisualFrame.EffectKind.IMPACT;
        else return; // Smoke is synchronized as a persistent emitter instead of a guessed explosion.
        Vec3 pos = CBCCompatTransformers.transformVec3(level, explosion.center());
        ResourceLocation fluid = explosion instanceof FluidExplosion fluidExplosion
                ? BuiltInRegistries.FLUID.getKey(((FluidExplosionAccess) fluidExplosion).cbcLongRangeVisuals$fluid()) : NO_FLUID;
        enqueue(level, new VisualFrame.Effect(++nextEffect, -1, pos, new Vec3(0, 1, 0), kind,
                Math.min(128, Math.max(0.5f, explosion.radius())), 0xB4AB9B, fluid, light(level, pos)));
    }

    public static void burst(Level level, EntityType<?> type, Vec3 position, Vec3 velocity, double spread, int entityId) {
        if (!(level instanceof ServerLevel server)) return;
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
        if (!path.equals("shrapnel_burst") && !path.equals("flak_burst") && !path.equals("grapeshot_burst")) return;
        Vec3 worldPosition = CBCCompatTransformers.transformVec3(level, position);
        Vec3 direction = CBCCompatTransformers.transformVec3(level, position.add(velocity), position).subtract(worldPosition).normalize();
        enqueue(server, new VisualFrame.Effect(++nextEffect, entityId, worldPosition, direction,
                VisualFrame.EffectKind.FRAGMENTS, (float) Math.clamp(spread, 0.05, 2), 0x706A61, NO_FLUID, light(level, worldPosition)));
    }

    static double nativeEffectRangeSqr(VisualFrame.EffectKind kind) {
        return switch (kind) {
            case SHELL -> 263000;
            case FLAK, SHRAPNEL -> 250000;
            case FLUID -> 16400;
            default -> 10000;
        };
    }

    private static int light(Level level, Vec3 position) {
        BlockPos block = BlockPos.containing(position);
        if (!level.hasChunkAt(block)) return VisualLight.levels(0, level.dimensionType().hasSkyLight() ? 15 : 0);
        return VisualLight.levels(level.getBrightness(LightLayer.BLOCK, block), level.getBrightness(LightLayer.SKY, block));
    }

    private static void enqueue(ServerLevel level, VisualFrame.Effect effect) {
        if (valid(effect.position()) && valid(effect.direction()) && Float.isFinite(effect.scale())
                && world(level).effects.size() < 512) world(level).effects.add(effect);
    }
    private static boolean valid(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z)
                && Math.abs(v.x) <= 32_000_000 && Math.abs(v.y) <= 32_000_000 && Math.abs(v.z) <= 32_000_000;
    }
    private static final class World {
        final Map<Integer, Entity> entities = new LinkedHashMap<>();
        final List<VisualFrame.Effect> effects = new ArrayList<>();
    }
    private ServerVisuals() {}
}
