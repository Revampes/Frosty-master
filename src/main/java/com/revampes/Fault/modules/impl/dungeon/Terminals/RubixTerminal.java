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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class RubixTerminal extends AbstractTerminal {
    private static final long QUEUE_RECHECK_DELAY_MS = 300L;
    private final Map<Integer, Integer> slotClicks = new ConcurrentHashMap<>();
    private final java.util.Map<Integer, Integer> solutionValues = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> pendingPredictedValues = new ConcurrentHashMap<>();
    private final Deque<int[]> queuedClicks = new ConcurrentLinkedDeque<>();
    private volatile boolean clicked = false;
    private static final long CLICK_TIMEOUT_MS = 140L;
    private long queueBecameEmptyAt = 0L;
    
    private static final int[] RUBIX_ORDER = new int[]{14, 1, 4, 13, 11};

    @Override
    public String getTerminalName() {
        return "Rubix";
    }

    @Override
    public boolean matches(String windowTitle) {
        return windowTitle.equals("Change all to same color!");
    }

    @Override
    public void onWindowOpen(String title, int windowId, int slotCount) {
        if (matches(title)) {
            this.windowId = windowId;
            inTerminal = true;
            openedAt = System.currentTimeMillis();
            slots.clear();
            solutionSlots.clear();
            slotClicks.clear();
            solutionValues.clear();
            pendingPredictedValues.clear();
            queuedClicks.clear();
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

    private boolean isValidClick(int slot, int button) {
        Integer value = solutionValues.get(slot);
        if (value == null) return false;
        return (value > 0 && button == 0) || (value < 0 && button == 1);
    }

    private void applyLocalPrediction(int slot, int button) {
        Integer value = solutionValues.get(slot);
        if (value == null) return;

        int next = button == 0 ? value - 1 : value + 1;
        pendingPredictedValues.put(slot, next);
        if (next == 0) {
            solutionValues.remove(slot);
            solutionSlots.remove(slot);
            slotClicks.remove(slot);
        } else {
            solutionValues.put(slot, next);
        }
    }

    private void processQueuedClicks() {
        if (clicked || queuedClicks.isEmpty()) return;
        boolean queueEnabled = shouldQueueClick();
        
        // Clear stuck queue if timeout exceeded
        if (queueEnabled && isQueuedClickTimeout()) {
            queuedClicks.clear();
            resetQueueDispatchState();
            return;
        }
        
        if (queueEnabled && isAwaitingQueuedClickAck()) {
            return;
        }
        if (!canDispatchQueuedClick(queueEnabled)) {
            return;
        }

        if (queueEnabled) {
            int[] next = queuedClicks.peekFirst();
            if (next != null && sendClickPacket(next[0], next[1])) {
                markQueuedClickDispatched(true);
                recordQueuedClickSent();
                if (ModuleManager.terminalManager != null) {
                    ModuleManager.terminalManager.recordQueuedClickSend(getTerminalName());
                }
            }
            return;
        }

        while (!queuedClicks.isEmpty()) {
            int[] next = queuedClicks.pollFirst();
            if (isValidClick(next[0], next[1])) {
                sendClickPacket(next[0], next[1]);
                return;
            }
        }
    }

    @Override
    protected void onQueuedClickAcknowledged() {
        queuedClicks.pollFirst();
        processQueuedClicks();
    }

    private boolean shouldQueueClick() {
        return ModuleManager.terminalManager != null && ModuleManager.terminalManager.isQueueClickEnabled();
    }

    private int calcIndex(int index) {
        return (index + RUBIX_ORDER.length) % RUBIX_ORDER.length;
    }

    private int getRubixMeta(SlotData slot) {
        if (slot == null) return -1;

        // Modern versions expose pane color in item id instead of durability metadata.
        String type = slot.itemType == null ? "" : slot.itemType;
        if (type.contains("red_stained_glass_pane")) return 14;
        if (type.contains("orange_stained_glass_pane")) return 1;
        if (type.contains("yellow_stained_glass_pane")) return 4;
        if (type.contains("green_stained_glass_pane")) return 13;
        if (type.contains("blue_stained_glass_pane")) return 11;

        // Legacy fallback for older metadata-based items.
        if (slot.meta == 14 || slot.meta == 1 || slot.meta == 4 || slot.meta == 13 || slot.meta == 11) {
            return slot.meta;
        }
        return -1;
    }

    @Override
    public void solve() {
        Map<Integer, Integer> freshValues = new HashMap<>();
        Set<Integer> freshSlots = new HashSet<>();

        int[] allowedSlots = TerminalRenderUtils.getAllowedSlots("rubix");

        int[] clicks = new int[]{0, 0, 0, 0, 0};
        for (int i = 0; i < RUBIX_ORDER.length; i++) {
            int targetMeta = RUBIX_ORDER[calcIndex(i)];
            for (SlotData slot : slots) {
                if (slot == null || !slotIsAllowed(slot.slot, allowedSlots)) continue;
                int slotMeta = getRubixMeta(slot);
                if (slotMeta == -1) continue;
                if (slotMeta == targetMeta) continue;

                if (slotMeta == RUBIX_ORDER[calcIndex(i - 2)]) clicks[i] += 2;
                else if (slotMeta == RUBIX_ORDER[calcIndex(i - 1)]) clicks[i] += 1;
                else if (slotMeta == RUBIX_ORDER[calcIndex(i + 1)]) clicks[i] += 1;
                else if (slotMeta == RUBIX_ORDER[calcIndex(i + 2)]) clicks[i] += 2;
            }
        }

        int origin = 0;
        for (int i = 1; i < clicks.length; i++) {
            if (clicks[i] < clicks[origin]) {
                origin = i;
            }
        }

        int originMeta = RUBIX_ORDER[calcIndex(origin)];
        for (SlotData slot : slots) {
            if (slot == null || !slotIsAllowed(slot.slot, allowedSlots)) continue;
            int slotMeta = getRubixMeta(slot);
            if (slotMeta == -1 || slotMeta == originMeta) continue;

            int value = 0;
            if (slotMeta == RUBIX_ORDER[calcIndex(origin - 2)]) value = 2;
            else if (slotMeta == RUBIX_ORDER[calcIndex(origin - 1)]) value = 1;
            else if (slotMeta == RUBIX_ORDER[calcIndex(origin + 1)]) value = -1;
            else if (slotMeta == RUBIX_ORDER[calcIndex(origin + 2)]) value = -2;

            if (value != 0) {
                freshSlots.add(slot.slot);
                freshValues.put(slot.slot, value);
            }
        }

        // Remove pending markers once the server reflects the predicted value.
        if (queuedClicks.isEmpty()) {
            long now = System.currentTimeMillis();
            if (queueBecameEmptyAt == 0L) {
                queueBecameEmptyAt = now;
            }
            if (!shouldQueueClick() || now - queueBecameEmptyAt >= QUEUE_RECHECK_DELAY_MS) {
                pendingPredictedValues.clear();
            }
        } else {
            queueBecameEmptyAt = 0L;
        }

        Iterator<Map.Entry<Integer, Integer>> it = pendingPredictedValues.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
            int slot = entry.getKey();
            int predicted = entry.getValue();
            int serverValue = freshValues.getOrDefault(slot, 0);
            if (serverValue == predicted) {
                it.remove();
            }
        }

        // Keep pending predicted state visible to avoid flash while server is delayed.
        for (Map.Entry<Integer, Integer> entry : pendingPredictedValues.entrySet()) {
            int slot = entry.getKey();
            int predicted = entry.getValue();
            if (predicted == 0) {
                freshValues.remove(slot);
                freshSlots.remove(slot);
            } else {
                freshValues.put(slot, predicted);
                freshSlots.add(slot);
            }
        }

        slotClicks.clear();
        solutionSlots.clear();
        solutionValues.clear();
        solutionSlots.addAll(freshSlots);
        solutionValues.putAll(freshValues);
        for (int slot : solutionSlots) {
            slotClicks.put(slot, 1);
        }
    }

    @Override
    public void onSlotClick(int slotIndex, int button) {
        int normalizedButton = button == 0 ? 0 : 1;
        if (!isValidClick(slotIndex, normalizedButton)) return;
        // Allow click if it's valid (other terminals deny rapid duplicate clicks, but Rubix has its own validation)

        applyLocalPrediction(slotIndex, normalizedButton);

        if (shouldQueueClick()) {
            queuedClicks.addLast(new int[]{slotIndex, normalizedButton});
            queueBecameEmptyAt = 0L;
            processQueuedClicks();
            slotClicks.merge(slotIndex, 1, Integer::sum);
            return;
        }

        if (clicked) {
            queuedClicks.addLast(new int[]{slotIndex, normalizedButton});
        } else {
            sendClickPacket(slotIndex, normalizedButton);
        }
        slotClicks.merge(slotIndex, 1, Integer::sum);
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
        String title = "§8[§bFault Terminal§8] §aRubix";
        TerminalRenderUtils.drawText(event.context, title, offsetX, offsetY, 0xFFFFFFFF);

        // Draw solution slots with click direction indicators
        for (int slot : solutionSlots) {
            Integer value = solutionValues.get(slot);
            if (value == null) continue;
            int clickValue = value;
            int color = clickValue > 0 ? 0xFF00AA00 : 0xFFAA0000; // Green for left, red for right
            
            // Draw the highlight box for this slot
            TerminalRenderUtils.drawSlotHighlight(event.context, slot, scale, offsetX, offsetY + titleHeight, color);
            
            // Draw the click count text centered in the slot
            int slotCol = slot % 9;
            int slotRow = slot / 9;
            int slotX = offsetX + (int)(slotCol * 18 * scale);
            int slotY = offsetY + titleHeight + (int)(slotRow * 18 * scale);
            
            // Keep sign like the JS reference so direction is explicit.
            String valueStr = String.valueOf(clickValue);
            
            int textWidth = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(valueStr);
            int textX = slotX + ((int)(16 * scale) - textWidth) / 2;
            int textY = slotY + (int)Math.round(4 * scale);
            
            TerminalRenderUtils.drawText(event.context, valueStr, textX, textY, 0xFFFFFFFF);
        }
    }

    @Override
    public int getPendingQueueCount() {
        return queuedClicks.size();
    }
}
