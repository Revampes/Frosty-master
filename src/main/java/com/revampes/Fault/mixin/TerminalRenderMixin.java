package com.revampes.Fault.mixin;

import com.revampes.Fault.Revampes;
import com.revampes.Fault.events.impl.RenderScreenEvent;
import com.revampes.Fault.modules.ModuleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class TerminalRenderMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (ModuleManager.terminalManager != null && ModuleManager.terminalManager.hasActiveTerminal()) {
            Revampes.EVENT_BUS.post(new RenderScreenEvent(context, mouseX, mouseY, deltaTicks, (HandledScreen<?>) (Object) this));
            ci.cancel();
        }
    }
    
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void onRenderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        // Hide container background when terminal is active
        if (ModuleManager.terminalManager != null && ModuleManager.terminalManager.hasActiveTerminal()) {
            ci.cancel();
        }
    }
    
    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
    private void hideTooltip(DrawContext context, int x, int y, CallbackInfo ci) {
        // Hide tooltips when terminal is active
        if (ModuleManager.terminalManager != null && ModuleManager.terminalManager.hasActiveTerminal()) {
            ci.cancel();
        }
    }
}
