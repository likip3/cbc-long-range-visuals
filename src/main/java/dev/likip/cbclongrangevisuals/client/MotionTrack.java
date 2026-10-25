package dev.likip.cbclongrangevisuals.client;

import net.minecraft.world.phys.Vec3;

/** Receipt-relative interpolation avoids assuming that client and server clocks are synchronized. */
public final class MotionTrack {
    private Vec3 from, to, velocity;
    private double received;
    private int interval;

    public MotionTrack(Vec3 position, Vec3 velocity, double clock, int interval) {
        from = to = position;
        this.velocity = velocity;
        this.received = clock;
        this.interval = interval;
    }

    public void update(Vec3 position, Vec3 speed, double clock, int ticks) {
        Vec3 current = sample(clock);
        // Dimension/ship discontinuities must never produce a smoke bridge across the map.
        from = current.distanceTo(position) > Math.max(256, speed.length() * ticks * 4) ? position : current;
        to = position;
        velocity = speed;
        received = clock;
        interval = ticks;
    }

    public Vec3 sample(double clock) {
        double elapsed = Math.max(0, clock - received);
        double alpha = Math.min(1, elapsed / interval);
        double extrapolation = Math.min(2, Math.max(0, elapsed - interval));
        return from.lerp(to, alpha).add(velocity.scale(extrapolation));
    }
}
