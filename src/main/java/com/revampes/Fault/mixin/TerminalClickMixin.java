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
    private boolean terminalLeftMouseHeld = false;
    private boolean terminalRightMouseHeld = false;
    private int terminalLastHoldSlot = -1;
    private long terminalLastHoldClickAt = 0L;

    private int getOverlaySlot(HandledScreen<?> screen, int mouseX, int mouseY, int windowSize) {
        int screenWidth = screen.width;
        int screenHeight = screen.height;

        float scale = ModuleManager.terminalManager.getOverlayScale();
        int offsetX = (int) Math.round(ModuleManager.terminalManager.getOverlayOffsetX() * scale);
        String terminalName = ModuleManager.terminalManager.getActiveTerminalName();
        float titleOffset = "Melody".equals(terminalName)
            ? 0.0f
            : ("Numbers".equals(terminalName) ? 9.0f : ("Rubix".equals(terminalName) ? 18.0f : 18.0f));
        int offsetY = (int) Math.round(ModuleManager.terminalManager.getOverlayOffsetY() * scale + titleOffset * scale);

        int hitboxWindowSize = windowSize;
        if (("Numbers".equals(terminalName) || "Rubix".equals(terminalName)) && windowSize <= 45) {
            // Their render layout is title + 5 rows, which is 6 row-heights in total.
            hitboxWindowSize = windowSize + 9;
        }

        return TerminalRenderUtils.getClickedSlot(
            mouseX, mouseY,
            screenWidth, screenHeight,
            hitboxWindowSize, scale, offsetX, offsetY
        );
    }
    
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
        
        int mouseX = (int) click.x();
        int mouseY = (int) click.y();
        int button = click.button();
        if (button == 0) {
            terminalLeftMouseHeld = true;
        } else if (button == 1) {
            terminalRightMouseHeld = true;
        }

        int slot = getOverlaySlot(screen, mouseX, mouseY, windowSize);
        
        if (slot >= 0) {
            ModuleManager.terminalManager.handleTerminalClick(slot, button);
            cir.setReturnValue(true); // Cancel the event so container doesn't handle it
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void onMouseReleased(Click click, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (click.button() == 0) {
            terminalLeftMouseHeld = false;
            terminalLastHoldSlot = -1;
        } else if (click.button() == 1) {
            terminalRightMouseHeld = false;
            terminalLastHoldSlot = -1;
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(Click click, double offsetXDrag, double offsetYDrag, CallbackInfoReturnable<Boolean> cir) {
        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;

        if (!(screen.getScreenHandler() instanceof GenericContainerScreenHandler)) {
            return;
        }

        if (ModuleManager.terminalManager == null || !ModuleManager.terminalManager.hasActiveTerminal()) {
            return;
        }

        if (!ModuleManager.terminalManager.isHoldClickEnabled()) {
            return;
        }

        boolean leftHeld = terminalLeftMouseHeld && click.button() == 0;
        boolean rightHeld = terminalRightMouseHeld && click.button() == 1;
        if (!leftHeld && !rightHeld) {
            return;
        }

        int windowSize = ModuleManager.terminalManager.getActiveWindowSize();
        if (windowSize <= 0) return;

        int mouseX = (int) click.x();
        int mouseY = (int) click.y();
        int slot = getOverlaySlot(screen, mouseX, mouseY, windowSize);

        if (slot < 0) {
            terminalLastHoldSlot = -1;
            return;
        }

        long now = System.currentTimeMillis();
        long interval = ModuleManager.terminalManager.getEffectiveHoldClickIntervalMs();
        boolean canClick = slot != terminalLastHoldSlot || (now - terminalLastHoldClickAt) >= interval;
        if (!canClick) {
            cir.setReturnValue(true);
            return;
        }

        int holdButton = click.button() == 1 ? 1 : 0;
        terminalLastHoldSlot = slot;
        terminalLastHoldClickAt = now;
        ModuleManager.terminalManager.handleTerminalClick(slot, holdButton);
        cir.setReturnValue(true);
    }
}
