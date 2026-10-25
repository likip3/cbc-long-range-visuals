package dev.likip.cbclongrangevisuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import rbasamoyai.createbigcannons.munitions.ShellExplosion;

@Mixin(value = ShellExplosion.class, remap = false)
public interface ShellExplosionAccess {
    @Accessor(value = "noEffects", remap = false)
    boolean cbcLongRangeVisuals$noEffects();
}
