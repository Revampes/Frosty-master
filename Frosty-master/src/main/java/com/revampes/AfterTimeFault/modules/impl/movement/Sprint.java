package com.revampes.AfterTimeFault.modules.impl.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import com.revampes.AfterTimeFault.events.impl.PreUpdateEvent;
import com.revampes.AfterTimeFault.modules.Module;
import com.revampes.AfterTimeFault.settings.impl.ButtonSetting;
import com.revampes.AfterTimeFault.settings.impl.SliderSetting;
import com.revampes.AfterTimeFault.utility.Utils;

public class Sprint extends Module {

    public ButtonSetting keep;
    public SliderSetting slow;

    public Sprint() {
        super("Sprint", category.Movement);

        this.registerSetting(keep = new ButtonSetting("Keep", false));
        this.registerSetting(slow = new SliderSetting("Slow", "%", 0, 0, 40, 1));

    }

    @Override
    public void guiUpdate() {
        this.slow.setVisibilityCondition(() -> keep.isToggled());
    }


    @EventHandler
    public void onPreUpdate(PreUpdateEvent event) {
        if (!Utils.nullCheck() && mc.isWindowFocused()) {
            return;
        }
        if (mc.player.forwardSpeed != 0) {
            if (!mc.options.getSprintToggled().getValue()) {
                mc.options.sprintKey.setPressed(true);
            } else if (mc.options.getSprintToggled().getValue() && !mc.player.isSprinting()) {
                mc.player.setSprinting(true);
            }
        }
    }
}
