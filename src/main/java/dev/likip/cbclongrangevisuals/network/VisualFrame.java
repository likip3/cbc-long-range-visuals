package dev.likip.cbclongrangevisuals.network;

import dev.likip.cbclongrangevisuals.CbcLongRangeVisuals;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** A bounded, complete interest frame. Missing objects are removed, never interpreted as explosions. */
public record VisualFrame(ResourceLocation dimension, long serverTick, int interval, int range,
                          List<Projectile> projectiles, List<Cloud> clouds, List<Effect> effects) implements CustomPacketPayload {
    public static final Type<VisualFrame> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CbcLongRangeVisuals.MOD_ID, "frame"));
    public static final int MAX_PROJECTILES = 512, MAX_CLOUDS = 64, MAX_EFFECTS = 128;
    public static final StreamCodec<RegistryFriendlyByteBuf, VisualFrame> CODEC = StreamCodec.of(VisualFrame::write, VisualFrame::read);

    public VisualFrame {
        projectiles = List.copyOf(projectiles);
        clouds = List.copyOf(clouds);
        effects = List.copyOf(effects);
        if (interval < 1 || interval > 10 || range < 128 || range > 16384
                || projectiles.size() > MAX_PROJECTILES || clouds.size() > MAX_CLOUDS || effects.size() > MAX_EFFECTS)
            throw new IllegalArgumentException("Visual frame exceeds protocol limits");
    }

    public enum Kind { BIG, AUTOCANNON, MORTAR }
    public enum EffectKind { IMPACT, RICOCHET, SHELL, FLAK, SHRAPNEL, FLUID, STONE, FRAGMENTS }

    public record Projectile(int id, UUID uuid, Vec3 position, Vec3 velocity, Vec3 direction,
                             Kind kind, boolean tracer, int trailTicks, int light) {}
    public record Cloud(int id, UUID uuid, Vec3 position, Vec3 size, int light) {}
    public record Effect(long id, int sourceId, Vec3 position, Vec3 direction, EffectKind kind, float scale,
                         int color, ResourceLocation fluid, int light) {}

    @Override public Type<VisualFrame> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf b, VisualFrame f) {
        b.writeResourceLocation(f.dimension);
        b.writeVarLong(f.serverTick);
        b.writeVarInt(f.interval);
        b.writeVarInt(f.range);
        b.writeVarInt(f.projectiles.size());
        for (var p : f.projectiles) {
            b.writeVarInt(p.id); b.writeUUID(p.uuid); writePosition(b, p.position);
            writeVector(b, p.velocity); writeVector(b, p.direction);
            b.writeByte(p.kind.ordinal()); b.writeBoolean(p.tracer); b.writeVarInt(p.trailTicks); b.writeByte(p.light);
        }
        b.writeVarInt(f.clouds.size());
        for (var c : f.clouds) {
            b.writeVarInt(c.id); b.writeUUID(c.uuid); writePosition(b, c.position); writeVector(b, c.size); b.writeByte(c.light);
        }
        b.writeVarInt(f.effects.size());
        for (var e : f.effects) {
            b.writeLong(e.id); b.writeVarInt(e.sourceId); writePosition(b, e.position); writeVector(b, e.direction);
            b.writeByte(e.kind.ordinal()); b.writeFloat(e.scale); b.writeInt(e.color); b.writeResourceLocation(e.fluid); b.writeByte(e.light);
        }
    }

    private static VisualFrame read(RegistryFriendlyByteBuf b) {
        var dimension = b.readResourceLocation();
        long tick = b.readVarLong();
        int interval = bounded(b.readVarInt(), 1, 10), range = bounded(b.readVarInt(), 128, 16384);
        int count = bounded(b.readVarInt(), 0, MAX_PROJECTILES);
        var projectiles = new ArrayList<Projectile>(count);
        for (int i = 0; i < count; i++) {
            int id = b.readVarInt(); var uuid = b.readUUID(); var pos = readPosition(b);
            Vec3 velocity = readVector(b, 1024), direction = readVector(b, 2);
            var kind = Kind.values()[bounded(b.readUnsignedByte(), 0, Kind.values().length - 1)];
            boolean tracer = b.readBoolean(); int lifetime = bounded(b.readVarInt(), 0, 400);
            projectiles.add(new Projectile(id, uuid, pos, velocity, direction, kind, tracer, lifetime, b.readUnsignedByte()));
        }
        count = bounded(b.readVarInt(), 0, MAX_CLOUDS);
        var clouds = new ArrayList<Cloud>(count);
        for (int i = 0; i < count; i++) {
            int id = b.readVarInt(); var uuid = b.readUUID(); var pos = readPosition(b); var size = readVector(b, 2048);
            if (size.x < 0 || size.y < 0 || size.z < 0) throw new DecoderException("Negative smoke size");
            clouds.add(new Cloud(id, uuid, pos, size, b.readUnsignedByte()));
        }
        count = bounded(b.readVarInt(), 0, MAX_EFFECTS);
        var effects = new ArrayList<Effect>(count);
        for (int i = 0; i < count; i++) {
            long id = b.readLong(); int sourceId = b.readVarInt(); var pos = readPosition(b); var direction = readVector(b, 2);
            var kind = EffectKind.values()[bounded(b.readUnsignedByte(), 0, EffectKind.values().length - 1)];
            float scale = b.readFloat();
            if (!Float.isFinite(scale) || scale < 0 || scale > 128) throw new DecoderException("Invalid effect scale");
            effects.add(new Effect(id, sourceId, pos, direction, kind, scale, b.readInt(), b.readResourceLocation(), b.readUnsignedByte()));
        }
        return new VisualFrame(dimension, tick, interval, range, projectiles, clouds, effects);
    }

    private static int bounded(int n, int min, int max) {
        if (n < min || n > max) throw new DecoderException("Visual frame field outside limits");
        return n;
    }
    private static void writePosition(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static void writeVector(RegistryFriendlyByteBuf b, Vec3 v) { b.writeFloat((float)v.x); b.writeFloat((float)v.y); b.writeFloat((float)v.z); }
    private static Vec3 readPosition(RegistryFriendlyByteBuf b) {
        return checked(new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), 32_000_000);
    }
    private static Vec3 readVector(RegistryFriendlyByteBuf b, double max) {
        return checked(new Vec3(b.readFloat(), b.readFloat(), b.readFloat()), max);
    }
    private static Vec3 checked(Vec3 v, double max) {
        if (!Double.isFinite(v.x) || !Double.isFinite(v.y) || !Double.isFinite(v.z)
                || Math.abs(v.x) > max || Math.abs(v.y) > max || Math.abs(v.z) > max)
            throw new DecoderException("Invalid visual coordinates");
        return v;
    }
}
