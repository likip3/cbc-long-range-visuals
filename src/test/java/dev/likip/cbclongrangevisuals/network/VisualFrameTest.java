package dev.likip.cbclongrangevisuals.network;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class VisualFrameTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.parse("minecraft:overworld");
    private RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY); }

    @Test void roundTripsWorldCoordinatesAndIdentityOutsideNormalChunkRange() {
        var p = new VisualFrame.Projectile(87, UUID.randomUUID(), new Vec3(29_000_000.125, 200.5, -12_345.75),
                new Vec3(14, -0.5, 2), new Vec3(1, 0, 0), VisualFrame.Kind.BIG, true, 300, VisualLight.levels(3, 15));
        var cloud = new VisualFrame.Cloud(88, UUID.randomUUID(), new Vec3(1000, 100, 2000), new Vec3(20, 40, 30), VisualLight.levels(0, 12));
        var effect = new VisualFrame.Effect(100, -1, new Vec3(5000, 10, 3), new Vec3(0, 1, 0), VisualFrame.EffectKind.SHELL, 4, 0xFFFFFF, ResourceLocation.parse("minecraft:empty"), VisualLight.levels(7, 15));
        var frame = new VisualFrame(DIMENSION, 999, 2, 4096, List.of(p), List.of(cloud), List.of(effect));
        var b = buffer();
        try {
            VisualFrame.CODEC.encode(b, frame);
            assertEquals(frame, VisualFrame.CODEC.decode(b));
            assertEquals(0, b.readableBytes());
        } finally { b.release(); }
    }
    @Test void rejectsOversizedListsBeforeAllocatingThem() {
        var b = buffer();
        try {
            b.writeResourceLocation(DIMENSION); b.writeVarLong(1); b.writeVarInt(2); b.writeVarInt(4096);
            b.writeVarInt(VisualFrame.MAX_PROJECTILES + 1);
            assertThrows(DecoderException.class, () -> VisualFrame.CODEC.decode(b));
        } finally { b.release(); }
    }
    @Test void rejectsNanCoordinates() {
        var p = new VisualFrame.Projectile(1, UUID.randomUUID(), new Vec3(Double.NaN, 0, 0),
                Vec3.ZERO, Vec3.ZERO, VisualFrame.Kind.BIG, false, 0, 0);
        var frame = new VisualFrame(DIMENSION, 1, 2, 4096, List.of(p), List.of(), List.of());
        var b = buffer();
        try {
            VisualFrame.CODEC.encode(b, frame);
            assertThrows(DecoderException.class, () -> VisualFrame.CODEC.decode(b));
        } finally { b.release(); }
    }
    @Test void emptyFrameIsExplicitAndNotAnExplosion() {
        var frame = new VisualFrame(DIMENSION, 1, 2, 4096, List.of(), List.of(), List.of());
        var b = buffer();
        try {
            VisualFrame.CODEC.encode(b, frame);
            assertEquals(frame, VisualFrame.CODEC.decode(b));
            assertTrue(frame.effects().isEmpty());
        } finally { b.release(); }
    }
}
