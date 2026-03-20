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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.Deque;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorsTerminal extends AbstractTerminal {
    private String extraColor = null;
    private final Deque<int[]> queuedClicks = new LinkedList<>();
    private final Set<Integer> pendingClicks = new HashSet<>();
    private long lastQueuedSendAt = 0L;
    private static final Map<String, String> COLOR_REPLACEMENTS = new HashMap<>();

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
            lastQueuedSendAt = 0L;
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
        solutionSlots.removeAll(pendingClicks);
        pendingClicks.retainAll(freshSolution);
    }

    private String processColorName(String name) {
        for (Map.Entry<String, String> entry : COLOR_REPLACEMENTS.entrySet()) {
            name = name.replace(entry.getKey(), entry.getValue());
        }
        return name;
    }

    @Override
    public void onSlotClick(int slotIndex, int button) {
        if (solutionSlots.contains(slotIndex)) {
            solutionSlots.remove(slotIndex);
            pendingClicks.add(slotIndex);
            int normalizedButton = button == 0 ? 0 : 1;
            if (shouldQueueClick()) {
                queuedClicks.addLast(new int[]{slotIndex, normalizedButton});
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
                net.minecraft.screen.sync.ComponentChangesHash.ComponentHasher hasher = component -> component.hashCode();
                net.minecraft.screen.sync.ItemStackHash cursorHash = net.minecraft.screen.sync.ItemStackHash.fromItemStack(mc.player.currentScreenHandler.getCursorStack(), hasher);
                net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket packet = 
                    new net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket(
                        handler.syncId,
                        handler.getRevision(), 
                        (short) slot, 
                        (byte) button, 
                        net.minecraft.screen.slot.SlotActionType.PICKUP, 
                        it.unimi.dsi.fastutil.ints.Int2ObjectMaps.emptyMap(),
                        cursorHash
                    );
                mc.getNetworkHandler().sendPacket(packet);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void render(RenderScreenEvent event) {
        // Don't render if not in terminal or no solution found yet
        if (!inTerminal || windowId == -1 || solutionSlots.isEmpty()) return;

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
}
