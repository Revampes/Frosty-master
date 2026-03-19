package com.revampes.Fault.mixin;

import com.revampes.Fault.Revampes;
import com.revampes.Fault.events.impl.RenderScreenEvent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderInventory(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Revampes.EVENT_BUS.post(new RenderScreenEvent(context, mouseX, mouseY, delta, (InventoryScreen) (Object) this));
    }
}
