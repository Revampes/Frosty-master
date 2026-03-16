package com.revampes.Fault.modules.impl.render;

import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.SliderSetting;

public class NoHurtCam extends Module {

    public SliderSetting multiplier;

    public NoHurtCam() {
        super("NoHurtCam", category.Render);

        this.registerSetting(multiplier = new SliderSetting("Multiplier", "x", 0, 0, 14, 1));
    }
}
