package com.revampes.Fault.modules.impl.dungeon.Terminals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.revampes.Fault.events.impl.RenderScreenEvent;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.utility.terminals.SlotData;
import com.revampes.Fault.utility.terminals.TerminalRenderUtils;

import net.minecraft.item.ItemStack;

public class NumbersTerminal extends AbstractTerminal {
    private static final int MAX_NUMBERS_SLOTS = 14;
    private static final long QUEUE_RECHECK_DELAY_MS = 300L;
    private final java.util.List<Integer> fullSolutionSlots = new java.util.ArrayList<>();
    private final List<Integer> orderedSolutionSlots = new ArrayList<>();
    private final Deque<int[]> queuedClicks = new ConcurrentLinkedDeque<>();
    private final Set<Integer> pendingClicks = ConcurrentHashMap.newKeySet();
    private volatile boolean clicked = false;
    private static final long CLICK_TIMEOUT_MS = 140L;
    private long queueBecameEmptyAt = 0L;

    @Override
    public String getTerminalName() {
        return "Numbers";
    }

    @Override
    public boolean matches(String windowTitle) {
        return windowTitle.equals("Click in order!");
    }

    @Override
    public void onWindowOpen(String title, int windowId, int slotCount) {
        if (matches(title)) {
            this.windowId = windowId;
            inTerminal = true;
            openedAt = System.currentTimeMillis();
            slots.clear();
            solutionSlots.clear();
            fullSolutionSlots.clear();
            orderedSolutionSlots.clear();
            queuedClicks.clear();
            pendingClicks.clear();
            clicked = false;
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
            clicked = false;
            processQueuedClicks();
        }
    }

    private boolean isQueueStillValid() {
        if (shouldQueueClick()) return true;
        if (queuedClicks.isEmpty()) return true;
        return !orderedSolutionSlots.isEmpty() && orderedSolutionSlots.get(0).equals(queuedClicks.peekFirst()[0]);
    }

    private boolean shouldQueueClick() {
        return ModuleManager.terminalManager != null && ModuleManager.terminalManager.isQueueClickEnabled();
    }

    private void processQueuedClicks() {
        if (clicked || queuedClicks.isEmpty()) return;
        
        boolean queueEnabled = shouldQueueClick();
        
        // Clear stuck queue if timeout exceeded
        if (queueEnabled && isQueuedClickTimeout()) {
            queuedClicks.clear();
            pendingClicks.clear();
            resetQueueDispatchState();
            return;
        }
        
        if (queueEnabled && isAwaitingQueuedClickAck() && !tryFallbackQueueAckRelease()) return;
        if (!canDispatchQueuedClick(queueEnabled)) {
            return;
        }
        if (!isQueueStillValid()) {
            queuedClicks.clear();
            return;
        }

        int[] next = queuedClicks.peekFirst();
        if (next != null && sendClickPacket(next[0], next[1])) {
            markQueuedClickDispatched(queueEnabled);
            if (queueEnabled) {
                recordQueuedClickSent();
            }
            if (ModuleManager.terminalManager != null) {
                ModuleManager.terminalManager.recordQueuedClickSend(getTerminalName());
            }
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
        fullSolutionSlots.clear();
        orderedSolutionSlots.clear();
        int[] allowedSlots = TerminalRenderUtils.getAllowedSlots("numbers");

        // Support both legacy wool and modern stained glass pane terminals.
        List<SlotData> allCandidates = new ArrayList<>();
        List<SlotData> clickCandidates = new ArrayList<>();
        
        for (SlotData slot : slots) {
            if (slot == null) continue;
            if (!slotIsAllowed(slot.slot, allowedSlots)) continue;

            String itemType = slot.itemType;
            boolean isLegacyWool = itemType.contains("wool") || itemType.contains("dye");
            boolean isPane = itemType.contains("stained_glass_pane");
            boolean isBlackPane = itemType.contains("black_stained_glass_pane");
            boolean isRedPane = itemType.contains("red_stained_glass_pane");

            if (isLegacyWool || (isPane && !isBlackPane)) {
                allCandidates.add(slot);
            }

            if ((isLegacyWool && slot.meta == 14) || isRedPane) {
                clickCandidates.add(slot);
            }
        }

        // Sort by size (stack count) like the JS does
        allCandidates.sort(Comparator.comparingInt(s -> s.size));
        clickCandidates.sort(Comparator.comparingInt(s -> s.size));

        // Store clickable items (bounded to first 14 entries)
        int clickLimit = Math.min(clickCandidates.size(), MAX_NUMBERS_SLOTS);
        for (int i = 0; i < clickLimit; i++) {
            SlotData slot = clickCandidates.get(i);
            orderedSolutionSlots.add(slot.slot);
            solutionSlots.add(slot.slot);
        }

        // Keep predicted clicked slots hidden until server catches up.
        Set<Integer> freshClickable = new HashSet<>(orderedSolutionSlots);
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
            pendingClicks.retainAll(freshClickable);
        }
        orderedSolutionSlots.removeIf(pendingClicks::contains);
        solutionSlots.removeAll(pendingClicks);
        
        // Store all sortable puzzle items for numbering display (bounded to 14)
        int fullLimit = Math.min(allCandidates.size(), MAX_NUMBERS_SLOTS);
        for (int i = 0; i < fullLimit; i++) {
            SlotData slot = allCandidates.get(i);
            fullSolutionSlots.add(slot.slot);
        }
    }

    @Override
    public void onSlotClick(int slotIndex, int button) {
        if (orderedSolutionSlots.isEmpty()) return;
        if (orderedSolutionSlots.get(0) != slotIndex) return; // Prevent wrong click

        int normalizedButton = button == 0 ? 0 : 1;

        // Move to the next expected slot immediately for responsive visuals.
        orderedSolutionSlots.remove(0);
        solutionSlots.remove(slotIndex);
        pendingClicks.add(slotIndex);

        if (shouldQueueClick()) {
            queuedClicks.addLast(new int[]{slotIndex, normalizedButton});
            queueBecameEmptyAt = 0L;
            processQueuedClicks();
            return;
        }

        if (clicked) {
            queuedClicks.addLast(new int[]{slotIndex, normalizedButton});
        } else {
            sendClickPacket(slotIndex, normalizedButton);
        }
    }
    
    private boolean sendClickPacket(int slot, int button) {
        clicked = sendWindowClickNoPickup(slot, button);
        if (!clicked) return false;

        if (shouldQueueClick()) {
            clicked = false;
            return true;
        }

        int initialWindowId = windowId;
        new Thread(() -> {
            try {
                Thread.sleep(CLICK_TIMEOUT_MS);
                if (!inTerminal || windowId != initialWindowId) return;
                // Release in-flight lock if server update is delayed and continue queue.
                clicked = false;
                processQueuedClicks();
            } catch (InterruptedException ignored) {
            }
        }).start();
        return true;
    }

    @Override
    public void render(RenderScreenEvent event) {
        if (!inTerminal || windowId == -1) return;

        int screenWidth = event.context.getScaledWindowWidth();
        int screenHeight = event.context.getScaledWindowHeight();
        float scale = ModuleManager.terminalManager != null ? ModuleManager.terminalManager.getOverlayScale() : 1.0f;

        int width = (int)(9 * 18 * scale);
        int titleHeight = (int)Math.round(18 * scale);
        int gridRows = 5;
        int gridHeight = (int)(gridRows * 18 * scale);
        int height = titleHeight + gridHeight;

        int offsetX = screenWidth / 2 - width / 2 + (int) Math.round((ModuleManager.terminalManager != null ? ModuleManager.terminalManager.getOverlayOffsetX() : 0) * scale);
        int offsetY = screenHeight / 2 - height / 2 + (int) Math.round((ModuleManager.terminalManager != null ? ModuleManager.terminalManager.getOverlayOffsetY() : 0) * scale);

        // Draw background
        event.context.fill(offsetX - 2, offsetY - 2, offsetX + width + 4, offsetY + height + 4, 0xFF000000);

        // Draw title
        String title = "§8[§bFault Terminal§8] §aNumbers";
        TerminalRenderUtils.drawText(event.context, title, offsetX, offsetY, 0xFFFFFFFF);

        // Snapshot mutable lists to avoid render-time races with async slot updates.
        List<Integer> fullSnapshot = new ArrayList<>(fullSolutionSlots);
        List<Integer> orderedSnapshot = new ArrayList<>(orderedSolutionSlots);

        // Draw numbers on valid slots (at most 14)
        int renderLimit = Math.min(fullSnapshot.size(), MAX_NUMBERS_SLOTS);
        for (int i = 0; i < renderLimit; i++) {
            int slot = fullSnapshot.get(i);
            int solutionIndex = orderedSnapshot.indexOf(slot);
            // Highlight nearest 3 clickable slots with decreasing opacity.
            if (solutionIndex >= 0 && solutionIndex < 3 && solutionIndex < MAX_NUMBERS_SLOTS) {
                int color = solutionIndex == 0 ? 0xCC00FF00 : (solutionIndex == 1 ? 0x9900FF00 : 0x6600FF00);
                TerminalRenderUtils.drawSlotHighlight(event.context, slot, scale, offsetX, offsetY + titleHeight, color);
            }
            
            // Draw number on slot
            int slotX = offsetX + (int)((slot % 9) * 18 * scale);
            int slotY = offsetY + titleHeight + (int)((slot / 9) * 18 * scale);
            int centerX = slotX + (int)(8 * scale);
            int textY = slotY + (int)Math.round(4 * scale);
            TerminalRenderUtils.drawCenteredText(event.context, String.valueOf(i + 1), centerX, textY, 0xFFFFFF);
        }
    }

    @Override
    public int getPendingQueueCount() {
        return queuedClicks.size();
    }
}
