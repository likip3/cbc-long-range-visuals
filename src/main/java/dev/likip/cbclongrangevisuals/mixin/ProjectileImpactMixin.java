package dev.likip.cbclongrangevisuals.mixin;

import dev.likip.cbclongrangevisuals.server.ServerVisuals;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;

/** CBC's custom collision loop does not use the vanilla ProjectileImpactEvent. */
@Mixin(value = AbstractCannonProjectile.class, remap = false)
public abstract class ProjectileImpactMixin {
    @Inject(method = "onImpact", at = @At("HEAD"), remap = false)
    private void cbcLongRangeVisuals$impact(HitResult hit, AbstractCannonProjectile.ImpactResult result,
                                          ProjectileContext context, CallbackInfoReturnable<Boolean> cir) {
        ServerVisuals.impact((AbstractCannonProjectile) (Object) this, hit, result);
    }
}
