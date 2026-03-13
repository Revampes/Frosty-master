package com.revampes.AfterTimeFault.modules.impl.render;

import com.revampes.AfterTimeFault.modules.Module;
import com.revampes.AfterTimeFault.settings.impl.ButtonSetting;

public class AntiDebuff extends Module {

    public ButtonSetting nausea;

    public AntiDebuff() {
        super("AntiDebuff", category.Render);

        this.registerSetting(nausea = new ButtonSetting("Nausea", true));
    }
}
