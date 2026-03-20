package com.revampes.Fault.modules.impl.dungeon.Terminals;

import com.revampes.Fault.events.impl.RenderScreenEvent;
import com.revampes.Fault.events.impl.SlotClickEvent;
import com.revampes.Fault.utility.terminals.SlotData;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class AbstractTerminal {
    protected boolean inTerminal = false;
    protected int windowId = -1;
    protected int windowSize = 0;
    protected final List<SlotData> slots = new CopyOnWriteArrayList<>();
    protected final Set<Integer> solutionSlots = ConcurrentHashMap.newKeySet();
    protected long openedAt = 0;

    public abstract String getTerminalName();

    public abstract boolean matches(String windowTitle);

    public abstract void onWindowOpen(String title, int windowId, int slotCount);

    public abstract void onSlotUpdate(int slotIndex, ItemStack itemStack);

    public abstract void solve();

    public abstract void onSlotClick(int slotIndex, int button);

    public abstract void render(RenderScreenEvent event);

    public void onWindowClose() {
        inTerminal = false;
        slots.clear();
        solutionSlots.clear();
        windowId = -1;
        windowSize = 0;
    }

    public boolean isInTerminal() {
        return inTerminal;
    }

    public int getWindowId() {
        return windowId;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(int size) {
        this.windowSize = size;
    }

    public List<SlotData> getSlots() {
        return slots;
    }

    public Set<Integer> getSolution() {
        return solutionSlots;
    }

    protected int getItemDamage(ItemStack stack) {
        if (stack.isEmpty()) return -1;
        return stack.getDamage();
    }

    protected String formatItemName(String name) {
        // Remove formatting codes (§c, §a, etc.)
        return name.replaceAll("\u00A7[0-9a-fk-or]", "").toLowerCase();
    }

    protected boolean slotIsAllowed(int slot, int[] allowedSlots) {
        for (int allowed : allowedSlots) {
            if (slot == allowed) return true;
        }
        return false;
    }
}
