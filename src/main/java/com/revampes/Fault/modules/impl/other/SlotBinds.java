package com.revampes.Fault.modules.impl.other;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.revampes.Fault.events.impl.KeyEvent;
import com.revampes.Fault.events.impl.RenderScreenEvent;
import com.revampes.Fault.events.impl.SlotClickEvent;
import com.revampes.Fault.mixin.HandledScreenAccessor;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ColorSetting;
import com.revampes.Fault.settings.impl.MapSetting;
import com.revampes.Fault.settings.impl.SelectSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.KeyAction;
import com.revampes.Fault.utility.Utils;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class SlotBinds extends Module {
    // We will hardcode to GLFW_KEY_V for setting binds for now.
    public int bindSetKey = GLFW.GLFW_KEY_V;
    
    public ColorSetting lineColor = new ColorSetting("Line Color", new Color(255, 170, 0)); // MINECRAFT_GOLD
    public SliderSetting lineWidth = new SliderSetting("Line Width", 3, 1, 8, 1);
    public SelectSetting currentProfile = new SelectSetting("Profile", 0, new String[]{"Profile 1", "Profile 2", "Profile 3", "Profile 4", "Profile 5", "Profile 6"});
    
    public MapSetting profileDataSetting = new MapSetting("ProfileData", new HashMap<>());

    private Integer previousSlot = null;

    public SlotBinds() {
        super("Slot Binds", "Bind slots together for quick access.", category.Other);
        this.registerSetting(lineColor);
        this.registerSetting(lineWidth);
        this.registerSetting(currentProfile);
        this.registerSetting(profileDataSetting);
    }

    private Map<Integer, Integer> getSlotBinds() {
        Map<String, Map<Integer, Integer>> data = profileDataSetting.getValue();
        if (data == null) {
            data = new HashMap<>();
            profileDataSetting.setValue(data);
        }
        return data.computeIfAbsent(currentProfile.getOption(), k -> new HashMap<>());
    }

    private boolean isShiftDown() {
        return InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private Slot getHoveredSlot(HandledScreen<?> screen, double mouseX, double mouseY) {
        int screenX = ((HandledScreenAccessor) screen).getX();
        int screenY = ((HandledScreenAccessor) screen).getY();

        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot == null) continue;

            int slotX = screenX + slot.x;
            int slotY = screenY + slot.y;
            if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                return slot;
            }
        }

        return ((HandledScreenAccessor) screen).getFocusedSlot();
    }

    private float getMouseXScaled() {
        return (float) (mc.mouse.getX() * mc.getWindow().getScaledWidth() / mc.getWindow().getWidth());
    }

    private float getMouseYScaled() {
        return (float) (mc.mouse.getY() * mc.getWindow().getScaledHeight() / mc.getWindow().getHeight());
    }

    private void renderBindLine(net.minecraft.client.gui.DrawContext context, HandledScreen<?> screen, float mouseX, float mouseY) {
        int startSlotId = -1;
        int endSlotId = -1;
        float endX = -1, endY = -1;

        int screenX = ((HandledScreenAccessor) screen).getX();
        int screenY = ((HandledScreenAccessor) screen).getY();

        Slot hoveredSlot = getHoveredSlot(screen, mouseX, mouseY);
        Map<Integer, Integer> binds = getSlotBinds();

        if (previousSlot != null) {
            startSlotId = previousSlot;
            endX = mouseX;
            endY = mouseY;
        } else {
            if (!isShiftDown()) {
                return;
            }
            if (hoveredSlot == null || hoveredSlot.id < 5 || hoveredSlot.id >= 45) return;

            Integer boundSlot = binds.get(hoveredSlot.id);
            if (boundSlot == null) {
                for (Map.Entry<Integer, Integer> entry : binds.entrySet()) {
                    if (entry.getValue() == hoveredSlot.id) {
                        boundSlot = entry.getKey();
                        break;
                    }
                }
            }

            if (boundSlot != null) {
                startSlotId = hoveredSlot.id;
                endSlotId = boundSlot;
            } else {
                return;
            }
        }

        Slot startSlot;
        try {
            startSlot = screen.getScreenHandler().getSlot(startSlotId);
        } catch (Exception e) {
            return;
        }

        if (startSlot == null) return;

        float startX = startSlot.x + screenX + 8;
        float startY = startSlot.y + screenY + 8;

        if (endSlotId != -1) {
            try {
                Slot endSlotObj = screen.getScreenHandler().getSlot(endSlotId);
                endX = endSlotObj.x + screenX + 8;
                endY = endSlotObj.y + screenY + 8;
            } catch (Exception e) {
                return;
            }
        }

        Color c = lineColor.getColor();
        int thickness = Math.max(1, (int) Math.round(lineWidth.getInput()));

        // Push to a fresh GUI layer so later screen passes don't overwrite the line.
        context.createNewRootLayer();
        com.revampes.Fault.utility.RenderUtils.draw2DLine(context, startX, startY, endX, endY, c.getRGB(), thickness);
    }

    @EventHandler
    public void onSlotClick(SlotClickEvent event) {
        if (!isShiftDown() || !(mc.currentScreen instanceof HandledScreen)) {
            return;
        }
        
        HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;
        
        // Only handle clicks in the player inventory
        if (!(screen.getScreenHandler() instanceof PlayerScreenHandler)) {
            return;
        }
        
        int clickedSlot = event.slotId;
        
        Map<Integer, Integer> binds = getSlotBinds();
        
        Integer boundSlot = binds.get(clickedSlot);
        if (boundSlot == null) {
            for (Map.Entry<Integer, Integer> entry : binds.entrySet()) {
                if (entry.getValue() == clickedSlot) {
                    boundSlot = entry.getKey();
                    break;
                }
            }
        }
        
        if (boundSlot == null) {
            return;
        }
        
        int from, to;
        if (clickedSlot >= 36 && clickedSlot <= 44) {
            from = boundSlot;
            to = clickedSlot;
        } else if (boundSlot >= 36 && boundSlot <= 44) {
            from = clickedSlot;
            to = boundSlot;
        } else {
            return;
        }
        
        if (mc.interactionManager != null && mc.player != null) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, from, to % 36, SlotActionType.SWAP, mc.player);
        }
        event.cancel();
    }

    @EventHandler
    public void onKeyPress(KeyEvent event) {
        if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen) || event.key != bindSetKey || event.action != KeyAction.Press) return;
        HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;
        
        Slot hoveredSlot = getHoveredSlot(screen, getMouseXScaled(), getMouseYScaled());
        
        if (hoveredSlot == null || hoveredSlot.id < 5 || hoveredSlot.id >= 45) return;
        
        int clickedSlot = hoveredSlot.id;
        event.cancel();
        
        Map<Integer, Integer> binds = getSlotBinds();
        if (previousSlot != null) {
            if (previousSlot == clickedSlot) {
                Utils.addChatMessage("§cYou can't bind a slot to itself.");
                return;
            }
            if ((previousSlot < 36 || previousSlot > 44) && (clickedSlot < 36 || clickedSlot > 44)) {
                Utils.addChatMessage("§cOne of the slots must be in the hotbar (36-44).");
                return;
            }
            Utils.addChatMessage("§aAdded bind from slot §b" + previousSlot + " §ato §d" + clickedSlot + " §7(" + currentProfile.getOption() + ").");
            binds.put(previousSlot, clickedSlot);
            profileDataSetting.setValue(profileDataSetting.getValue()); // trigger save update if needed
            previousSlot = null;
        } else {
            for (Map.Entry<Integer, Integer> entry : binds.entrySet()) {
                if (entry.getKey() == clickedSlot) {
                    binds.remove(entry.getKey());
                    Utils.addChatMessage("§cRemoved bind from slot §b" + entry.getKey() + " §cto §d" + entry.getValue() + " §7(" + currentProfile.getOption() + ").");
                    return;
                }
            }
            previousSlot = clickedSlot;
        }
    }

    @EventHandler
    public void onRenderScreen(RenderScreenEvent event) {
        if (!(mc.currentScreen instanceof HandledScreen)) {
            return;
        }
        
        HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;
        renderBindLine(event.context, screen, event.mouseX, event.mouseY);
    }

    @Override
    public void onDisable() {
        previousSlot = null;
        super.onDisable();
    }
}
