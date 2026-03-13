package com.revampes.AfterTimeFault.modules.impl.client;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.InputUtil;
import com.revampes.AfterTimeFault.gui.ClickGui;
import com.revampes.AfterTimeFault.modules.Module;
import com.revampes.AfterTimeFault.settings.impl.InputSetting;
import com.revampes.AfterTimeFault.settings.impl.SelectSetting;
import com.revampes.AfterTimeFault.settings.impl.SliderSetting;
import com.revampes.AfterTimeFault.utility.Utils;

public class UI extends Module {

    public SelectSetting clickGuiColor;
    private String[] clickGuiColors = new String[] {"Light", "Dark"};

    public UI() {
        super("ClickGui", category.Client, InputUtil.GLFW_KEY_RIGHT_SHIFT);

        this.registerSetting(clickGuiColor = new SelectSetting("Background Color", 0, clickGuiColors));
    }

    @Override
    public String getDesc() {
        return "This module is a global setting";
    }

    public void onEnable() {
        if (!Utils.nullCheck()) {
            return;
        }
        mc.setScreen(new ClickGui());
    }

    public void onDisable() {
        if (!Utils.nullCheck()) {
            return;
        }
        if (mc.currentScreen instanceof ClickGui) {
            mc.setScreen(null);
        }
    }

    @Override
    public void onUpdate() {
        if (!(mc.currentScreen instanceof ClickGui)) {
            this.disable();
        }
    }
}
