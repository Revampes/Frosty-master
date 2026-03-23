package com.revampes.Fault.modules.impl.dungeon.Terminals;

import com.revampes.Fault.events.impl.RenderScreenEvent;
import com.revampes.Fault.events.impl.SlotClickEvent;
import com.revampes.Fault.modules.ModuleManager;
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
    private volatile int queueInFlightClicks = 0;
    private volatile int lastSeenServerRevision = Integer.MIN_VALUE;
    private volatile long lastQueuedClickSentAt = 0L;
    private volatile int optimisticQueueRevisionBase = Integer.MIN_VALUE;
    private volatile int optimisticQueueClicksSent = 0;
    private static final long QUEUE_TIMEOUT_MS = 5000L;  // Failsafe timeout for queue stuck detection

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
        resetQueueDispatchState();
    }

    public void onServerWindowUpdate(int revision) {
        if (revision != lastSeenServerRevision) {
            int delta = lastSeenServerRevision == Integer.MIN_VALUE ? 1 : Math.max(1, revision - lastSeenServerRevision);
            int ackCount = Math.min(queueInFlightClicks, delta);
            for (int i = 0; i < ackCount; i++) {
                onQueuedClickAcknowledged();
            }
            queueInFlightClicks = Math.max(0, queueInFlightClicks - ackCount);
            lastSeenServerRevision = revision;
            optimisticQueueRevisionBase = revision;
            optimisticQueueClicksSent = 0;
        }
    }

    protected void resetQueueDispatchState() {
        queueInFlightClicks = 0;
        lastSeenServerRevision = Integer.MIN_VALUE;
        lastQueuedClickSentAt = 0L;
        optimisticQueueRevisionBase = Integer.MIN_VALUE;
        optimisticQueueClicksSent = 0;
    }

    protected void recordQueuedClickSent() {
        lastQueuedClickSentAt = System.currentTimeMillis();
    }

    protected boolean isQueuedClickTimeout() {
        if (lastQueuedClickSentAt == 0L) return false;
        return System.currentTimeMillis() - lastQueuedClickSentAt >= QUEUE_TIMEOUT_MS;
    }

    protected boolean hasQueuedDispatchIntervalElapsed() {
        if (lastQueuedClickSentAt == 0L) {
            return true;
        }

        long intervalMs = 200L;
        if (ModuleManager.terminalManager != null) {
            intervalMs = Math.max(50L, ModuleManager.terminalManager.getQueueClickBaseIntervalMs());
        }

        return System.currentTimeMillis() - lastQueuedClickSentAt >= intervalMs;
    }

    protected boolean canDispatchQueuedClick(boolean queueEnabled) {
        if (!queueEnabled) {
            return true;
        }
        int maxAdvance = 0;
        if (ModuleManager.terminalManager != null) {
            maxAdvance = ModuleManager.terminalManager.getQueueClicksInAdvance();
        }
        return queueInFlightClicks <= maxAdvance && hasQueuedDispatchIntervalElapsed();
    }

    protected void markQueuedClickDispatched(boolean queueEnabled) {
        if (queueEnabled) {
            queueInFlightClicks++;
        }
    }

    protected boolean isAwaitingQueuedClickAck() {
        int maxAdvance = 0;
        if (ModuleManager.terminalManager != null) {
            maxAdvance = ModuleManager.terminalManager.getQueueClicksInAdvance();
        }
        return queueInFlightClicks > maxAdvance;
    }

    protected boolean tryFallbackQueueAckRelease() {
        if (!isAwaitingQueuedClickAck()) {
            return true;
        }

        long sentAt = lastQueuedClickSentAt;
        if (sentAt <= 0L) {
            return false;
        }

        long fallbackMs = 180L;
        if (ModuleManager.terminalManager != null) {
            fallbackMs = Math.max(50L, ModuleManager.terminalManager.getQueueClickBaseIntervalMs());
        }

        if (System.currentTimeMillis() - sentAt >= fallbackMs) {
            queueInFlightClicks = Math.max(0, queueInFlightClicks - 1);
            // Treat fallback timeout as a synthetic ACK so queue head advances.
            onQueuedClickAcknowledged();
            return true;
        }

        return false;
    }

    protected void clearQueuedClickAckWait() {
        queueInFlightClicks = 0;
    }

    protected void onQueuedClickAcknowledged() {
        // Optional override in terminals that maintain explicit click queues.
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

    public int getPendingQueueCount() {
        return 0;
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

    // Forge 1.8.9-style no-pickup click: send a PICKUP click with an empty carried stack hash.
    protected boolean sendWindowClickNoPickup(int slot, int button) {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc == null || mc.player == null || mc.getNetworkHandler() == null || mc.player.currentScreenHandler == null) {
                return false;
            }

            net.minecraft.screen.ScreenHandler handler = mc.player.currentScreenHandler;
            if (windowId != -1 && handler.syncId != windowId) {
                return false;
            }

            boolean queueEnabled = ModuleManager.terminalManager != null && ModuleManager.terminalManager.isQueueClickEnabled();
            int packetRevision = handler.getRevision();
            if (queueEnabled) {
                if (optimisticQueueRevisionBase == Integer.MIN_VALUE) {
                    optimisticQueueRevisionBase = Math.max(lastSeenServerRevision, packetRevision);
                    optimisticQueueClicksSent = 0;
                }
                packetRevision = optimisticQueueRevisionBase + optimisticQueueClicksSent;
            }

            net.minecraft.screen.sync.ComponentChangesHash.ComponentHasher hasher = component -> component.hashCode();
            net.minecraft.screen.sync.ItemStackHash emptyCursorHash = net.minecraft.screen.sync.ItemStackHash.fromItemStack(net.minecraft.item.ItemStack.EMPTY, hasher);
            net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket packet =
                new net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket(
                    handler.syncId,
                    packetRevision,
                    (short) slot,
                    (byte) button,
                    net.minecraft.screen.slot.SlotActionType.PICKUP,
                    it.unimi.dsi.fastutil.ints.Int2ObjectMaps.emptyMap(),
                    emptyCursorHash
                );
            mc.getNetworkHandler().sendPacket(packet);
            if (queueEnabled) {
                optimisticQueueClicksSent++;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
