package com.revampes.Fault.mixin;

import com.revampes.Fault.modules.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class TerminalOpenCloseMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void onScreenOpen(CallbackInfo ci) {
        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        if (screen.getScreenHandler() instanceof GenericContainerScreenHandler handler) {
            if (ModuleManager.terminalManager != null) {
                String title = screen.getTitle().getString();
                int windowId = handler.syncId;
                int slotCount = handler.getRows() * 9;
                ModuleManager.terminalManager.handleScreenOpen(title, windowId, slotCount, handler);                
                // Schedule slot reading after a short delay to let slots populate
                new Thread(() -> {
                    try {
                        Thread.sleep(100); // Wait for server to send slot update packets
                        ModuleManager.terminalManager.handleSlotReading(handler);
                    } catch (Exception ignored) {
                    }
                }).start();            }
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void onScreenClose(CallbackInfo ci) {
        if (ModuleManager.terminalManager == null) return;

        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        if (!(screen.getScreenHandler() instanceof GenericContainerScreenHandler closingHandler)) {
            ModuleManager.terminalManager.handleScreenClose();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean stillInSameContainer = mc.player != null
            && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler currentHandler
            && currentHandler.syncId == closingHandler.syncId;

        if (!stillInSameContainer) {
            ModuleManager.terminalManager.handleScreenClose();
        }
    }
}
