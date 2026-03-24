package com.revampes.Fault.modules.impl.client;

import net.minecraft.client.util.InputUtil;
import com.revampes.Fault.gui.ClickGui;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.ColorSetting;
import com.revampes.Fault.settings.impl.SelectSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.Utils;

import java.awt.Color;

public class UI extends Module {

    public final SelectSetting clickGuiColor;
    public final SliderSetting guiScale;
    public final SliderSetting moduleButtonHeight;
    public final SliderSetting subsettingsGap;
    public final ButtonSetting clickSound;
    public final ButtonSetting customColors;
    public final ColorSetting accentColor;
    public final ColorSetting backgroundColor;
    public final ColorSetting panelColor;
    public final ColorSetting moduleEnabledColor;

    private final String[] clickGuiColors = new String[] {"Light", "Dark"};

    public UI() {
        super("ClickGui", category.Client, InputUtil.GLFW_KEY_RIGHT_SHIFT);

        this.registerSetting(clickGuiColor = new SelectSetting("Background Color", 0, clickGuiColors));
        this.registerSetting(guiScale = new SliderSetting("Gui Scale", "x", 1.0, 0.8, 1.6, 0.05));
        this.registerSetting(moduleButtonHeight = new SliderSetting("Module Button Size", 15.0, 12.0, 24.0, 1.0));
        this.registerSetting(subsettingsGap = new SliderSetting("Subsettings Gap", 5.0, 3.0, 14.0, 1.0));
        this.registerSetting(clickSound = new ButtonSetting("Click Sound", true));

        this.registerSetting(customColors = new ButtonSetting("Custom Colors", false));
        this.registerSetting(accentColor = new ColorSetting("Accent Color", new Color(100, 100, 255, 255)));
        this.registerSetting(backgroundColor = new ColorSetting("Background Color Custom", new Color(240, 240, 240, 255)));
        this.registerSetting(panelColor = new ColorSetting("Panel Color", new Color(220, 220, 220, 255)));
        this.registerSetting(moduleEnabledColor = new ColorSetting("Enabled Module Color", new Color(180, 220, 255, 255)));
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
        if (mc.currentScreen instanceof ClickGui || mc.currentScreen instanceof com.revampes.Fault.gui.screen.HudEditorScreen) {
            mc.setScreen(null);
        }
    }

    @Override
    public void onUpdate() {
        if (!(mc.currentScreen instanceof ClickGui) && !(mc.currentScreen instanceof com.revampes.Fault.gui.screen.HudEditorScreen)) {
            this.disable();
        }
    }

    public boolean isLightTheme() {
        return clickGuiColor.getValue() == 0;
    }

    public boolean useCustomColors() {
        return customColors.isToggled();
    }
}
