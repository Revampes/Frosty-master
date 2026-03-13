package com.revampes.AfterTimeFault.modules.impl.other;

import meteordevelopment.orbit.EventHandler;
import com.revampes.AfterTimeFault.events.impl.PostSendMovementPacketsEvent;
import com.revampes.AfterTimeFault.modules.Module;
import com.revampes.AfterTimeFault.modules.ModuleManager;
import com.revampes.AfterTimeFault.settings.impl.ButtonSetting;
import com.revampes.AfterTimeFault.utility.Rotations;
import com.revampes.AfterTimeFault.utility.Utils;

import java.util.Objects;

public class MoveFix extends Module {

    public ButtonSetting foragingOnly;

    public MoveFix() {
        super("MoveFix", category.Other);

        this.registerSetting(foragingOnly = new ButtonSetting("Foraging Island only", false));
    }

    @Override
    public String getDesc() {
        return "Correct movement while silent rotating";
    }

    public static boolean shouldApply() {
        if (ModuleManager.moveFix.isEnabled() && Rotations.rotating) {
            return !ModuleManager.moveFix.foragingOnly.isToggled() || (Objects.equals(Utils.getCurrentLocation().get("Area"), "Galatea") || Objects.equals(Utils.getCurrentLocation().get("Area"), "The Park"));
        }
        return false;
    }

    @EventHandler
    public void onPostSendMovementPacket(PostSendMovementPacketsEvent event) {
        if (shouldApply()) {
        }
    }
}
