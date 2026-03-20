package com.revampes.Fault.mixin;

import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.utility.terminals.TerminalRenderUtils;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class TerminalClickMixin {
    
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClick(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        
        if (!(screen.getScreenHandler() instanceof GenericContainerScreenHandler handler)) {
            return;
        }
        
        if (ModuleManager.terminalManager == null || !ModuleManager.terminalManager.hasActiveTerminal()) {
            return;
        }
        
        // Try to click on terminal overlay
        int windowSize = ModuleManager.terminalManager.getActiveWindowSize();
        if (windowSize <= 0) return;
        int screenWidth = screen.width;
        int screenHeight = screen.height;
        
        int mouseX = (int) click.x();
        int mouseY = (int) click.y();
        int button = click.button();

        float scale = ModuleManager.terminalManager.getOverlayScale();
        int offsetX = (int) Math.round(ModuleManager.terminalManager.getOverlayOffsetX() * scale);
        String terminalName = ModuleManager.terminalManager.getActiveTerminalName();
        float titleOffset = "Numbers".equals(terminalName) ? 9.0f : ("Rubix".equals(terminalName) ? 18.0f : 18.0f);
        int offsetY = (int) Math.round(ModuleManager.terminalManager.getOverlayOffsetY() * scale + titleOffset * scale);

        int hitboxWindowSize = windowSize;
        if (("Numbers".equals(terminalName) || "Rubix".equals(terminalName)) && windowSize <= 45) {
            // Their render layout is title + 5 rows, which is 6 row-heights in total.
            hitboxWindowSize = windowSize + 9;
        }
        
        // getClickedSlot does centering internally, offsetY is additional offset for title bar
        int slot = TerminalRenderUtils.getClickedSlot(
            mouseX, mouseY,
            screenWidth, screenHeight,
            hitboxWindowSize, scale, offsetX, offsetY
        );
        
        if (slot >= 0) {
            System.out.println("[TerminalClickMixin] Clicked slot: " + slot);
            ModuleManager.terminalManager.handleTerminalClick(slot, button);
            cir.setReturnValue(true); // Cancel the event so container doesn't handle it
        }
    }
}
