package com.revampes.Fault.modules.impl.render;

import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;

public class AntiDebuff extends Module {

    public ButtonSetting nausea;
    public ButtonSetting antiBlind;
    public ButtonSetting antiPortal;

    public AntiDebuff() {
        super("AntiDebuff", category.Render);

        this.registerSetting(nausea = new ButtonSetting("Nausea", true));
        this.registerSetting(antiBlind = new ButtonSetting("Blind", true));
        this.registerSetting(antiPortal = new ButtonSetting("Portal", true));
    }
}
