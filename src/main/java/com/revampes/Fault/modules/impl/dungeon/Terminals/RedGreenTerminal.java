package com.revampes.Fault.modules.impl.dungeon.Terminals;

import com.revampes.Fault.events.impl.RenderScreenEvent;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.utility.terminals.SlotData;
import com.revampes.Fault.utility.terminals.TerminalRenderUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.sync.ComponentChangesHash;
import net.minecraft.screen.sync.ItemStackHash;

import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class RedGreenTerminal extends AbstractTerminal {
    private static final long QUEUE_RECHECK_DELAY_MS = 300L;
    private final Deque<int[]> queuedClicks = new ConcurrentLinkedDeque<>();
    private final Set<Integer> pendingClicks = ConcurrentHashMap.newKeySet();
    private long queueBecameEmptyAt = 0L;

    @Override
    public String getTerminalName() {
        return "Red Green";
    }

    @Override
    public boolean matches(String windowTitle) {
        return windowTitle.equals("Correct all the panes!");
    }

    @Override
    public void onWindowOpen(String title, int windowId, int slotCount) {
        if (matches(title)) {
            this.windowId = windowId;
            inTerminal = true;
            openedAt = System.currentTimeMillis();
            slots.clear();
            solutionSlots.clear();
            queuedClicks.clear();
            pendingClicks.clear();
            queueBecameEmptyAt = 0L;
            resetQueueDispatchState();
            windowSize = slotCount;
        }
    }

    @Override
    public void onSlotUpdate(int slotIndex, ItemStack itemStack) {
        if (slotIndex < 0 || slotIndex >= windowSize) return;

        SlotData data = new SlotData(slotIndex, itemStack, itemStack.isEmpty() ? "" : itemStack.getName().getString());
        
        if (slotIndex < slots.size()) {
            slots.set(slotIndex, data);
        } else {
            while (slots.size() <= slotIndex) {
                slots.add(null);
            }
            slots.set(slotIndex, data);
        }

        if (slots.size() >= windowSize) {
            solve();
            processQueuedClicks();
        }
    }

    private boolean shouldQueueClick() {
        return ModuleManager.terminalManager != null && ModuleManager.terminalManager.isQueueClickEnabled();
    }

    private void processQueuedClicks() {
        if (!shouldQueueClick() || queuedClicks.isEmpty()) return;
        
        // Clear stuck queue if timeout exceeded
        if (isQueuedClickTimeout()) {
            queuedClicks.clear();
            pendingClicks.clear();
            resetQueueDispatchState();
            return;
        }
        
        if (isAwaitingQueuedClickAck()) return;
        if (!canDispatchQueuedClick(true)) return;

        int[] next = queuedClicks.peekFirst();
        if (next == null) return;
        if (!sendClickPacket(next[0], next[1])) return;
        markQueuedClickDispatched(true);
        recordQueuedClickSent();
        if (ModuleManager.terminalManager != null) {
            ModuleManager.terminalManager.recordQueuedClickSend(getTerminalName());
        }
    }

    @Override
    protected void onQueuedClickAcknowledged() {
        queuedClicks.pollFirst();
        processQueuedClicks();
    }

    @Override
    public void solve() {
        solutionSlots.clear();
        int[] allowedSlots = TerminalRenderUtils.getAllowedSlots("redgreen");

        // Find red panes (item type contains "red" and "pane")
        for (SlotData slot : slots) {
            if (slot == null) {
                continue;
            }
            if (!slotIsAllowed(slot.slot, allowedSlots)) {
                continue;
            }

            // Check if it's a red stained glass pane (modern MC uses separate items for each color)
            if (slot.itemType.toLowerCase().contains("red") && slot.itemType.toLowerCase().contains("pane")) {
                solutionSlots.add(slot.slot);
            }
        }

        Set<Integer> freshSolution = new HashSet<>(solutionSlots);
        if (queuedClicks.isEmpty()) {
            long now = System.currentTimeMillis();
            if (queueBecameEmptyAt == 0L) {
                queueBecameEmptyAt = now;
            }
            if (!shouldQueueClick() || now - queueBecameEmptyAt >= QUEUE_RECHECK_DELAY_MS) {
                pendingClicks.clear();
            }
        } else {
            queueBecameEmptyAt = 0L;
            pendingClicks.retainAll(freshSolution);
        }
        solutionSlots.removeAll(pendingClicks);
    }

    @Override
    public void onSlotClick(int slotIndex, int button) {
        // Check if this exact slot is already queued
        for (int[] queued : queuedClicks) {
            if (queued[0] == slotIndex) return;  // Already queued, prevent duplicate
        }
        
        solutionSlots.remove(slotIndex);
        pendingClicks.add(slotIndex);
        int normalizedButton = button == 0 ? 0 : 1;

        if (shouldQueueClick()) {
            queuedClicks.addLast(new int[]{slotIndex, normalizedButton});
            queueBecameEmptyAt = 0L;
            processQueuedClicks();
        } else {
            sendClickPacket(slotIndex, normalizedButton);
        }
    }
    
    private boolean sendClickPacket(int slot, int button) {
        return sendWindowClickNoPickup(slot, button);
    }

    @Override
    public void render(RenderScreenEvent event) {
        if (!inTerminal || windowId == -1) return;

        int screenWidth = event.context.getScaledWindowWidth();
        int screenHeight = event.context.getScaledWindowHeight();
        float scale = ModuleManager.terminalManager != null ? ModuleManager.terminalManager.getOverlayScale() : 1.0f;
        int titleHeight = (int)Math.round(18 * scale);

        int width = (int)(9 * 18 * scale);
        int height = (int)(windowSize / 9 * 18 * scale);

        int offsetX = screenWidth / 2 - width / 2 + (int) Math.round((ModuleManager.terminalManager != null ? ModuleManager.terminalManager.getOverlayOffsetX() : 0) * scale);
        int offsetY = screenHeight / 2 - height / 2 + (int) Math.round((ModuleManager.terminalManager != null ? ModuleManager.terminalManager.getOverlayOffsetY() : 0) * scale);

        // Draw background
        event.context.fill(offsetX - 2, offsetY - 2, offsetX + width + 4, offsetY + height + 4, 0xFF000000);

        // Draw title
        String title = "§8[§bFault Terminal§8] §aRed Green";
        TerminalRenderUtils.drawText(event.context, title, offsetX, offsetY, 0xFFFFFF);

        // Draw solution slots
        for (int slot : solutionSlots) {
            TerminalRenderUtils.drawSlotHighlight(event.context, slot, scale, offsetX, offsetY + titleHeight, 0xFFFF0000);
        }
    }

    @Override
    public int getPendingQueueCount() {
        return queuedClicks.size();
    }
}
