package dev.likip.cbclongrangevisuals.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class VisualMath {
    public static double tracerHalfWidth(double distance, double verticalProjectionScale, int height, double pixels) {
        return Math.clamp(distance * pixels / (Math.max(1, height) * Math.max(0.1, verticalProjectionScale)), 0.04, 4);
    }

    public static List<Vec3> trailSamples(Vec3 from, Vec3 to, double spacing, int budget) {
        double length = from.distanceTo(to);
        if (budget <= 0 || !Double.isFinite(length) || length > 1024 || length < spacing) return List.of();
        int count = Math.min(budget, (int) Math.floor(length / Math.max(0.1, spacing)));
        var points = new ArrayList<Vec3>(count);
        for (int i = 1; i <= count; i++) points.add(from.lerp(to, (double) i / count));
        return points;
    }

    private VisualMath() {}
}
