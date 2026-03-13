package com.revampes.AfterTimeFault.modules.impl.render;

import com.revampes.AfterTimeFault.modules.Module;
import com.revampes.AfterTimeFault.settings.impl.ButtonSetting;

public class NoOverlay extends Module {

    public ButtonSetting fire, water, inWall;

    public NoOverlay() {
        super("NoOverlay", category.Render);

        this.registerSetting(fire = new ButtonSetting("Fire", true));
        this.registerSetting(water = new ButtonSetting("Water", true));
        this.registerSetting(inWall = new ButtonSetting("In Wall", true));
    }
}
