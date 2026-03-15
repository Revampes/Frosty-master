package com.revampes.AfterTimeFault.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.revampes.AfterTimeFault.Revampes;
import com.revampes.AfterTimeFault.events.impl.AddParticleEvent;

@Mixin(ParticleManager.class)
public class ParticleManagerMixin {
    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"))
    private void onAddParticle(Particle particle, CallbackInfo ci) {
        if (particle != null) {
            Revampes.EVENT_BUS.post(new AddParticleEvent(particle));            
        }
    }
}
