package com.revampes.Fault.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import com.revampes.Fault.interfaces.IInput;

@Mixin(Input.class)
public abstract class InputMixin implements IInput {

    @Shadow
    public PlayerInput playerInput;

    @Shadow
    public abstract Vec2f getMovementInput();

    @Unique
    protected PlayerInput initial = PlayerInput.DEFAULT;

    @Unique
    protected PlayerInput untransformed = PlayerInput.DEFAULT;

    @Override
    public PlayerInput revampes$getInitial() {
        return initial;
    }

    @Override
    public PlayerInput revampes$getUntransformed() {
        return untransformed;
    }

}
