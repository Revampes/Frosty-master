package com.revampes.Fault.mixin;

import com.revampes.Fault.modules.ModuleManager;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class TerminalSlotUpdateMixin {

    @Inject(method = "setStackInSlot", at = @At("TAIL"))
    private void onSlotUpdate(int slot, int revision, ItemStack itemStack, CallbackInfo ci) {
        if (ModuleManager.terminalManager != null && ModuleManager.terminalManager.hasActiveTerminal()) {
            ModuleManager.terminalManager.handleSlotUpdate(slot, itemStack);
        }
    }
}
