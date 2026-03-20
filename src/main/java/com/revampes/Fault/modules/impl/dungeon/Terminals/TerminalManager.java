package com.revampes.Fault.modules.impl.dungeon.Terminals;

import com.revampes.Fault.events.impl.RenderScreenEvent;
import com.revampes.Fault.events.impl.SlotClickEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.ColorSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.awt.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.ArrayList;
import java.util.List;

public class TerminalManager extends Module {
    private final List<AbstractTerminal> terminals = new ArrayList<>();
    private AbstractTerminal activeTerminal = null;
    private long lastContainerSyncAt = 0L;
    private static final long CONTAINER_SYNC_INTERVAL_MS = 80L;

    // Settings
    private final ButtonSetting enableColors = new ButtonSetting("Colors Terminal", true);
    private final ButtonSetting enableMelody = new ButtonSetting("Melody Terminal", true);
    private final ButtonSetting enableNumbers = new ButtonSetting("Numbers Terminal", true);
    private final ButtonSetting enableRedGreen = new ButtonSetting("Red Green Terminal", true);
    private final ButtonSetting enableRubix = new ButtonSetting("Rubix Terminal", true);
    private final ButtonSetting enableStartsWith = new ButtonSetting("Starts With Terminal", true);
    private final ButtonSetting queueClick = new ButtonSetting("Queue click", true);
    private final SliderSetting queueClickDelay = new SliderSetting("Queue click delay", "ms", 200.0, 100.0, 500.0, 10.0);

    private final ColorSetting highlightColor = new ColorSetting("Highlight Color", new Color(0, 255, 0, 128));
    private final SliderSetting scale = new SliderSetting("Overlay Scale", 1.0f, 0.5f, 4.0f, 0.1f);
    private final SliderSetting renderOffsetX = new SliderSetting("Offset X", 0, -100, 100, 1);
    private final SliderSetting renderOffsetY = new SliderSetting("Offset Y", 0, -100, 100, 1);

    public TerminalManager() {
        super("Terminal Helper", category.Dungeon);
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
        }

        // Render active terminal if any
        if (activeTerminal != null && activeTerminal.isInTerminal()) {
            activeTerminal.render(event);
        }
    }

    @EventHandler
    public void onSlotClick(SlotClickEvent event) {
        if (!isEnabled()) return;
        if (activeTerminal == null || !activeTerminal.isInTerminal()) return;

        // Handle slot click for terminal
        if (event.slotId >= 0) {
            activeTerminal.onSlotClick(event.slotId, event.button);
        }
    }

    public void handleScreenOpen(String windowTitle, int windowId, int slotCount, GenericContainerScreenHandler handler) {
        // Try to match against all terminals
        boolean matched = false;
        for (AbstractTerminal terminal : terminals) {
            if (terminal.matches(windowTitle)) {
                matched = true;
                System.out.println("[TERMINAL] Matched: " + terminal.getTerminalName());
                if (isTerminalEnabled(terminal.getTerminalName())) {
                    activeTerminal = terminal;
                    activeTerminal.onWindowOpen(windowTitle, windowId, slotCount);
                    lastContainerSyncAt = 0L;
                    System.out.println("[TERMINAL] Activated " + terminal.getTerminalName());
                    // Slot reading will happen after delay in the mixin
                    return;
                }
            }
        }
        if (!matched) {
            System.out.println("[TERMINAL] No match for: " + windowTitle);
        }
    }

    public void handleScreenClose() {
        if (activeTerminal != null) {
            activeTerminal.onWindowClose();
            activeTerminal = null;
        }
        lastContainerSyncAt = 0L;
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
        if (activeTerminal == null || !activeTerminal.isInTerminal()) {
            System.out.println("[TERMINAL] No active terminal when reading slots");
            return;
        }
        
        System.out.println("[TERMINAL] Reading slots after delay...");
        int nonEmptyCount = 0;
        int limit = Math.min(handler.slots.size(), activeTerminal.getWindowSize());
        for (int i = 0; i < limit; i++) {
            var slot = handler.slots.get(i);
            var itemStack = slot.getStack();
            if (!itemStack.isEmpty()) {
                nonEmptyCount++;
                String itemName = itemStack.getItem().toString();
                System.out.println("[TERMINAL] Slot " + i + ": " + itemName + " count=" + itemStack.getCount());
                activeTerminal.onSlotUpdate(i, itemStack);
            }
        }
        System.out.println("[TERMINAL] Found " + nonEmptyCount + " non-empty slots");
        System.out.println("[TERMINAL] Calling solve()");
        activeTerminal.solve();
        System.out.println("[TERMINAL] Solution slots: " + activeTerminal.getSolution());
    }

    public void handleTerminalClick(int slot, int button) {
        if (activeTerminal != null && activeTerminal.isInTerminal()) {
            activeTerminal.onSlotClick(slot, button);
        }
    }

    public void handleSlotUpdate(int slotIndex, ItemStack itemStack) {
        if (activeTerminal != null && activeTerminal.isInTerminal()) {
            activeTerminal.onSlotUpdate(slotIndex, itemStack);
        }
    }

    public void handleSlotClick(int slotIndex, int button) {
        if (activeTerminal != null && activeTerminal.isInTerminal()) {
            activeTerminal.onSlotClick(slotIndex, button);
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

    public long getQueueClickIntervalMs() {
        long base = Math.max(100L, Math.min(500L, Math.round(queueClickDelay.getInput())));
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
