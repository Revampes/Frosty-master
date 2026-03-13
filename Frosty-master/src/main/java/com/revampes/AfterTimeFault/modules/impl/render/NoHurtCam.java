package com.revampes.AfterTimeFault.modules.impl.render;

import com.revampes.AfterTimeFault.modules.Module;
import com.revampes.AfterTimeFault.settings.impl.SliderSetting;

public class NoHurtCam extends Module {

    public SliderSetting multiplier;

    public NoHurtCam() {
        super("NoHurtCam", category.Render);

        this.registerSetting(multiplier = new SliderSetting("Multiplier", "x", 0, 0, 14, 1));
    }
}
