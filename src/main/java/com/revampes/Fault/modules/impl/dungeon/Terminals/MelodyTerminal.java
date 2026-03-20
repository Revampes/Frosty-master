package com.revampes.Fault.modules.impl.dungeon.Terminals;

import com.revampes.Fault.events.impl.RenderScreenEvent;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.utility.terminals.SlotData;
import com.revampes.Fault.utility.terminals.TerminalRenderUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.sync.ComponentChangesHash;
import net.minecraft.screen.sync.ItemStackHash;

public class MelodyTerminal extends AbstractTerminal {
    private int correctColumn = -1;
    private static final int[] MELODY_BUTTONS = {16, 25, 34, 43};

    @Override
    public String getTerminalName() {
        return "Melody";
    }

    @Override
    public boolean matches(String windowTitle) {
        return windowTitle.equals("Click the button on time!");
    }

    @Override
    public void onWindowOpen(String title, int windowId, int slotCount) {
        if (matches(title)) {
            this.windowId = windowId;
            inTerminal = true;
            openedAt = System.currentTimeMillis();
            slots.clear();
            solutionSlots.clear();
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
    }

    @Override
    public void solve() {
        // Melody terminal doesn't need solving - it's time-based
    }

    @Override
    public void onSlotClick(int slotIndex, int button) {
        // Check if it's one of the melody buttons
        for (int buttonSlot : MELODY_BUTTONS) {
            if (slotIndex == buttonSlot) {
                sendClickPacket(slotIndex, button);
                solutionSlots.add(slotIndex);
                break;
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
            System.out.println("[Melody] Error sending click packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void render(RenderScreenEvent event) {
        if (!inTerminal || windowId == -1 || solutionSlots.isEmpty()) return;

        int screenWidth = event.context.getScaledWindowWidth();
        int screenHeight = event.context.getScaledWindowHeight();
        float scale = ModuleManager.terminalManager != null ? ModuleManager.terminalManager.getOverlayScale() : 1.0f;

        int width = (int)(9 * 18 * scale);
        int height = (int)(windowSize / 9 * 18 * scale);

        int offsetX = screenWidth / 2 - width / 2 + (int) Math.round((ModuleManager.terminalManager != null ? ModuleManager.terminalManager.getOverlayOffsetX() : 0) * scale);
        int offsetY = screenHeight / 2 - height / 2 + (int) Math.round((ModuleManager.terminalManager != null ? ModuleManager.terminalManager.getOverlayOffsetY() : 0) * scale);

        if (correctColumn >= 0) {
            // Draw column highlight
            int colX = offsetX + (correctColumn + 1) * 18;
            event.context.fill(colX, offsetY + 18, colX + 16, offsetY + 18 + 70, 0xFF00FF00);
        }

        // Draw background
        event.context.fill(offsetX - 2, offsetY - 2, offsetX + width + 4, offsetY + height + 4, 0xFF000000);

        // Draw title
        String title = "§8[§bSA Terminal§8] §aMelody";
        TerminalRenderUtils.drawText(event.context, title, offsetX, offsetY, 0xFFFFFF);
    }
}
