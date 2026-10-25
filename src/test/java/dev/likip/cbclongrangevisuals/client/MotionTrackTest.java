package dev.likip.cbclongrangevisuals.client;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MotionTrackTest {
    @Test void interpolatesAndBoundsExtrapolationWhenPacketsStop() {
        var motion = new MotionTrack(Vec3.ZERO, new Vec3(10, 0, 0), 1000, 2);
        motion.update(new Vec3(20, 0, 0), new Vec3(10, 0, 0), 1000, 2);
        assertEquals(10, motion.sample(1001).x, 1e-9);
        assertEquals(20, motion.sample(1002).x, 1e-9);
        assertEquals(40, motion.sample(1004).x, 1e-9);
        assertEquals(40, motion.sample(2000).x, 1e-9);
    }
    @Test void usesReceiptClockAndDoesNotMoveBackwardsBeforeReceipt() {
        var motion = new MotionTrack(new Vec3(5, 0, 0), Vec3.ZERO, 10000, 2);
        motion.update(new Vec3(9, 0, 0), Vec3.ZERO, 10000, 2);
        assertEquals(5, motion.sample(9999).x, 1e-9);
        assertEquals(7, motion.sample(10001).x, 1e-9);
    }
    @Test void snapsAcrossShipOrTeleportDiscontinuities() {
        var motion = new MotionTrack(Vec3.ZERO, Vec3.ZERO, 0, 2);
        motion.update(new Vec3(10000, 20, 10), Vec3.ZERO, 2, 2);
        assertEquals(new Vec3(10000, 20, 10), motion.sample(2));
    }
    @Test void fastProjectilesDoNotCreateUnboundedParticleCounts() {
        var samples = VisualMath.trailSamples(Vec3.ZERO, new Vec3(600, 0, 0), 2, 12);
        assertEquals(12, samples.size());
        assertEquals(new Vec3(600, 0, 0), samples.getLast());
        assertTrue(samples.stream().allMatch(p -> p.x > 0 && p.x <= 600));
        assertTrue(VisualMath.trailSamples(Vec3.ZERO, new Vec3(10000, 0, 0), 2, 12).isEmpty());
        assertTrue(VisualMath.trailSamples(Vec3.ZERO, new Vec3(4, 0, 0), 2, 0).isEmpty());
    }
    @Test void minimumTracerWidthScalesWithDistanceAndStopsGrowing() {
        assertEquals(2 * VisualMath.tracerHalfWidth(1000, 1, 1000, 1.5),
                VisualMath.tracerHalfWidth(2000, 1, 1000, 1.5), 1e-9);
        assertEquals(4, VisualMath.tracerHalfWidth(16000, 1, 1000, 4), 1e-9);
    }
}
