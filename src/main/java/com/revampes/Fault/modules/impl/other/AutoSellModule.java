package com.revampes.Fault.modules.impl.other;

import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.SelectSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.events.impl.PreUpdateEvent;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;

public class AutoSellModule extends Module {
    
    private final SliderSetting interval = new SliderSetting("Click Interval (ticks)", 5, 0, 40, 1);
    private final SelectSetting clickType = new SelectSetting("Click Type", 0, new String[]{"Left", "Right", "Middle"});
    private final ButtonSetting highlightSellItem = new ButtonSetting("Highlight Sell item", true);

    private final List<String> defaultItems = Arrays.asList(
            "enchanted ice", "superboom tnt", "rotten", "skeleton master", "skeleton grunt", "cutlass",
            "skeleton lord", "skeleton soldier", "zombie soldier", "zombie knight", "zombie commander", "zombie lord",
            "skeletor", "super heavy", "heavy", "sniper helmet", "dreadlord", "earth shard", "zombie commander whip",
            "machine gun", "sniper bow", "soulstealer bow", "silent death", "training weight",
            "beating heart", "premium flesh", "mimic fragment", "enchanted rotten flesh", "sign",
            "enchanted bone", "defuse kit", "optical lens", "tripwire hook", "button", "carpet", "lever", "diamond atom",
            "healing viii splash potion", "healing 8 splash potion", "candycomb", "rune"
    );

    private int tickCounter = 0;

    public AutoSellModule() {
        super("AutoSell", category.Other);
        this.registerSetting(interval);
        this.registerSetting(clickType);
        this.registerSetting(highlightSellItem);
        
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof HandledScreen<?> handledScreen) {
                ScreenEvents.afterRender(screen).register((screen1, drawContext, mouseX, mouseY, tickDelta) -> {
                    if (this.isEnabled() && highlightSellItem.isToggled()) {
                        renderHighlights(drawContext, handledScreen);
                    }
                });
            }
        });
    }

    @EventHandler
    public void onPreUpdate(PreUpdateEvent event) {
        if (!isValidContainer()) {
            tickCounter = 0;
            return;
        }

        tickCounter++;
        if (tickCounter >= interval.getInput()) {
            tickCounter = 0;
            processNextItem();
        }
    }

    private boolean isValidContainer() {
        if (mc.currentScreen instanceof HandledScreen<?> screen) {
            String title = screen.getTitle().getString().toLowerCase();
            return title.contains("trades") || title.contains("booster cookie") ||
                   title.contains("farm merchant") || title.contains("ophelia");
        }
        return false;
    }

    private void processNextItem() {
        if (mc.currentScreen instanceof HandledScreen<?> screen && mc.player != null) {
            boolean clicked = false;
            for (Slot slot : screen.getScreenHandler().slots) {
                if (slot.inventory == mc.player.getInventory() && slot.hasStack()) {
                    ItemStack stack = slot.getStack();
                    String itemName = stack.getName().getString().toLowerCase();
                    itemName = itemName.replaceAll("\\xA7[0-9a-fk-or]", ""); 

                    for (String match : defaultItems) {
                        if (itemName.contains(match)) {
                            int button = switch (clickType.getOption()) {
                                case "Left" -> 0;
                                case "Right" -> 1;
                                case "Middle" -> 2;
                                default -> 0;
                            };
                            
                            SlotActionType action = (button == 2) ? SlotActionType.CLONE : SlotActionType.PICKUP;
                            if (mc.interactionManager != null) {
                                mc.interactionManager.clickSlot(
                                        screen.getScreenHandler().syncId,
                                        slot.id,
                                        button,
                                        action,
                                        mc.player
                                );
                            }
                            clicked = true;
                            break;
                        }
                    }
                }
                if (clicked) break; 
            }
        }
    }

    private void renderHighlights(DrawContext context, HandledScreen<?> screen) {
        if (!isValidContainer() || mc.player == null) return;

        int x = 0;
        int y = 0;
        
        try {
            java.lang.reflect.Field xField = null;
            java.lang.reflect.Field yField = null;
            
            for (java.lang.reflect.Field f : HandledScreen.class.getDeclaredFields()) {
                if (f.getType() == int.class) {
                    if (f.getName().equals("x") || f.getName().equals("field_2776")) {
                        xField = f;
                    } else if (f.getName().equals("y") || f.getName().equals("field_2800")) {
                        yField = f;
                    }
                }
            }
            if (xField != null && yField != null) {
                xField.setAccessible(true);
                yField.setAccessible(true);
                x = xField.getInt(screen);
                y = yField.getInt(screen);
            }
        } catch (Exception e) {}

        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.inventory == mc.player.getInventory() && slot.hasStack()) {
                ItemStack stack = slot.getStack();
                String itemName = stack.getName().getString().toLowerCase();
                itemName = itemName.replaceAll("\\xA7[0-9a-fk-or]", "");

                for (String match : defaultItems) {
                    if (itemName.contains(match)) {
                        int slotX = x + slot.x;
                        int slotY = y + slot.y;
                        
                        context.fill(slotX, slotY, slotX + 16, slotY + 16, new Color(255, 0, 0, 100).getRGB());
                        break;
                    }
                }
            }
        }
    }
}
