package dev.likip.cbclongrangevisuals;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class VisualConfig {
    public static final ModConfigSpec SERVER;
    public static final ModConfigSpec CLIENT;
    public static final ModConfigSpec.IntValue RANGE, INTERVAL, PROJECTILES, CLOUDS, EVENTS;
    public static final ModConfigSpec.BooleanValue ENABLED, SMOKE, ROXY_DEPTH;
    public static final ModConfigSpec.IntValue TRAIL_BUDGET, EFFECT_BUDGET;
    public static final ModConfigSpec.DoubleValue TRACER_PIXELS, TRACER_LENGTH;

    static {
        var server = new ModConfigSpec.Builder();
        server.comment("Only visual synchronization; CBC remains responsible for physics and chunk loading.").push("synchronization");
        RANGE = server.defineInRange("maxDistance", 4096, 128, 16384);
        INTERVAL = server.defineInRange("snapshotIntervalTicks", 2, 1, 10);
        PROJECTILES = server.defineInRange("maxProjectilesPerPlayer", 128, 1, 512);
        CLOUDS = server.defineInRange("maxSmokeCloudsPerPlayer", 32, 0, 64);
        EVENTS = server.defineInRange("maxEffectsPerFrame", 64, 0, 128);
        server.pop();
        SERVER = server.build();

        var client = new ModConfigSpec.Builder();
        client.push("readableDistance");
        ENABLED = client.define("enabled", true);
        SMOKE = client.define("smokeTrails", true);
        ROXY_DEPTH = client.comment("Optional bridge to Roxy's Voxy LOD depth. Falls back to vanilla depth when unavailable.")
                .define("roxyLodOcclusion", true);
        TRACER_PIXELS = client.defineInRange("minimumTracerPixels", 1.5, 0.25, 4);
        TRACER_LENGTH = client.comment("Length follows recent motion; this is the maximum world-space length in blocks.")
                .defineInRange("maximumTracerLength", 12.0, 1, 32);
        TRAIL_BUDGET = client.defineInRange("maxTrailPuffs", 4096, 128, 16384);
        EFFECT_BUDGET = client.defineInRange("maxActiveEffects", 128, 8, 512);
        client.pop();
        CLIENT = client.build();
    }

    private VisualConfig() {}
}
