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
import java.util.LinkedList;
import java.util.Set;

public class RedGreenTerminal extends AbstractTerminal {
    private static final long QUEUE_RECHECK_DELAY_MS = 300L;
    private final Deque<int[]> queuedClicks = new LinkedList<>();
    private final Set<Integer> pendingClicks = new HashSet<>();
    private long lastQueuedSendAt = 0L;
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
            lastQueuedSendAt = 0L;
            queueBecameEmptyAt = 0L;
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

    private long getQueueClickIntervalMs() {
        return ModuleManager.terminalManager != null ? ModuleManager.terminalManager.getQueueClickIntervalMs() : 200L;
    }

    private boolean canSendNextQueuedClick() {
        return System.currentTimeMillis() - lastQueuedSendAt >= getQueueClickIntervalMs();
    }

    private void processQueuedClicks() {
        if (!shouldQueueClick() || queuedClicks.isEmpty()) return;
        if (!canSendNextQueuedClick()) return;

        int[] next = queuedClicks.pollFirst();
        if (next == null) return;
        sendClickPacket(next[0], next[1]);
        lastQueuedSendAt = System.currentTimeMillis();
        if (ModuleManager.terminalManager != null) {
            ModuleManager.terminalManager.recordQueuedClickSend(getTerminalName());
        }
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
        if (solutionSlots.contains(slotIndex)) {
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
    }
    
    private void sendClickPacket(int slot, int button) {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player != null && mc.player.currentScreenHandler != null) {
                net.minecraft.screen.ScreenHandler handler = mc.player.currentScreenHandler;
                ComponentChangesHash.ComponentHasher hasher = component -> component.hashCode();
                ItemStackHash cursorHash = ItemStackHash.fromItemStack(mc.player.currentScreenHandler.getCursorStack(), hasher);
                net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket packet = 
                    new net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket(
                        handler.syncId,
                        handler.getRevision(), 
                        (short) slot, 
                        (byte) button, 
                        net.minecraft.screen.slot.SlotActionType.PICKUP, 
                        Int2ObjectMaps.emptyMap(),
                        cursorHash
                    );
                mc.getNetworkHandler().sendPacket(packet);
            }
        } catch (Exception ignored) {
        }
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
