package com.revampes.AfterTimeFault.modules.impl.other;

import meteordevelopment.orbit.EventHandler;
import com.revampes.AfterTimeFault.events.impl.ReceiveMessageEvent;
import com.revampes.AfterTimeFault.events.impl.Render2DEvent;
import com.revampes.AfterTimeFault.modules.Module;
import com.revampes.AfterTimeFault.settings.impl.SliderSetting;
import com.revampes.AfterTimeFault.utility.Utils;
import com.revampes.AfterTimeFault.gui.container.AutoPetNotificationContainer;

public class AutoPetNotification extends Module {

    public SliderSetting xPos = new SliderSetting("X Position", "%", 50, 0, 100, 1);
    public SliderSetting yPos = new SliderSetting("Y Position", "%", 20, 0, 100, 1);

    public AutoPetNotification() {
        super("AutoPetNotification", category.Other);
        this.registerSetting(xPos);
        this.registerSetting(yPos);
    }

    @Override
    public String getDesc() {
        return "Displays a custom GUI notification when Autopet equips a pet";
    }

    @EventHandler
    public void onReceiveMessage(ReceiveMessageEvent event) {
        if (!Utils.nullCheck()) return;

        String text = event.getMessage().getString();

        // Strip colors in case there are formatting codes mixed in
        String unformatted = Utils.stripColor(text);

        if (unformatted.contains("Autopet equipped your ")) {
            try {
                String prefix = "Autopet equipped your ";
                int startIndex = unformatted.indexOf(prefix) + prefix.length();
                int endIndex = unformatted.indexOf('!', startIndex);

                if (endIndex == -1) endIndex = unformatted.length();

                String petText = unformatted.substring(startIndex, endIndex).trim();

                // Custom GUI logic
                AutoPetNotificationContainer.INSTANCE.warningText = "Autopet equipped " + petText;
                AutoPetNotificationContainer.INSTANCE.lastTriggeredTimestamp = net.minecraft.util.Util.getMeasuringTimeMs();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (AutoPetNotificationContainer.INSTANCE.isActive()) {
            Utils.scaledProjection();

            float xCenter = (float) ((mc.getWindow().getScaledWidth() * xPos.getInput()) / 100.0);
            float yTop = (float) ((mc.getWindow().getScaledHeight() * yPos.getInput()) / 100.0);

            AutoPetNotificationContainer.INSTANCE.render(
                event.drawContext,
                xCenter,
                yTop,
                0,
                0,
                1.0f
            );

            Utils.unscaledProjection();
        }
    }
}