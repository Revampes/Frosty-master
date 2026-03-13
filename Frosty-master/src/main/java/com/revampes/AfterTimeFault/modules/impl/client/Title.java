package com.revampes.AfterTimeFault.modules.impl.client;

import com.revampes.AfterTimeFault.modules.Module;
import com.revampes.AfterTimeFault.settings.impl.ButtonSetting;
import com.revampes.AfterTimeFault.settings.impl.InputSetting;

public class Title extends Module {

    public ButtonSetting keepOriginal;

    public Title() {
        super("Title", category.Client);

        this.registerSetting(keepOriginal = new ButtonSetting("Keep original", true));
    }
}
