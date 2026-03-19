package com.revampes.Fault.mixin;

import com.revampes.Fault.Revampes;
import com.revampes.Fault.events.impl.RenderScreenEvent;
import com.revampes.Fault.events.impl.SlotClickEvent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow @Nullable protected Slot focusedSlot;

    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"), cancellable = true)
    private void onMouseClickHook(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        if (Revampes.EVENT_BUS.post(new SlotClickEvent(slot, slotId, button, actionType)).isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderHook(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Revampes.EVENT_BUS.post(new RenderScreenEvent(context, mouseX, mouseY, delta, (HandledScreen<?>) (Object) this));
    }
}
