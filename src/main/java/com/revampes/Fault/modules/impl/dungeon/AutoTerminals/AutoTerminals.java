package com.revampes.Fault.modules.impl.dungeon.AutoTerminals;

import com.revampes.Fault.modules.Module;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.Utils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.sync.ComponentChangesHash;
import net.minecraft.screen.sync.ItemStackHash;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoTerminals extends Module {
    private enum TerminalType {
        NONE,
        ORDER,
        CORRECT,
        START,
        COLOR,
        COLORPAD,
        MELODY
    }

    private final ButtonSetting onlyInDungeon = new ButtonSetting("Only In Dungeon", true);
    private final ButtonSetting pauseWhenLegitTerminals = new ButtonSetting("Pause When Terminals Enabled", true);
    private final ButtonSetting zeroPing = new ButtonSetting("Zero Ping", false);
    private final SliderSetting firstClickDelayMs = new SliderSetting("First click delay", "ms", 500.0, 0.0, 1500.0, 25.0);
    private final SliderSetting clickDelayMs = new SliderSetting("Click Delay", "ms", 120.0, 40.0, 400.0, 5.0);
    private final SliderSetting clicksInAdvance = new SliderSetting("Clicks In Advance", 2.0, 0.0, 10.0, 1.0);
    private final SliderSetting clickButton = new SliderSetting("Click Button", 0.0, 0.0, 2.0, 1.0);

    private final ButtonSetting solveOrder = new ButtonSetting("Solve ORDER", true);
    private final ButtonSetting solveCorrect = new ButtonSetting("Solve CORRECT", true);
    private final ButtonSetting solveStart = new ButtonSetting("Solve START", true);
    private final ButtonSetting solveColor = new ButtonSetting("Solve COLOR", true);
    private final ButtonSetting solveColorPad = new ButtonSetting("Solve COLORPAD", true);
    private final ButtonSetting solveMelody = new ButtonSetting("Solve Melody", true);
    private final ButtonSetting debugStartColorPad = new ButtonSetting("Debug START/COLORPAD", false);

    private final Deque<Integer> clickQueue = new ArrayDeque<>();

    private TerminalType terminalType = TerminalType.NONE;
    private String color = "";
    private String currentTitle = "";
    private char startChar = ' ';

    private int activeSyncId = -1;
    private int activeWindowSize = 0;
    private int optimisticClicksSent = 0;
    private int lastSeenRevision = Integer.MIN_VALUE;

    private boolean recalculate = false;
    private long lastClickAt = 0L;
    private long lastRevisionSeenAt = 0L;
    private boolean awaitingStateUpdate = false;
    private long awaitStateSince = 0L;
    private String lastMelodySignature = "";
    private long lastStartDebugAt = 0L;
    private long lastColorPadDebugAt = 0L;
    private long terminalOpenedAt = 0L;
    private boolean firstClickPending = true;

    public AutoTerminals() {
        super("AutoTerminals", category.Dungeon);

        this.registerSetting(onlyInDungeon);
        this.registerSetting(pauseWhenLegitTerminals);
        this.registerSetting(zeroPing);
        this.registerSetting(firstClickDelayMs);
        this.registerSetting(clickDelayMs);
        this.registerSetting(clicksInAdvance);
        this.registerSetting(clickButton);

        this.registerSetting(solveOrder);
        this.registerSetting(solveCorrect);
        this.registerSetting(solveStart);
        this.registerSetting(solveColor);
        this.registerSetting(solveColorPad);
        this.registerSetting(solveMelody);
        this.registerSetting(debugStartColorPad);
    }

    @Override
    public String getDesc() {
        return "Standalone AutoTerminals solver (separate from LegitTerminals).";
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @Override
    public void onUpdate() {
        if (mc == null || mc.player == null || mc.getNetworkHandler() == null) {
            resetState();
            return;
        }

        if (pauseWhenLegitTerminals.isToggled()
            && ModuleManager.terminalManager != null
            && ModuleManager.terminalManager.isEnabled()) {
            resetState();
            return;
        }

        if (onlyInDungeon.isToggled() && !DungeonUtils.isInDungeon()) {
            resetState();
            return;
        }

        if (!(mc.currentScreen instanceof HandledScreen<?> handledScreen)) {
            resetState();
            return;
        }

        if (!(handledScreen.getScreenHandler() instanceof GenericContainerScreenHandler handler)) {
            resetState();
            return;
        }

        String title = handledScreen.getTitle().getString();
        TerminalType detectedType = detectTerminalType(title);

        if (detectedType == TerminalType.NONE || !isTerminalEnabled(detectedType)) {
            resetState();
            return;
        }

        if (detectedType != terminalType || activeSyncId != handler.syncId) {
            openTerminal(detectedType, title, handler.syncId, handler.getRows() * 9);
        }

        if (handler.getRevision() != lastSeenRevision) {
            lastSeenRevision = handler.getRevision();
            optimisticClicksSent = 0;
            lastRevisionSeenAt = System.currentTimeMillis();
            awaitingStateUpdate = false;
            recalculate = true;
        }

        if (awaitingStateUpdate && System.currentTimeMillis() - awaitStateSince > 260L) {
            // Fallback when server slot/revision updates are delayed.
            awaitingStateUpdate = false;
            recalculate = true;
        }

        List<ItemStack> stacks = readWindowStacks(handler, activeWindowSize);

        if (terminalType == TerminalType.MELODY) {
            solveMelodyTerminal(stacks);
        } else if (!awaitingStateUpdate && (clickQueue.isEmpty() || recalculate)) {
            recalculate = calculate(stacks);
        }

        trySendNextClick(handler);
    }

    private void openTerminal(TerminalType newType, String title, int syncId, int windowSize) {
        String oldTitle = stripFormatting(currentTitle);
        String newTitle = stripFormatting(title == null ? "" : title);
        boolean sameSession = terminalType == newType && oldTitle.equalsIgnoreCase(newTitle);

        terminalType = newType;
        currentTitle = title == null ? "" : title;
        activeSyncId = syncId;
        activeWindowSize = windowSize;
        if (!sameSession) {
            terminalOpenedAt = System.currentTimeMillis();
            firstClickPending = true;
            lastClickAt = 0L;
        }
        optimisticClicksSent = 0;
        lastSeenRevision = Integer.MIN_VALUE;
        lastRevisionSeenAt = 0L;
        clickQueue.clear();
        recalculate = true;
        awaitingStateUpdate = false;
        awaitStateSince = 0L;
        color = "";
        startChar = ' ';
        lastMelodySignature = "";

        if (newType == TerminalType.START) {
            startChar = parseStartChar(title);
            debugStart("open title='" + stripFormatting(title) + "' parsed='" + startChar + "'");
        } else if (newType == TerminalType.COLORPAD) {
            debugColorPad("open title='" + stripFormatting(title) + "' size=" + windowSize);
        } else if (newType == TerminalType.COLOR) {
            Matcher matcher = Pattern.compile("Select all the (.*) items!").matcher(title);
            if (matcher.find()) {
                color = stripFormatting(matcher.group(1)).toUpperCase();
            }
        }
    }

    private void resetState() {
        terminalType = TerminalType.NONE;
        clickQueue.clear();
        color = "";
        currentTitle = "";
        startChar = ' ';
        activeSyncId = -1;
        activeWindowSize = 0;
        optimisticClicksSent = 0;
        lastSeenRevision = Integer.MIN_VALUE;
        lastRevisionSeenAt = 0L;
        lastClickAt = 0L;
        recalculate = false;
        awaitingStateUpdate = false;
        awaitStateSince = 0L;
        lastMelodySignature = "";
        terminalOpenedAt = 0L;
        firstClickPending = true;
    }

    private TerminalType detectTerminalType(String title) {
        if (title == null) return TerminalType.NONE;
        String clean = stripFormatting(title).toLowerCase();

        if (clean.startsWith("click in order")) return TerminalType.ORDER;
        if (clean.startsWith("correct all the panes")) return TerminalType.CORRECT;
        if (clean.contains("starts with")) return TerminalType.START;
        if (clean.startsWith("select all the ")) return TerminalType.COLOR;
        if (clean.contains("same color")) return TerminalType.COLORPAD;
        if (clean.startsWith("click the button on time")) return TerminalType.MELODY;

        return TerminalType.NONE;
    }

    private boolean isTerminalEnabled(TerminalType type) {
        return switch (type) {
            case ORDER -> solveOrder.isToggled();
            case CORRECT -> solveCorrect.isToggled();
            case START -> solveStart.isToggled();
            case COLOR -> solveColor.isToggled();
            case COLORPAD -> solveColorPad.isToggled();
            case MELODY -> solveMelody.isToggled();
            case NONE -> false;
        };
    }

    private List<ItemStack> readWindowStacks(GenericContainerScreenHandler handler, int windowSize) {
        List<ItemStack> stacks = new ArrayList<>(windowSize);
        int limit = Math.min(windowSize, handler.slots.size());
        for (int i = 0; i < limit; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            stacks.add(stack == null ? ItemStack.EMPTY : stack);
        }
        while (stacks.size() < windowSize) {
            stacks.add(ItemStack.EMPTY);
        }
        return stacks;
    }

    private boolean calculate(List<ItemStack> stacks) {
        clickQueue.clear();

        return switch (terminalType) {
            case ORDER -> calculateOrder(stacks);
            case CORRECT -> calculateCorrect(stacks);
            case START -> calculateStart(stacks);
            case COLOR -> calculateColor(stacks);
            case COLORPAD -> calculateColorPad(stacks);
            default -> true;
        };
    }

    private boolean calculateOrder(List<ItemStack> stacks) {
        if (stacks.size() < 36) return true;

        int[] byNumber = new int[14];
        for (int i = 0; i < byNumber.length; i++) {
            byNumber[i] = -1;
        }

        for (int slot = 10; slot <= 25; slot++) {
            if (slot == 17 || slot == 18) continue;

            ItemStack stack = stacks.get(slot);
            if (stack.isEmpty()) continue;

            boolean isLegacyTarget = stack.getDamage() == 14;
            boolean isModernTarget = isType(stack, "red_stained_glass_pane");
            if (!isLegacyTarget && !isModernTarget) continue;

            int amount = stack.getCount();
            if (amount < 1 || amount > 14) {
                return true;
            }
            byNumber[amount - 1] = slot;
        }

        for (int slot : byNumber) {
            if (slot != -1) {
                clickQueue.addLast(slot);
            }
        }
        return false;
    }

    private boolean calculateCorrect(List<ItemStack> stacks) {
        if (stacks.size() < 45) return true;

        for (int i = 0; i < 45; i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) continue;

            if (stack.getDamage() == 14 || isType(stack, "red_stained_glass_pane")) {
                clickQueue.addLast(i);
            }
        }
        return false;
    }

    private boolean calculateStart(List<ItemStack> stacks) {
        if (stacks.size() < 36) {
            debugStart("skip solve: stacks too small size=" + stacks.size());
            return true;
        }
        if (startChar == ' ') {
            startChar = parseStartChar(currentTitle);
            debugStart("reparse title='" + stripFormatting(currentTitle) + "' parsed='" + startChar + "'");
        }
        if (startChar == ' ') {
            debugStart("no parsed character; skipping solve");
            return true;
        }

        int matched = 0;

        int limit = Math.min(stacks.size(), 54);
        for (int i = 0; i < limit; i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) continue;

            String name = stripFormatting(stack.getName().getString()).trim();
            if (!name.isEmpty() && Character.toLowerCase(name.charAt(0)) == startChar) {
                clickQueue.addLast(i);
                matched++;
            }
        }
        debugStart("solve char='" + startChar + "' matched=" + matched + " queue=" + clickQueue.size());
        return false;
    }

    private boolean calculateColor(List<ItemStack> stacks) {
        if (stacks.size() < 54) return true;

        String target = color.toUpperCase();
        for (int i = 0; i < 54; i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty() || stack.hasGlint()) continue;

            String name = stripFormatting(stack.getName().getString()).toUpperCase();
            if (name.contains(target)
                || (target.equals("SILVER") && name.contains("LIGHT GRAY"))
                || (target.equals("WHITE") && name.equals("WOOL"))
                || (target.equals("WHITE") && name.contains("BONE"))
                || (target.equals("BLACK") && name.contains("INK"))
                || (target.equals("BROWN") && name.contains("COCOA"))
                || (target.equals("BLUE") && name.contains("LAPIS"))) {
                clickQueue.addLast(i);
            }
        }
        return false;
    }

    private boolean calculateColorPad(List<ItemStack> stacks) {
        if (stacks.size() <= 32) {
            debugColorPad("skip solve: stacks too small size=" + stacks.size());
            return true;
        }

        int[] board = new int[]{12, 13, 14, 21, 22, 23, 30, 31, 32};
        List<Integer> best = new ArrayList<>();
        int bestShift = -1;
        StringBuilder boardInfo = new StringBuilder();

        for (int slot : board) {
            ItemStack stack = stacks.get(slot);
            if (stack.isEmpty()) {
                boardInfo.append(slot).append("=empty ");
            } else {
                int mapped = getColorPadValue(stack);
                boardInfo.append(slot).append("=").append(mapped).append("[")
                    .append(Registries.ITEM.getId(stack.getItem())).append("] ");
            }
        }

        for (int shift = 0; shift < 5; shift++) {
            Map<Integer, Integer> map = new HashMap<>();
            map.put(4, (4 + shift) % 5);
            map.put(13, (3 + shift) % 5);
            map.put(11, (2 + shift) % 5);
            map.put(14, (1 + shift) % 5);
            map.put(1, shift % 5);

            List<Integer> candidate = new ArrayList<>();
            for (int slot : board) {
                ItemStack stack = stacks.get(slot);
                if (stack.isEmpty()) continue;

                int meta = getColorPadValue(stack);
                if (meta == -1) continue;
                int presses = map.getOrDefault(meta, 0);
                for (int i = 0; i < presses; i++) {
                    candidate.add(slot);
                }
            }

            if (best.isEmpty() || candidate.size() < best.size()) {
                best = candidate;
                bestShift = shift;
            }
        }

        clickQueue.addAll(best);
        debugColorPad("board " + boardInfo + "| bestShift=" + bestShift + " queue=" + clickQueue.size() + " seq=" + best);
        return false;
    }

    private void solveMelodyTerminal(List<ItemStack> stacks) {
        if (stacks.size() < 45) return;

        String signature = buildMelodySignature(stacks);
        if (signature.equals(lastMelodySignature)) return;
        lastMelodySignature = signature;

        int activeColumn = 0;
        for (int i = 1; i <= 5; i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) continue;

            int meta = stack.getDamage();
            if (meta == 2 || meta == 10 || isType(stack, "green_stained_glass_pane") || isType(stack, "magenta_stained_glass_pane")) {
                activeColumn = i;
            }
        }

        if (activeColumn == 0) return;

        int targetRow = 0;
        for (int row = 1; row <= 4; row++) {
            int slot = row * 9 + activeColumn;
            ItemStack stack = stacks.get(slot);
            if (!stack.isEmpty() && (stack.getDamage() == 5 || isType(stack, "lime_stained_glass_pane"))) {
                targetRow = row;
            }
        }

        if (targetRow != 0) {
            int buttonSlot = targetRow * 9 + 7;
            if (!clickQueue.contains(buttonSlot)) {
                clickQueue.addLast(buttonSlot);
            }
        }
    }

    private String buildMelodySignature(List<ItemStack> stacks) {
        StringBuilder sb = new StringBuilder(45 * 8);
        for (int i = 0; i < 45; i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) {
                sb.append("e;");
            } else {
                sb.append(Registries.ITEM.getId(stack.getItem())).append(':').append(stack.getDamage()).append(':').append(stack.getCount()).append(';');
            }
        }
        return sb.toString();
    }

    private void trySendNextClick(GenericContainerScreenHandler handler) {
        if (clickQueue.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (firstClickPending) {
            long firstDelay = Math.max(0L, Math.round(firstClickDelayMs.getInput()));
            if (terminalOpenedAt > 0L && now - terminalOpenedAt < firstDelay) {
                return;
            }
        }

        long delay = Math.max(20L, Math.round(clickDelayMs.getInput()));
        if (now - lastClickAt < delay) return;

        if (zeroPing.isToggled()) {
            int maxAdvance = (int) Math.round(Math.max(0.0, clicksInAdvance.getInput()));
            if (now - lastRevisionSeenAt > 350L) {
                optimisticClicksSent = 0;
            }
            if (optimisticClicksSent > maxAdvance) {
                return;
            }
        }

        Integer slot = clickQueue.peekFirst();
        if (slot == null) return;

        int button = (int) Math.round(clickButton.getInput());
        if (terminalType == TerminalType.START) {
            debugStart("dispatch slot=" + slot + " button=" + button + " queueLeft=" + clickQueue.size());
        } else if (terminalType == TerminalType.COLORPAD) {
            debugColorPad("dispatch slot=" + slot + " button=" + button + " queueLeft=" + clickQueue.size());
        }
        if (sendClick(handler, slot, button)) {
            lastClickAt = now;
            firstClickPending = false;
            clickQueue.pollFirst();
            awaitingStateUpdate = true;
            awaitStateSince = now;
            recalculate = false;
            if (zeroPing.isToggled()) {
                optimisticClicksSent++;
            }
        }
    }

    private boolean sendClick(ScreenHandler handler, int slot, int button) {
        if (mc.player == null || mc.getNetworkHandler() == null) {
            if (terminalType == TerminalType.START) debugStart("sendClick blocked: null player/network");
            if (terminalType == TerminalType.COLORPAD) debugColorPad("sendClick blocked: null player/network");
            return false;
        }
        if (handler.syncId != activeSyncId) {
            if (terminalType == TerminalType.START) debugStart("sendClick blocked: syncId mismatch current=" + handler.syncId + " expected=" + activeSyncId);
            if (terminalType == TerminalType.COLORPAD) debugColorPad("sendClick blocked: syncId mismatch current=" + handler.syncId + " expected=" + activeSyncId);
            return false;
        }

        try {
            int revision = handler.getRevision();
            ComponentChangesHash.ComponentHasher hasher = component -> component.hashCode();
            ItemStackHash emptyCursorHash = ItemStackHash.fromItemStack(ItemStack.EMPTY, hasher);

            ClickSlotC2SPacket packet = new ClickSlotC2SPacket(
                handler.syncId,
                revision,
                (short) slot,
                (byte) button,
                SlotActionType.PICKUP,
                Int2ObjectMaps.emptyMap(),
                emptyCursorHash
            );
            mc.getNetworkHandler().sendPacket(packet);
            return true;
        } catch (Throwable ignored) {
            if (terminalType == TerminalType.START) debugStart("sendClick exception");
            if (terminalType == TerminalType.COLORPAD) debugColorPad("sendClick exception");
            return false;
        }
    }

    private boolean isType(ItemStack stack, String contains) {
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        return id.contains(contains);
    }

    private int getColorPadValue(ItemStack stack) {
        String id = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase();
        if (id.contains("red")) return 14;
        if (id.contains("orange")) return 1;
        if (id.contains("yellow")) return 4;
        if (id.contains("lime") || id.contains("green")) return 13;
        if (id.contains("blue") || id.contains("cyan")) return 11;

        int legacy = stack.getDamage();
        if (legacy == 14 || legacy == 1 || legacy == 4 || legacy == 13 || legacy == 11) {
            return legacy;
        }
        return -1;
    }

    private char parseStartChar(String title) {
        String cleanTitle = stripFormatting(title == null ? "" : title);
        Matcher matcher = Pattern.compile("starts with\\s*[:：]\\s*['‘’\\\"]?([^'‘’\\\"\\s?])", Pattern.CASE_INSENSITIVE).matcher(cleanTitle);
        if (matcher.find()) {
            String g = matcher.group(1);
            if (g != null && !g.isEmpty()) {
                return Character.toLowerCase(g.charAt(0));
            }
        }

        int quote = cleanTitle.indexOf('\'');
        if (quote >= 0 && quote + 1 < cleanTitle.length()) {
            return Character.toLowerCase(cleanTitle.charAt(quote + 1));
        }

        return ' ';
    }

    private String stripFormatting(String input) {
        return input == null ? "" : input.replaceAll("\\u00A7[0-9a-fk-or]", "");
    }

    private void debugStart(String message) {
        if (!debugStartColorPad.isToggled()) return;
        long now = System.currentTimeMillis();
        if (now - lastStartDebugAt < 120L) return;
        lastStartDebugAt = now;
        Utils.addChatMessage("§b[AT-START] §7" + message);
    }

    private void debugColorPad(String message) {
        if (!debugStartColorPad.isToggled()) return;
        long now = System.currentTimeMillis();
        if (now - lastColorPadDebugAt < 120L) return;
        lastColorPadDebugAt = now;
        Utils.addChatMessage("§d[AT-COLORPAD] §7" + message);
    }
}
