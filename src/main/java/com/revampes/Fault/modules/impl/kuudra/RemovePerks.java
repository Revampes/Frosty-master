package com.revampes.Fault.modules.impl.kuudra;

import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.events.impl.SendPacketEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import java.util.HashSet;
import java.util.Set;

public class RemovePerks extends Module {

    private final ButtonSetting renderStun = new ButtonSetting("Show Stun", false);
    private final Set<Integer> hiddenSlots = new HashSet<>();

    public RemovePerks() {
        super("Remove Perks", category.Kuudra);
        this.registerSetting(renderStun);
    }

    @Override
    public String getDesc() {
        return "Removes certain perks from the perk menu.";
    }

    @EventHandler
    private void onUpdate(PreUpdateEvent event) {
        if (mc.currentScreen instanceof HandledScreen<?> handledScreen) {
            String title = handledScreen.getTitle().getString();
            if ("Perk Menu".equals(title)) {
                ScreenHandler handler = handledScreen.getScreenHandler();
                for (int i = 0; i < handler.slots.size(); i++) {
                    Slot slot = handler.getSlot(i);
                    ItemStack item = slot.getStack();
                    if (item != null && !item.isEmpty()) {
                        String name = Utils.stripFormatting(item.getName().getString());
                        if (slotCheck(name)) {
                            hiddenSlots.add(i);
                            slot.setStack(ItemStack.EMPTY);
                        }
                    }
                }
            } else {
                hiddenSlots.clear();
            }
        } else {
            hiddenSlots.clear();
        }
    }

    @EventHandler
    private void onSendPacket(SendPacketEvent event) {
        if (event.getPacket() instanceof ClickSlotC2SPacket packet) {
            if (mc.currentScreen instanceof HandledScreen<?> handledScreen) {
                String title = handledScreen.getTitle().getString();
                if ("Perk Menu".equals(title)) {
                    int slotId = packet.slot();
                    if (hiddenSlots.contains(slotId)) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    private boolean slotCheck(String slotName) {
        boolean contains1 = slotName.contains("Steady Hands") || slotName.contains("Bomberman") || slotName.contains("Mining Frenzy");
        boolean equals1 = slotName.equals("Elle's Lava Rod") || slotName.equals("Elle's Pickaxe") || slotName.equals("Auto Revive");
        boolean stunCheck = !renderStun.isToggled() && slotName.contains("Human Cannonball");

        return contains1 || equals1 || stunCheck;
    }
}
