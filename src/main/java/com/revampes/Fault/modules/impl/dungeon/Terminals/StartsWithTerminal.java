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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StartsWithTerminal extends AbstractTerminal {
    private String startingLetter = null;
    private final Deque<int[]> queuedClicks = new LinkedList<>();
    private final Set<Integer> pendingClicks = new HashSet<>();
    private long lastQueuedSendAt = 0L;

    @Override
    public String getTerminalName() {
        return "Starts With";
    }

    @Override
    public boolean matches(String windowTitle) {
        return windowTitle.matches("^What starts with: '(\\w)\'\\?$");
    }

    @Override
    public void onWindowOpen(String title, int windowId, int slotCount) {
        Pattern pattern = Pattern.compile("^What starts with: '(\\w)\\'\\?$");
        Matcher matcher = pattern.matcher(title);

        if (matcher.find()) {
            this.windowId = windowId;
            startingLetter = matcher.group(1).toLowerCase();
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
    }

    @Override
    public void solve() {
        solutionSlots.clear();
        int[] allowedSlots = TerminalRenderUtils.getAllowedSlots("startswith");

        for (SlotData slot : slots) {
            if (slot == null) continue;
            if (!slotIsAllowed(slot.slot, allowedSlots)) continue;
            if (slot.enchanted) continue;

            String itemName = formatItemName(slot.name);
            if (itemName.startsWith(startingLetter)) {
                solutionSlots.add(slot.slot);
            }
        }

        Set<Integer> freshSolution = new HashSet<>(solutionSlots);
        solutionSlots.removeAll(pendingClicks);
        pendingClicks.retainAll(freshSolution);
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
        } catch (Exception e) {
            System.out.println("[StartsWith] Error sending click packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void render(RenderScreenEvent event) {
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
        String title = "§8[§bFault Terminal§8] §aStarts With '" + startingLetter + "'";
        TerminalRenderUtils.drawText(event.context, title, offsetX, offsetY, 0xFFFFFF);

        // Draw solution slots
        for (int slot : solutionSlots) {
            TerminalRenderUtils.drawSlotHighlight(event.context, slot, scale, offsetX, offsetY + titleHeight, 0xFFFFAA00);
        }
    }
}
