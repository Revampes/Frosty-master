package com.revampes.AfterTimeFault.modules.impl.other;

import com.revampes.AfterTimeFault.events.impl.Render2DEvent;
import com.revampes.AfterTimeFault.events.impl.SimpleChatEventHandler;
import com.revampes.AfterTimeFault.gui.container.RagnarockTimerContainer;
import com.revampes.AfterTimeFault.modules.Module;
import com.revampes.AfterTimeFault.settings.impl.SliderSetting;
import com.revampes.AfterTimeFault.settings.impl.ButtonSetting;
import com.revampes.AfterTimeFault.utility.SkyblockItem;
import com.revampes.AfterTimeFault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.Optional;

public class RagnarockTimer extends Module implements SimpleChatEventHandler.Overlay {
    public SliderSetting xPos = new SliderSetting("X Position", "%", 50, 0, 100, 1);
    public SliderSetting yPos = new SliderSetting("Y Position", "%", 20, 0, 100, 1);
    public ButtonSetting showCooldown = new ButtonSetting("Show Cooldown", true);

    private static final String ID_RAGNAROCK = "RAGNAROCK";

    public RagnarockTimer() {
        super("RagnarockTimer", category.Other);
        this.registerSetting(xPos);
        this.registerSetting(yPos);
        this.registerSetting(showCooldown);
    }

    @Override
    public String getDesc() {
        return "Displays a custom GUI to show ragnarock status";
    }

    @Override
    public void onReceiveOverlay(String message) {
        if (!Utils.nullCheck()) return;

        String unformatted = Utils.stripColor(message).trim();

        long current = Util.getMeasuringTimeMs();

        if (unformatted.contains("CASTING")) {
            if (unformatted.contains(" IN ")) {
                if (RagnarockTimerContainer.INSTANCE.castStartTimestamp != 0 && (current - RagnarockTimerContainer.INSTANCE.castStartTimestamp < 3000)) {
                    // Prevent reset from multiple 3s/2s/1s messages
                    return;
                }
                RagnarockTimerContainer.INSTANCE.castStartTimestamp = current;
                RagnarockTimerContainer.INSTANCE.buffStartTimestamp = 0; // reset buff
                RagnarockTimerContainer.INSTANCE.showCooldown = showCooldown.isEnabled;
                return;
            }

            // It's the successful CASTING message
            if (RagnarockTimerContainer.INSTANCE.buffStartTimestamp != 0 && (current - RagnarockTimerContainer.INSTANCE.buffStartTimestamp < 10000)) {
                // Buff is already active, ignore repeated CASTING messages
                return;
            }

            // Immediately set it so the timer starts, we will refine the strength value below if possible
            RagnarockTimerContainer.INSTANCE.buffStartTimestamp = current;
            
            if (mc.player == null) return;
            ItemStack mainHandItem = mc.player.getMainHandStack();
            SkyblockItem skyblockItem = SkyblockItem.from(mainHandItem).orElse(null);
            
            if (skyblockItem == null) {
                return;
            }
            
            if (skyblockItem.getID().map(id -> id.contains(ID_RAGNAROCK)).orElse(false)) { 
                Text strengthComponent = skyblockItem.getStyledLoreLines()
                        .flatMap(it -> it.stream()
                                .filter(line -> line.getString().contains("Strength: ")) 
                                .findAny())
                        .orElse(null);
                        
                if (strengthComponent != null) {
                    String strengthText = strengthComponent.getString();
                    int startIndex = strengthText.indexOf("Strength: ") + "Strength: ".length();
                    int endIndex = strengthText.indexOf(' ', startIndex);
                    strengthText = strengthText.substring(startIndex, endIndex > 0 ? endIndex : strengthText.length()).replaceAll("[^0-9.]", "");

                    try {
                        float gained = Float.parseFloat(strengthText) * 1.5F;
                        RagnarockTimerContainer.INSTANCE.gainedStrength = gained;
                    } catch (NumberFormatException e) {
                    }
                }
            }
        }
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (RagnarockTimerContainer.INSTANCE.isActive()) {
            Utils.scaledProjection();

            float xCenter = (float) ((mc.getWindow().getScaledWidth() * xPos.getInput()) / 100.0);
            float yTop = (float) ((mc.getWindow().getScaledHeight() * yPos.getInput()) / 100.0);

            RagnarockTimerContainer.INSTANCE.render(
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
