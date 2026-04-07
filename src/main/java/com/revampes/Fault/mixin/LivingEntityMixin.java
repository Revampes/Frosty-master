package com.revampes.Fault.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.revampes.Fault.modules.impl.render.blockanimation.BlockAnimation;
import com.revampes.Fault.modules.ModuleManager;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "swingHand(Lnet/minecraft/util/Hand;)V", at = @At("HEAD"))
    private void onSwingHand(Hand hand, CallbackInfo ci) {
        BlockAnimation.startSwing(hand);
    }

    @Inject(method = "hasStatusEffect", at = @At("HEAD"), cancellable = true)
    private void modifyBlindnessCheck(RegistryEntry<?> effect, CallbackInfoReturnable<Boolean> cir) {
        // If antiBlind is enabled and checking for BLINDNESS, cancel and return false
        if (ModuleManager.antiDebuff.isEnabled() && ModuleManager.antiDebuff.antiBlind.isToggled()) {
            if (effect == StatusEffects.BLINDNESS) {
                cir.setReturnValue(false);
            }
        }
    }
}
