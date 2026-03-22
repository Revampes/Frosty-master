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
    private volatile boolean queueReadyForDispatch = true;
    private volatile boolean queueAwaitingServerAck = false;
    private volatile int lastSeenServerRevision = Integer.MIN_VALUE;
    private volatile long lastQueuedClickSentAt = 0L;
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
            lastSeenServerRevision = revision;
            queueReadyForDispatch = true;
            if (queueAwaitingServerAck) {
                queueAwaitingServerAck = false;
                onQueuedClickAcknowledged();
            }
        }
    }

    protected void resetQueueDispatchState() {
        queueReadyForDispatch = true;
        queueAwaitingServerAck = false;
        lastSeenServerRevision = Integer.MIN_VALUE;
        lastQueuedClickSentAt = 0L;
    }

    protected void recordQueuedClickSent() {
        lastQueuedClickSentAt = System.currentTimeMillis();
    }

    protected boolean isQueuedClickTimeout() {
        if (lastQueuedClickSentAt == 0L) return false;
        return System.currentTimeMillis() - lastQueuedClickSentAt >= QUEUE_TIMEOUT_MS;
    }

    protected boolean canDispatchQueuedClick(boolean queueEnabled) {
        return !queueEnabled || queueReadyForDispatch;
    }

    protected void markQueuedClickDispatched(boolean queueEnabled) {
        if (queueEnabled) {
            queueReadyForDispatch = false;
            queueAwaitingServerAck = true;
        }
    }

    protected boolean isAwaitingQueuedClickAck() {
        return queueAwaitingServerAck;
    }

    protected void clearQueuedClickAckWait() {
        queueAwaitingServerAck = false;
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

            net.minecraft.screen.sync.ComponentChangesHash.ComponentHasher hasher = component -> component.hashCode();
            net.minecraft.screen.sync.ItemStackHash emptyCursorHash = net.minecraft.screen.sync.ItemStackHash.fromItemStack(net.minecraft.item.ItemStack.EMPTY, hasher);
            net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket packet =
                new net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket(
                    handler.syncId,
                    handler.getRevision(),
                    (short) slot,
                    (byte) button,
                    net.minecraft.screen.slot.SlotActionType.PICKUP,
                    it.unimi.dsi.fastutil.ints.Int2ObjectMaps.emptyMap(),
                    emptyCursorHash
                );
            mc.getNetworkHandler().sendPacket(packet);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
