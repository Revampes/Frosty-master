package com.revampes.AfterTimeFault.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.revampes.AfterTimeFault.modules.impl.render.blockanimation.BlockAnimation;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "swingHand(Lnet/minecraft/util/Hand;)V", at = @At("HEAD"))
    private void onSwingHand(Hand hand, CallbackInfo ci) {
        BlockAnimation.startSwing(hand);
    }
}
