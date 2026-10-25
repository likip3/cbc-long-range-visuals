package dev.likip.cbclongrangevisuals.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.likip.cbclongrangevisuals.CbcLongRangeVisuals;
import dev.likip.cbclongrangevisuals.VisualConfig;
import dev.likip.cbclongrangevisuals.network.VisualFrame;
import dev.likip.cbclongrangevisuals.network.VisualLight;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

/** Camera-relative doubles are converted to floats only when writing vertices. */
public final class FarVisualRenderer {
    private static ShaderInstance shader;
    private static TextureAtlasSprite[] smokeSprites;
    private static final ByteBufferBuilder MEMORY = new ByteBufferBuilder(1 << 20);
    private static final RenderType TYPE = RenderType.create("cbc_far_visuals", DefaultVertexFormat.PARTICLE,
            VertexFormat.Mode.QUADS, 1 << 20, false, false, RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> shader))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setCullState(RenderStateShard.NO_CULL).createCompositeState(false));

    public static void registerShader(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CbcLongRangeVisuals.MOD_ID, "far_visual"),
                    DefaultVertexFormat.PARTICLE), loaded -> { shader = loaded; smokeSprites = null; });
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load CBC far visuals shader", exception);
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || shader == null || !VisualConfig.ENABLED.get()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var state = ClientVisuals.INSTANCE;
        double clock = state.clock + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        var rightF = new Vector3f(1, 0, 0).rotate(event.getCamera().rotation());
        var upF = new Vector3f(0, 1, 0).rotate(event.getCamera().rotation());
        Vec3 right = new Vec3(rightF.x, rightF.y, rightF.z), up = new Vec3(upF.x, upF.y, upF.z);
        var quads = new ArrayList<Quad>();
        for (var puff : state.puffs) {
            double age = Math.max(0, clock - puff.born()), life = age / puff.lifetime();
            double fade = Math.min(1, (1 - life) * 3);
            double radius = puff.radius() * (0.8 + life * 0.8);
            Vec3 drift = new Vec3(noise(puff.seed()) - 0.5, 0.3, noise(puff.seed() + 1) - 0.5).scale(age * 0.008);
            // CBC's TrailSmokeParticle uses untinted vanilla big_smoke sprites and ordinary particle lighting.
            litBillboard(quads, puff.position().add(drift), right, up, radius, 0xFFFFFF, fade * 0.35,
                    VisualLight.packed(puff.light()), Math.floorMod(puff.seed(), 12));
        }
        for (var cloud : state.clouds.values()) {
            if (state.isNative(cloud.id(), cloud.uuid())) continue;
            int seed = cloud.uuid().hashCode();
            Vec3 size = cloud.size();
            double radius = Math.clamp((size.x + size.y + size.z) / 9, 1.5, 32);
            for (int i = 0; i < 18; i++) {
                Vec3 offset = new Vec3((noise(seed + i * 3) - 0.5) * size.x,
                        (noise(seed + i * 3 + 1) - 0.5) * size.y, (noise(seed + i * 3 + 2) - 0.5) * size.z);
                litBillboard(quads, cloud.position().add(offset), right, up,
                        radius * (1 + 0.08 * Math.sin(clock * 0.03 + i)), 0xD0D3D7, 0.22,
                        VisualLight.packed(cloud.light()), -1);
            }
        }
        for (var effect : state.effects) effect(quads, effect, clock, right, up);
        for (var track : state.projectiles.values()) {
            if (state.isNative(track.state.id(), track.state.uuid())) continue;
            Vec3 position = track.motion.sample(clock);
            double distance = position.distanceTo(camera);
            if (track.state.tracer()) {
                double width = VisualMath.tracerHalfWidth(distance, Math.abs(event.getProjectionMatrix().m11()),
                        mc.getWindow().getHeight(), VisualConfig.TRACER_PIXELS.get());
                double remaining = VisualConfig.TRACER_LENGTH.get();
                Vec3 head = position;
                var history = track.history.descendingIterator();
                while (history.hasNext() && remaining > 0) {
                    Vec3 tail = history.next();
                    double length = head.distanceTo(tail);
                    if (length < 0.01) continue;
                    if (length > remaining) tail = head.add(tail.subtract(head).scale(remaining / length));
                    beam(quads, head, tail, camera, up, width * 2.4, 0xFF9D42, 0.13);
                    beam(quads, head, tail, camera, up, width, 0xFFE9B0, 0.95);
                    remaining -= length;
                    head = tail;
                }
                billboard(quads, position, right, up, width * 2.5, 0xFFAE54, 0.13);
                billboard(quads, position, right, up, width, 0xFFF1CF, 0.95);
            } else {
                double radius = track.state.kind() == VisualFrame.Kind.AUTOCANNON ? 0.08 : 0.4;
                litBillboard(quads, position, right, up, radius, track.state.kind() == VisualFrame.Kind.MORTAR ? 0x7C7B76 : 0x48443E,
                        0.95, VisualLight.packed(track.state.light()), -1);
            }
        }
        double maxDistance = state.range;
        quads.removeIf(q -> q.position.distanceToSqr(camera) > maxDistance * maxDistance || q.alpha < 0.005);
        if (quads.isEmpty()) return;
        quads.sort(Comparator.comparingDouble((Quad q) -> q.position.distanceToSqr(camera)).reversed());
        var buffer = new BufferBuilder(MEMORY, VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        for (var quad : quads) {
            double fade = Math.clamp((maxDistance - quad.position.distanceTo(camera)) / (maxDistance * 0.1), 0, 1);
            write(buffer, quad, camera, (float) (quad.alpha * fade));
        }
        var mesh = buffer.build();
        if (mesh == null) return;
        var previousShader = RenderSystem.getShader();
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int[] previousSamplers = new int[4];
        for (int unit = 0; unit < previousSamplers.length; unit++) {
            previousSamplers[unit] = GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, unit);
            GL33.glBindSampler(unit, 0);
        }
        TYPE.setupRenderState();
        try {
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, event.getModelViewMatrix(), event.getProjectionMatrix(), mc.getWindow());
            shader.setSampler("Sampler0", mc.getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES).getId());
            RoxyDepthBridge.apply(shader);
            shader.apply();
            BufferUploader.draw(mesh);
        } finally {
            shader.clear();
            TYPE.clearRenderState();
            RenderSystem.setShader(() -> previousShader);
            GL20.glUseProgram(previousProgram);
            for (int unit = 0; unit < previousSamplers.length; unit++) GL33.glBindSampler(unit, previousSamplers[unit]);
        }
    }

    private static void effect(List<Quad> quads, ClientVisuals.ActiveEffect active, double clock, Vec3 right, Vec3 up) {
        var e = active.state();
        double age = Math.max(0, clock - active.born());
        double progress = age / ClientVisuals.effectLifetime(e.kind());
        double scale = Math.clamp(e.scale(), 0.5, 16);
        double radius = scale * (0.8 + Math.sqrt(age) * 0.6);
        double opacity = Math.max(0, 1 - progress);
        int seed = Long.hashCode(e.id());
        int smokeColor = e.kind() == VisualFrame.EffectKind.FLUID
                ? IClientFluidTypeExtensions.of(BuiltInRegistries.FLUID.get(e.fluid())).getTintColor() & 0xFFFFFF : 0xA6A29A;
        for (int i = 0; i < (e.kind() == VisualFrame.EffectKind.FRAGMENTS ? 0 : 10); i++) {
            Vec3 offset = new Vec3(noise(seed + i * 3) - 0.5, noise(seed + i * 3 + 1) * 0.6,
                    noise(seed + i * 3 + 2) - 0.5).scale(radius * 1.5);
            litBillboard(quads, e.position().add(offset), right, up, radius * 0.5, smokeColor, opacity * 0.22,
                    VisualLight.packed(e.light()), -1);
        }
        if (e.kind() != VisualFrame.EffectKind.IMPACT && e.kind() != VisualFrame.EffectKind.STONE
                && e.kind() != VisualFrame.EffectKind.FLUID && e.kind() != VisualFrame.EffectKind.FRAGMENTS && age < 5) {
            billboard(quads, e.position(), right, up, scale * (2 - age * 0.2), 0xFFCF7A, (1 - age / 5) * 0.85);
        }
        if (e.kind() == VisualFrame.EffectKind.RICOCHET || e.kind() == VisualFrame.EffectKind.FRAGMENTS) {
            for (int i = 0; i < 6; i++) {
                double spread = e.kind() == VisualFrame.EffectKind.FRAGMENTS ? e.scale() : 0.5;
                Vec3 direction = e.direction().add((noise(seed+i*3)-0.5)*spread,
                        (noise(seed+i*3+1)-0.5)*spread, (noise(seed+i*3+2)-0.5)*spread).normalize();
                Vec3 point = e.position().add(direction.scale(Math.min(20, age * 0.8)));
                litBillboard(quads, point, right, up, 0.15 * scale,
                        e.kind() == VisualFrame.EffectKind.RICOCHET ? 0xFFD185 : 0x6B6660, opacity * 0.75,
                        e.kind() == VisualFrame.EffectKind.RICOCHET ? LightTexture.FULL_BRIGHT : VisualLight.packed(e.light()), -1);
            }
        }
    }

    private static void billboard(List<Quad> output, Vec3 position, Vec3 right, Vec3 up, double radius, int color, double alpha) {
        litBillboard(output, position, right, up, radius, color, alpha, LightTexture.FULL_BRIGHT, -1);
    }
    private static void litBillboard(List<Quad> output, Vec3 position, Vec3 right, Vec3 up, double radius, int color,
                                     double alpha, int light, int smokeSprite) {
        output.add(new Quad(position, right.scale(radius), up.scale(radius), color, alpha, light, smokeSprite));
    }
    private static void beam(List<Quad> output, Vec3 head, Vec3 tail, Vec3 camera, Vec3 up, double width, int color, double alpha) {
        Vec3 along = head.subtract(tail).scale(0.5), centre = head.add(tail).scale(0.5);
        Vec3 side = along.cross(camera.subtract(centre)).normalize();
        if (side.lengthSqr() < 0.01) side = up;
        output.add(new Quad(centre, along, side.scale(width), color, alpha, LightTexture.FULL_BRIGHT, -1));
    }
    private static void write(BufferBuilder buffer, Quad q, Vec3 camera, float alpha) {
        Vec3 centre = q.position.subtract(camera);
        float u0 = 0, u1 = 1, v0 = 0, v1 = 1;
        if (q.smokeSprite >= 0) {
            if (smokeSprites == null) {
                // ParticleEngine owns this atlas and registers it with TextureManager, not ModelManager.
                // Minecraft#getTextureAtlas delegates to ModelManager, whose AtlasSet has no particle entry.
                var texture = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
                if (texture instanceof TextureAtlas atlas) {
                    var loaded = new TextureAtlasSprite[12];
                    for (int i = 0; i < loaded.length; i++) loaded[i] = atlas.getSprite(ResourceLocation.withDefaultNamespace("big_smoke_" + i));
                    smokeSprites = loaded;
                }
            }
            if (smokeSprites != null) {
                var sprite = smokeSprites[q.smokeSprite];
                // An offset outside [0,1] distinguishes atlas smoke from procedural tracer/glow quads.
                u0 = 2 + sprite.getU0(); u1 = 2 + sprite.getU1(); v0 = sprite.getV0(); v1 = sprite.getV1();
            }
        }
        vertex(buffer, centre.subtract(q.a).subtract(q.b), u0, v0, q.color, alpha, q.light);
        vertex(buffer, centre.subtract(q.a).add(q.b), u0, v1, q.color, alpha, q.light);
        vertex(buffer, centre.add(q.a).add(q.b), u1, v1, q.color, alpha, q.light);
        vertex(buffer, centre.add(q.a).subtract(q.b), u1, v0, q.color, alpha, q.light);
    }
    private static void vertex(BufferBuilder b, Vec3 p, float u, float v, int rgb, float alpha, int light) {
        b.addVertex((float)p.x, (float)p.y, (float)p.z).setUv(u, v).setLight(light)
                .setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, (int) (Math.clamp(alpha, 0, 1) * 255));
    }
    private static double noise(int seed) { return ((Integer.toUnsignedLong(Integer.rotateLeft(seed * 0x45d9f3b, 13) * 0x45d9f3b)) & 0xFFFFFF) / (double)0xFFFFFF; }
    private record Quad(Vec3 position, Vec3 a, Vec3 b, int color, double alpha, int light, int smokeSprite) {}
    private FarVisualRenderer() {}
}
