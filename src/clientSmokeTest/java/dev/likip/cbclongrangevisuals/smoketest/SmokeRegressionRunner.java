package dev.likip.cbclongrangevisuals.smoketest;

import dev.likip.cbclongrangevisuals.client.ClientVisuals;
import java.lang.reflect.Field;
import java.util.Collection;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.opengl.GL11;

/** Loads only with -PclientSmokeTest and operates on an isolated copied world. */
@Mod(value = "cbc_visuals_smoke_test", dist = Dist.CLIENT)
public final class SmokeRegressionRunner {
    private final Field puffs;
    private int ticks, framesWithSmoke;
    private boolean reloading, reloadFinished, done;
    private long launched = System.nanoTime();

    public SmokeRegressionRunner() {
        try {
            puffs = ClientVisuals.class.getDeclaredField("puffs");
            puffs.setAccessible(true);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect smoke regression state", exception);
        }
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::tick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::rendered);
    }

    private void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (done) return;
        if (System.nanoTime() - launched > 180_000_000_000L) {
            finish(false, "Timeout before graphical regression completed");
            return;
        }
        if (mc.level == null || mc.player == null || mc.getOverlay() != null || reloading) return;
        // Quick Play can leave the pause screen when the window is backgrounded.
        if (mc.screen != null) mc.setScreen(null);
        ticks++;
        if (ticks == 5) {
            mc.player.setXRot(-10);
            mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setDayTime(6000));
            ClientVisuals.INSTANCE.preview(2048);
        }
        if (ticks == 65) {
            if (framesWithSmoke == 0) { finish(false, "No smoke draw calls observed in daylight"); return; }
            mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setDayTime(18000));
            ClientVisuals.INSTANCE.preview(2048);
        }
        if (ticks == 125) {
            reloading = true;
            mc.reloadResourcePacks().whenComplete((unused, error) -> mc.execute(() -> {
                reloading = false;
                if (error != null) finish(false, "Resource reload failed: " + error);
                else { reloadFinished = true; ClientVisuals.INSTANCE.preview(2048); }
            }));
        }
        if (ticks >= 200) finish(reloadFinished && framesWithSmoke >= 60,
                "day/night/resource-reload; framesWithSmoke=" + framesWithSmoke + "; reload=" + reloadFinished);
    }

    private void rendered(RenderLevelStageEvent event) {
        if (done || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || reloading
                || !hasSmoke()) return;
        var mc = Minecraft.getInstance();
        var texture = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
        if (!(texture instanceof TextureAtlas atlas)) { finish(false, "Particle atlas not registered"); return; }
        for (int i = 0; i < 12; i++) {
            var name = ResourceLocation.withDefaultNamespace("big_smoke_" + i);
            if (!atlas.getSprite(name).contents().name().equals(name)) {
                finish(false, "Missing smoke sprite: " + name);
                return;
            }
        }
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) { finish(false, "OpenGL error after far smoke draw: 0x" + Integer.toHexString(error)); return; }
        framesWithSmoke++;
    }

    private boolean hasSmoke() {
        try { return !((Collection<?>) puffs.get(ClientVisuals.INSTANCE)).isEmpty(); }
        catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
    }

    private void finish(boolean success, String detail) {
        if (done) return;
        done = true;
        String result = (success ? "PASS " : "FAIL ") + detail;
        try { Files.writeString(Path.of("smoke-result.txt"), result); }
        catch (Exception error) { throw new IllegalStateException("Cannot save graphical regression result", error); }
        System.out.println("CBC_VISUALS_GRAPHICAL_REGRESSION: " + result);
        Minecraft.getInstance().stop();
    }
}
