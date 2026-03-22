package com.revampes.Fault.modules.impl.dungeon.Terminals;

import com.revampes.Fault.events.impl.RenderScreenEvent;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.utility.terminals.SlotData;
import com.revampes.Fault.utility.terminals.TerminalRenderUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.sync.ComponentChangesHash;
import net.minecraft.screen.sync.ItemStackHash;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorsTerminal extends AbstractTerminal {
    private static final long QUEUE_RECHECK_DELAY_MS = 300L;
    private static final long PENDING_CONFIRM_TIMEOUT_MS = 1400L;
    private String extraColor = null;
    private final Deque<int[]> queuedClicks = new ConcurrentLinkedDeque<>();
    private final Set<Integer> pendingClicks = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Long> pendingSince = new ConcurrentHashMap<>();
    private long queueBecameEmptyAt = 0L;
    private static final Map<String, String> COLOR_REPLACEMENTS = new ConcurrentHashMap<>();

    static {
        COLOR_REPLACEMENTS.put("light gray", "silver");
        COLOR_REPLACEMENTS.put("wool", "white");
        COLOR_REPLACEMENTS.put("bone", "white");
        COLOR_REPLACEMENTS.put("ink", "black");
        COLOR_REPLACEMENTS.put("lapis", "blue");
        COLOR_REPLACEMENTS.put("cocoa", "brown");
        COLOR_REPLACEMENTS.put("dandelion", "yellow");
        COLOR_REPLACEMENTS.put("rose", "red");
        COLOR_REPLACEMENTS.put("cactus", "green");
    }

    @Override
    public String getTerminalName() {
        return "Colors";
    }

    @Override
    public boolean matches(String windowTitle) {
        return windowTitle.matches("^Select all the ([\\w ]+) items!$");
    }

    @Override
    public void onWindowOpen(String title, int windowId, int slotCount) {
        this.windowId = windowId;
        Pattern pattern = Pattern.compile("^Select all the ([\\w ]+) items!$");
        Matcher matcher = pattern.matcher(title);

        if (matcher.find()) {
            extraColor = matcher.group(1).toLowerCase();
            inTerminal = true;
            openedAt = System.currentTimeMillis();
            slots.clear();
            solutionSlots.clear();
            queuedClicks.clear();
            pendingClicks.clear();
            pendingSince.clear();
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
            pendingSince.clear();
            resetQueueDispatchState();
            return;
        }
        
        if (isAwaitingQueuedClickAck() && !tryFallbackQueueAckRelease()) return;
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
        int[] allowedSlots = TerminalRenderUtils.getAllowedSlots("colors");

        for (SlotData slot : slots) {
            if (slot == null) continue;
            if (!slotIsAllowed(slot.slot, allowedSlots)) continue;
            if (slot.enchanted) continue;

            String itemName = formatItemName(slot.name);
            String processedName = processColorName(itemName);

            if (processedName.startsWith(extraColor)) {
                solutionSlots.add(slot.slot);
            }
        }

        // Keep clicked entries hidden locally while they are being queued/sent.
        Set<Integer> freshSolution = new HashSet<>(solutionSlots);
        if (queuedClicks.isEmpty()) {
            long now = System.currentTimeMillis();
            pendingClicks.removeIf(slot -> {
                if (!freshSolution.contains(slot)) return true;
                long markedAt = pendingSince.getOrDefault(slot, now);
                // If still unsolved server-side after timeout, unhide for retry.
                return now - markedAt >= PENDING_CONFIRM_TIMEOUT_MS;
            });
            pendingSince.keySet().retainAll(pendingClicks);
        } else {
            queueBecameEmptyAt = 0L;
            pendingClicks.retainAll(freshSolution);
        }
        solutionSlots.removeAll(pendingClicks);
    }

    private String processColorName(String name) {
        for (Map.Entry<String, String> entry : COLOR_REPLACEMENTS.entrySet()) {
            name = name.replace(entry.getKey(), entry.getValue());
        }
        return name;
    }

    @Override
    public void onSlotClick(int slotIndex, int button) {
        // Check if this exact slot is already queued
        for (int[] queued : queuedClicks) {
            if (queued[0] == slotIndex) return;  // Already queued, prevent duplicate
        }
        
        solutionSlots.remove(slotIndex);
        pendingClicks.add(slotIndex);
        pendingSince.put(slotIndex, System.currentTimeMillis());
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
        String title = "§8[§bFault Terminal§8] §aColors";
        TerminalRenderUtils.drawText(event.context, title, offsetX, offsetY, 0xFFFFFF);

        // Draw solution slots
        for (int slot : solutionSlots) {
            TerminalRenderUtils.drawSlotHighlight(event.context, slot, scale, offsetX, offsetY + titleHeight, 0xFF00FF00);
        }
    }

    @Override
    public int getPendingQueueCount() {
        return queuedClicks.size();
    }
}
