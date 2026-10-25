package dev.likip.cbclongrangevisuals.client;

import dev.likip.cbclongrangevisuals.CbcLongRangeVisuals;
import dev.likip.cbclongrangevisuals.VisualConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;

/** Optional adapter for Roxy's captured Voxy frame; no mandatory Roxy/Voxy/Iris dependency. */
public final class RoxyDepthBridge {
    private static boolean checked, failed, active;
    private static Field frame;
    private static Method depth, stencil, reverse, zeroOne, transform;

    static void apply(ShaderInstance shader) {
        shader.safeGetUniform("LodEnabled").set(0);
        // Keep differently typed samplers on separate texture units, even without a Voxy frame.
        shader.setSampler("LodDepth", 0);
        shader.setSampler("LodStencil", 0);
        active = false;
        if (!VisualConfig.ROXY_DEPTH.get() || failed) return;
        if (!checked) resolve();
        if (frame == null) return;
        try {
            Object current = frame.get(null);
            if (current == null) return; // E.g. no Voxy terrain in this view, or a shadow pass.
            int depthId = (int) depth.invoke(current), stencilId = (int) stencil.invoke(current);
            if (depthId == 0 || stencilId == 0) return;
            shader.setSampler("LodDepth", depthId);
            shader.setSampler("LodStencil", stencilId);
            shader.safeGetUniform("ClipToLod").set(new Matrix4f().set((float[]) transform.invoke(current)));
            shader.safeGetUniform("LodReverseZ").set((boolean) reverse.invoke(current) ? 1 : 0);
            shader.safeGetUniform("LodZeroOne").set((boolean) zeroOne.invoke(current) ? 1 : 0);
            shader.safeGetUniform("LodEnabled").set(1);
            active = true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            failed = true;
            CbcLongRangeVisuals.LOGGER.warn("Roxy LOD depth bridge disabled; vanilla depth remains enabled", exception);
        }
    }

    private static void resolve() {
        checked = true;
        try {
            Class<?> api = Class.forName("net.rasanovum.roxy.compat.RoxyLodEntityOcclusion");
            frame = api.getDeclaredField("frame");
            frame.setAccessible(true);
            Class<?> record = frame.getType();
            depth = accessor(record, "depthTexture");
            stencil = accessor(record, "stencilTexture");
            reverse = accessor(record, "reverseZ");
            zeroOne = accessor(record, "zeroOne");
            transform = accessor(record, "transform");
            CbcLongRangeVisuals.LOGGER.info("Roxy Voxy LOD depth bridge available");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            frame = null;
            if (ModList.get().isLoaded("roxy"))
                CbcLongRangeVisuals.LOGGER.warn("This Roxy build does not expose the expected LOD depth frame; using vanilla depth");
        }
    }

    private static Method accessor(Class<?> type, String name) throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(name);
        method.setAccessible(true);
        return method;
    }

    public static String status() { return active ? "LOD depth active" : failed ? "bridge failed" : frame != null ? "awaiting LOD frame" : "unavailable"; }
    private RoxyDepthBridge() {}
}
