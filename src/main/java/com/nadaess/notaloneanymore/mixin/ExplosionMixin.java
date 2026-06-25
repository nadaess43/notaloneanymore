package com.nadaess.notaloneanymore.mixin;

import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public class ExplosionMixin {
    @Inject(method = "finalizeExplosion", at = @At("HEAD"))
    private void onExplode(boolean spawnParticles, CallbackInfo ci) {
        Explosion explosion = (Explosion) (Object) this;
    }
}