package com.revampes.Fault.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import com.revampes.Fault.modules.impl.other.MoveFix;
import com.revampes.Fault.utility.DirectionalInput;
import com.revampes.Fault.utility.Rotations;

import static com.revampes.Fault.Revampes.mc;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends InputMixin {
    @Shadow @Final private GameOptions settings;

    @Unique
    private DirectionalInput revampes$getCorrectedInput() {
        if (MoveFix.shouldApply() &&  mc.player != null) {
            DirectionalInput originalInput = new DirectionalInput(
                    this.settings.forwardKey.isPressed(),
                    this.settings.backKey.isPressed(),
                    this.settings.leftKey.isPressed(),
                    this.settings.rightKey.isPressed()
            );

            return Rotations.calculateCorrectedInput(
                    originalInput,
                    mc.player.getYaw(),
                    Rotations.lastServerYaw
            );
        }

        return new DirectionalInput(
                this.settings.forwardKey.isPressed(),
                this.settings.backKey.isPressed(),
                this.settings.leftKey.isPressed(),
                this.settings.rightKey.isPressed()
        );
    }

    @ModifyExpressionValue(
            method = "tick",
            at = @At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/util/PlayerInput;")
    )
    private PlayerInput modifyInput(PlayerInput original) {
        if (!MoveFix.shouldApply() || mc.player == null) {
            return original;
        }

        DirectionalInput corrected = revampes$getCorrectedInput();

        return new PlayerInput(
                corrected.isForwards(),
                corrected.isBackwards(),
                corrected.isLeft(),
                corrected.isRight(),
                original.jump(),
                original.sneak(),
                original.sprint()
        );
    }
}
