package dev.likip.cbclongrangevisuals.mixin;

import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import rbasamoyai.createbigcannons.munitions.big_cannon.fluid_shell.FluidExplosion;

@Mixin(value = FluidExplosion.class, remap = false)
public interface FluidExplosionAccess {
    @Accessor(value = "fluid", remap = false)
    Fluid cbcLongRangeVisuals$fluid();
}
