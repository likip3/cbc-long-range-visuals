package dev.likip.cbclongrangevisuals.mixin;

import dev.likip.cbclongrangevisuals.server.ServerVisuals;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rbasamoyai.createbigcannons.munitions.fragment_burst.CBCProjectileBurst;
import rbasamoyai.ritchiesprojectilelib.projectile_burst.ProjectileBurst;

@Mixin(value = CBCProjectileBurst.class, remap = false)
public abstract class ProjectileBurstMixin {
    @Inject(method = "spawnConeBurst", at = @At("RETURN"), remap = false)
    private static void cbcLongRangeVisuals$burst(Level level, EntityType<?> type, Vec3 position,
                                                Vec3 velocity, int count, double spread,
                                                CallbackInfoReturnable<ProjectileBurst> cir) {
        ServerVisuals.burst(level, type, position, velocity, spread, cir.getReturnValue().getId());
    }
}
