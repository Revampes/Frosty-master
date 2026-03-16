package com.revampes.Fault.mixin;

import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.revampes.Fault.modules.impl.other.MoveFix;
import com.revampes.Fault.utility.Rotations;

@Mixin(PlayerInteractItemC2SPacket.class)
public class PlayerInteractItemC2SPacketMixin {
    @Mutable
    @Shadow
    @Final
    private float yaw;

    @Mutable
    @Shadow
    @Final
    private float pitch;

    @Inject(method = "<init>(Lnet/minecraft/util/Hand;IFF)V", at = @At("RETURN"))
    private void modifyRotation(Hand hand, int sequence, float yaw, float pitch, CallbackInfo ci) {
        if (Rotations.rotating) {
            this.yaw = Rotations.serverYaw;
            this.pitch = Rotations.serverPitch;
        }
    }
}
