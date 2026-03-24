package com.revampes.Fault.modules.impl.dungeon.Terminals;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.revampes.Fault.events.impl.RenderScreenEvent;
import com.revampes.Fault.events.impl.SlotClickEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.ColorSetting;
import com.revampes.Fault.settings.impl.SliderSetting;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class TerminalManager extends Module {
    private final List<AbstractTerminal> terminals = new ArrayList<>();
    private AbstractTerminal activeTerminal = null;
    private long lastContainerSyncAt = 0L;
    private long lastQueueClickSentAt = -1L;
    private final Deque<int[]> userClickQueue = new ArrayDeque<>();
    private static final int MAX_USER_CLICK_QUEUE = 128;
    private static final long CONTAINER_SYNC_INTERVAL_MS = 80L;

    // Settings
    private final ButtonSetting enableColors = new ButtonSetting("Colors Terminal", true);
    private final ButtonSetting enableMelody = new ButtonSetting("Melody Terminal", true);
    private final ButtonSetting enableNumbers = new ButtonSetting("Numbers Terminal", true);
    private final ButtonSetting enableRedGreen = new ButtonSetting("Red Green Terminal", true);
    private final ButtonSetting enableRubix = new ButtonSetting("Rubix Terminal", true);
    private final ButtonSetting enableStartsWith = new ButtonSetting("Starts With Terminal", true);
    private final ButtonSetting queueClick = new ButtonSetting("Zero Ping", true);
    private final SliderSetting queueClickDelay = new SliderSetting("Queue click delay", "ms", 200.0, 100.0, 500.0, 10.0);
    private final SliderSetting queueClicksInAdvance = new SliderSetting("Clicks In Advance", 2.0, 0.0, 10.0, 1.0);
    private final ButtonSetting holdClick = new ButtonSetting("Hold Click", false);
    private final SliderSetting holdClickDelay = new SliderSetting("Hold Click Delay", "ms", 120.0, 50.0, 300.0, 10.0);

    private final ColorSetting highlightColor = new ColorSetting("Highlight Color", new Color(0, 255, 0, 128));
    private final SliderSetting scale = new SliderSetting("Overlay Scale", 1.0f, 0.5f, 4.0f, 0.1f);
    private final SliderSetting renderOffsetX = new SliderSetting("Offset X", 0, -100, 100, 1);
    private final SliderSetting renderOffsetY = new SliderSetting("Offset Y", 0, -100, 100, 1);

    public TerminalManager() {
        super("Terminals", category.Dungeon);
        initializeTerminals();
        registerSettings();
    }

    private void initializeTerminals() {
        terminals.add(new ColorsTerminal());
        terminals.add(new MelodyTerminal());
        terminals.add(new NumbersTerminal());
        terminals.add(new RedGreenTerminal());
        terminals.add(new RubixTerminal());
        terminals.add(new StartsWithTerminal());
    }

    private void registerSettings() {
        this.registerSetting(enableColors);
        this.registerSetting(enableMelody);
        this.registerSetting(enableNumbers);
        this.registerSetting(enableRedGreen);
        this.registerSetting(enableRubix);
        this.registerSetting(enableStartsWith);
        this.registerSetting(queueClick);
        this.registerSetting(queueClickDelay);
        this.registerSetting(queueClicksInAdvance);
        this.registerSetting(holdClick);
        this.registerSetting(holdClickDelay);
        this.registerSetting(highlightColor);
        this.registerSetting(scale);
        this.registerSetting(renderOffsetX);
        this.registerSetting(renderOffsetY);
    }

    @EventHandler
    public void onRenderScreen(RenderScreenEvent event) {
        if (!isEnabled()) return;
        if (!(event.screen instanceof HandledScreen<?>)) return;

        HandledScreen<?> screen = event.screen;
        if (!(screen.getScreenHandler() instanceof GenericContainerScreenHandler handler)) return;

        if (activeTerminal != null && activeTerminal.isInTerminal()) {
            long now = System.currentTimeMillis();
            if (now - lastContainerSyncAt >= CONTAINER_SYNC_INTERVAL_MS) {
                syncActiveTerminalFromHandler(handler);
                lastContainerSyncAt = now;
            }

            flushUserClickQueue();
        }

        // Render active terminal if any
        if (activeTerminal != null && activeTerminal.isInTerminal()) {
            activeTerminal.render(event);

            int queuedLeft = activeTerminal.getPendingQueueCount();
            if (isQueueClickEnabled() && queuedLeft > 0) {
                String label = "Queue Left: " + queuedLeft;
                int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(label);
                float overlayScale = getOverlayScale();
                int width = (int) (9 * 18 * overlayScale);
                int windowSize = Math.max(9, getActiveWindowSize());
                int rows = Math.max(1, windowSize / 9);
                int overlayHeight = (int) (rows * 18 * overlayScale);
                String terminalName = getActiveTerminalName();
                if ("Numbers".equals(terminalName) || "Rubix".equals(terminalName)) {
                    overlayHeight = (int) (6 * 18 * overlayScale);
                }

                int overlayX = event.context.getScaledWindowWidth() / 2 - width / 2 + (int) Math.round(getOverlayOffsetX() * overlayScale);
                int overlayY = event.context.getScaledWindowHeight() / 2 - overlayHeight / 2 + (int) Math.round(getOverlayOffsetY() * overlayScale);
                int x = overlayX + width - textWidth - 6;
                int y = overlayY + 4;
                event.context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, label, x, y, 0xFFFF5555);
            }
        }
    }

    @EventHandler
    public void onSlotClick(SlotClickEvent event) {
        if (!isEnabled()) return;
        if (activeTerminal == null || !activeTerminal.isInTerminal()) return;

        if (event.slotId < 0) return;

        // Block vanilla container interaction so terminal clicks do not move/pick cursor items.
        event.setCancelled(true);

        if (event.actionType == SlotActionType.PICKUP) {
            enqueueUserClick(event.slotId, event.button);
        }
    }

    public void handleScreenOpen(String windowTitle, int windowId, int slotCount, GenericContainerScreenHandler handler) {
        // Try to match against all terminals
        boolean matched = false;
        for (AbstractTerminal terminal : terminals) {
            if (terminal.matches(windowTitle)) {
                matched = true;
                if (isTerminalEnabled(terminal.getTerminalName())) {
                    activeTerminal = terminal;
                    activeTerminal.onWindowOpen(windowTitle, windowId, slotCount);
                    lastContainerSyncAt = 0L;
                    userClickQueue.clear();
                    // Slot reading will happen after delay in the mixin
                    return;
                }
            }
        }
    }

    public void handleScreenClose() {
        if (activeTerminal != null) {
            activeTerminal.onWindowClose();
            activeTerminal = null;
        }
        lastContainerSyncAt = 0L;
        userClickQueue.clear();
    }

    private void syncActiveTerminalFromHandler(GenericContainerScreenHandler handler) {
        if (activeTerminal == null || !activeTerminal.isInTerminal()) return;

        int limit = Math.min(handler.slots.size(), activeTerminal.getWindowSize());
        for (int i = 0; i < limit; i++) {
            ItemStack itemStack = handler.slots.get(i).getStack();
            activeTerminal.onSlotUpdate(i, itemStack);
        }
    }

    public void handleSlotReading(GenericContainerScreenHandler handler) {
        if (activeTerminal == null || !activeTerminal.isInTerminal()) return;
        
        int limit = Math.min(handler.slots.size(), activeTerminal.getWindowSize());
        for (int i = 0; i < limit; i++) {
            var slot = handler.slots.get(i);
            var itemStack = slot.getStack();
            if (!itemStack.isEmpty()) {
                activeTerminal.onSlotUpdate(i, itemStack);
            }
        }
        activeTerminal.solve();
    }

    public void handleTerminalClick(int slot, int button) {
        if (activeTerminal != null && activeTerminal.isInTerminal()) {
            enqueueUserClick(slot, button);
        }
    }

    public void handleSlotUpdate(int slotIndex, int revision, ItemStack itemStack) {
        if (activeTerminal != null && activeTerminal.isInTerminal()) {
            activeTerminal.onServerWindowUpdate(revision);
            activeTerminal.onSlotUpdate(slotIndex, itemStack);
        }
    }

    public void handleSlotClick(int slotIndex, int button) {
        if (activeTerminal != null && activeTerminal.isInTerminal()) {
            enqueueUserClick(slotIndex, button);
        }
    }

    private void enqueueUserClick(int slot, int button) {
        if (activeTerminal == null || !activeTerminal.isInTerminal()) {
            return;
        }

        int normalizedButton = button == 0 ? 0 : 1;
        if (!isQueueClickEnabled()) {
            activeTerminal.onSlotClick(slot, normalizedButton);
            return;
        }

        if (userClickQueue.size() >= MAX_USER_CLICK_QUEUE) {
            userClickQueue.pollFirst();
        }
        userClickQueue.addLast(new int[]{slot, normalizedButton});
        flushUserClickQueue();
    }

    private void flushUserClickQueue() {
        if (activeTerminal == null || !activeTerminal.isInTerminal()) {
            userClickQueue.clear();
            return;
        }

        // Push a small burst each frame; terminal-level queueing/acks handles send pacing.
        int burst = Math.min(6, userClickQueue.size());
        for (int i = 0; i < burst; i++) {
            int[] click = userClickQueue.pollFirst();
            if (click == null) {
                break;
            }
            activeTerminal.onSlotClick(click[0], click[1]);
        }
    }

    private boolean isTerminalEnabled(String terminalName) {
        return switch (terminalName) {
            case "Colors" -> enableColors.isToggled();
            case "Melody" -> enableMelody.isToggled();
            case "Numbers" -> enableNumbers.isToggled();
            case "Red Green" -> enableRedGreen.isToggled();
            case "Rubix" -> enableRubix.isToggled();
            case "Starts With" -> enableStartsWith.isToggled();
            default -> false;
        };
    }

    @Override
    public void guiUpdate() {
        // Visibility conditions can be set here if needed
    }

    public AbstractTerminal getActiveTerminal() {
        return activeTerminal;
    }

    public boolean hasActiveTerminal() {
        return activeTerminal != null && activeTerminal.isInTerminal();
    }

    public int getActiveWindowSize() {
        return activeTerminal != null ? activeTerminal.getWindowSize() : 0;
    }

    public String getActiveTerminalName() {
        return activeTerminal != null ? activeTerminal.getTerminalName() : "";
    }

    public boolean isQueueClickEnabled() {
        return queueClick.isToggled();
    }

    public int getQueueClicksInAdvance() {
        return (int) Math.round(Math.max(0.0, queueClicksInAdvance.getInput()));
    }

    public boolean isHoldClickEnabled() {
        return holdClick.isToggled();
    }

    public long getQueueClickBaseIntervalMs() {
        return Math.max(100L, Math.min(500L, Math.round(queueClickDelay.getInput())));
    }

    public long getHoldClickIntervalMs() {
        return Math.max(50L, Math.round(holdClickDelay.getInput()));
    }

    public long getEffectiveHoldClickIntervalMs() {
        if (isQueueClickEnabled()) {
            return getQueueClickBaseIntervalMs();
        }
        return getHoldClickIntervalMs();
    }

    public void recordQueuedClickSend(String terminalName) {
        long now = System.currentTimeMillis();
        if (lastQueueClickSentAt > 0L) {
            long delta = now - lastQueueClickSentAt;
            System.out.println("[QueueClick] " + terminalName + " interval=" + delta + "ms");
        } else {
            System.out.println("[QueueClick] " + terminalName + " first click");
        }
        lastQueueClickSentAt = now;
    }

    public long getQueueClickIntervalMs() {
        long base = getQueueClickBaseIntervalMs();
        int jitter = ThreadLocalRandom.current().nextInt(10, 21);
        int sign = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        long jittered = base + sign * (long) jitter;
        return Math.max(50L, jittered);
    }

    public float getOverlayScale() {
        return (float) scale.getInput();
    }

    public int getOverlayOffsetX() {
        return (int) Math.round(renderOffsetX.getInput());
    }

    public int getOverlayOffsetY() {
        return (int) Math.round(renderOffsetY.getInput());
    }
}
