package com.revampes.Fault.modules.impl.other;

import java.util.Arrays;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import com.revampes.Fault.events.impl.ReceiveMessageEvent;
import com.revampes.Fault.gui.NotificationManager;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.Utils;

public class LifeSaverTimer extends Module {

    public enum LifeSavers {
        BONZO_MASK("Bonzo's Mask", 360_000, true), // based on catacomb level
        SPIRIT_MASK("Spirit Mask", 30_000, false),
        PHOENIX_PET("Phoenix Pet", 60_000, false);

        public String name;
        public long cooldown; // in milliseconds
        public boolean shouldReadLore;

        LifeSavers(String name, long cooldown, boolean shouldReadLore) {
            this.name = name;
            this.cooldown = cooldown;
            this.shouldReadLore = shouldReadLore;
        }
    }

    public SliderSetting xPos = new SliderSetting("X Position", "%", 50, 0, 100, 1);
    public SliderSetting yPos = new SliderSetting("Y Position", "%", 30, 0, 100, 1);
    
    public LifeSavers lastTriggered;
    public long[] availableTimestamp = new long[LifeSavers.values().length];

    public LifeSaverTimer() {
        super("LifeSaverTimer", category.Other);
        this.registerSetting(xPos);
        this.registerSetting(yPos);
    }
    
    @Override
    public String getDesc() {
        return "Notifies you when a lifesaver item is triggered.";
    }

    public void reset() {
        Arrays.fill(availableTimestamp, 0);
    }

    @EventHandler
    public void onReceiveMessage(ReceiveMessageEvent event) {
        if (!Utils.nullCheck()) return;

        String plain = Utils.stripColor(event.getMessage().getString());
        LifeSavers triggered = null;

        if (plain.startsWith("Your ") && plain.endsWith("Bonzo's Mask saved your life!")) {
            triggered = LifeSavers.BONZO_MASK;
        } else if (plain.equals("Second Wind Activated! Your Spirit Mask saved your life!")) {
            triggered = LifeSavers.SPIRIT_MASK;
        } else if (plain.equals("Your Phoenix Pet saved you from certain death!")) {
            triggered = LifeSavers.PHOENIX_PET;
        } else {
            return;
        }

        long cooldownTime = triggered.cooldown;
        if (triggered.shouldReadLore) {
            try {
                cooldownTime = readCooldownFromHelmet();
            } catch (Exception e) {
                e.printStackTrace(); // Keep default cooldown if lore fails
            }
        }

        this.availableTimestamp[triggered.ordinal()] = cooldownTime + net.minecraft.util.Util.getMeasuringTimeMs();
        this.lastTriggered = triggered;
        
        NotificationManager.INSTANCE.show(
            triggered.name(),
            triggered.name, 
            cooldownTime, 
            (float) xPos.getInput(), 
            (float) yPos.getInput(),
            true
        );
    }

    private long readCooldownFromHelmet() {
        ItemStack helmet = mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD);
        if (helmet.isEmpty()) { 
            throw new IllegalStateException("failed to read lore from helmet: helmet is empty");
        }
        
        net.minecraft.component.type.LoreComponent loreComponent = helmet.get(net.minecraft.component.DataComponentTypes.LORE);
        if (loreComponent == null) {
            throw new IllegalStateException("failed to read lore from helmet: lore component is not found");
        }
        
        for (net.minecraft.text.Text lineText : loreComponent.lines()) {
            String line = Utils.stripColor(lineText.getString()).trim();
            if (line.startsWith("Cooldown: ")) {
                String secondsText = line.substring("Cooldown: ".length(), line.length() - 1).trim();
                return Long.parseLong(secondsText) * 1000;
            }
        }
        
        throw new IllegalStateException("failed to read lore from helmet: cooldown is not found");
    }
}