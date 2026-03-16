package com.revampes.Fault.modules.impl.render;

import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;

public class AntiDebuff extends Module {

    public ButtonSetting nausea;

    public AntiDebuff() {
        super("AntiDebuff", category.Render);

        this.registerSetting(nausea = new ButtonSetting("Nausea", true));
    }
}
