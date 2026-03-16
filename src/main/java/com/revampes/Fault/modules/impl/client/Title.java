package com.revampes.Fault.modules.impl.client;

import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.InputSetting;

public class Title extends Module {

    public ButtonSetting keepOriginal;

    public Title() {
        super("Title", category.Client);

        this.registerSetting(keepOriginal = new ButtonSetting("Keep original", true));
    }
}
