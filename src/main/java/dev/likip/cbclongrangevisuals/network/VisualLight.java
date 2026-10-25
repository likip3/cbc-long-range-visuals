package dev.likip.cbclongrangevisuals.network;

/** Compact block/sky light levels; actual lightmap RGB is evaluated on the client each frame. */
public final class VisualLight {
    public static int levels(int block, int sky) { return Math.clamp(block, 0, 15) | (Math.clamp(sky, 0, 15) << 4); }
    public static int packed(int levels) { return ((levels & 15) << 4) | (((levels >>> 4) & 15) << 20); }
    private VisualLight() {}
}
