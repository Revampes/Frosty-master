package com.revampes.Fault.modules.impl.render;

import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.SelectSetting;

public class Fullbright extends Module {

    private SelectSetting mode;

    private String[] modes = new String[]{"Gamma"};

    public int selectedMode;

    public Fullbright() {
        super("Fullbright", category.Render);

        this.registerSetting(mode = new SelectSetting("Mode", 0, modes));
    }

    @Override
    public void onEnable() {
        selectedMode = (int) mode.getValue();
    }

    @Override
    public void onDisable() {
        if (selectedMode == 0) {
            mc.gameRenderer.getLightmapTextureManager().tick();
        }
    }
}
