package com.revampes.AfterTimeFault.modules.impl.other;

import meteordevelopment.orbit.EventHandler;
import com.revampes.AfterTimeFault.events.impl.PreUpdateEvent;
import com.revampes.AfterTimeFault.mixin.accessor.MinecraftClientAccessor;
import com.revampes.AfterTimeFault.modules.Module;
import com.revampes.AfterTimeFault.settings.impl.SliderSetting;

public class FastPlace extends Module {

    private SliderSetting delay;

    public FastPlace() {
        super("FastPlace", category.Other);

        this.registerSetting(delay = new SliderSetting("Delay", 1, 0, 3, 1));
    }

    @EventHandler
    public void onPreUpdate(PreUpdateEvent event) {
        if (((MinecraftClientAccessor)mc).revampes$getItemUseCooldown() > delay.getInput()) {
            ((MinecraftClientAccessor)mc).revampes$setItemUseCooldown((int) delay.getInput());
        }
    }
}
