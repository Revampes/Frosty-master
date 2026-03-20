package com.revampes.Fault.modules.impl.dungeon.Terminals;

import com.revampes.Fault.events.impl.RenderScreenEvent;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.utility.terminals.SlotData;
import com.revampes.Fault.utility.terminals.TerminalRenderUtils;

import net.minecraft.item.ItemStack;

public class MelodyTerminal extends AbstractTerminal {
    private int correctColumn = -1;
    private int melodyButtonRow = -1;
    private int melodyCurrentColumn = -1;
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
            correctColumn = -1;
            melodyButtonRow = -1;
            melodyCurrentColumn = -1;
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

        if (isCurrentMelodyIndicator(data)) {
            int correct = -1;
            for (SlotData slot : slots) {
                if (slot != null && isCorrectMelodyColumn(slot)) {
                    correct = slot.slot - 1;
                    break;
                }
            }

            correctColumn = correct;
            melodyButtonRow = slotIndex / 9 - 1;
            melodyCurrentColumn = slotIndex % 9 - 1;
        }
    }

    private boolean isCurrentMelodyIndicator(SlotData slot) {
        if (slot == null) return false;
        String type = slot.itemType == null ? "" : slot.itemType;
        // Legacy lime pane (meta 5) or modern lime pane id.
        return (slot.id == 160 && slot.meta == 5)
            || type.contains("lime_stained_glass_pane");
    }

    private boolean isCorrectMelodyColumn(SlotData slot) {
        if (slot == null) return false;
        String type = slot.itemType == null ? "" : slot.itemType;
        // Legacy green pane (meta 2) or modern green/magenta pane ids.
        // Newer server variants use magenta panes as boundary markers for the hit column.
        return (slot.id == 160 && slot.meta == 2)
            || type.contains("green_stained_glass_pane")
            || type.contains("magenta_stained_glass_pane");
    }

    private Integer getSlotBaseColor(SlotData slot) {
        if (slot == null || slot.itemType == null || slot.itemType.isEmpty()) return null;

        String type = slot.itemType;
        if (type.contains("black_stained_glass_pane")) return 0xCC111111;
        if (type.contains("white_stained_glass_pane")) return 0xCCEEEEEE;
        if (type.contains("magenta_stained_glass_pane")) return 0xCCB03BE0;
        if (type.contains("red_stained_glass_pane")) return 0xCCB12D2D;
        if (type.contains("lime_stained_glass_pane")) return 0xCC44C95A;
        if (type.contains("red_terracotta")) return 0xCC9B3C2F;
        if (type.contains("lime_terracotta")) return 0xCC6EA736;
        return null;
    }

    @Override
    public void solve() {
        // Melody is render-driven and does not use solver state.
    }

    @Override
    public void onSlotClick(int slotIndex, int button) {
        // Melody has direct button clicks only; no queue/prediction.
        for (int buttonSlot : MELODY_BUTTONS) {
            if (slotIndex == buttonSlot) {
                sendClickPacket(slotIndex, 0);
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
            // Ignore send failures to avoid noisy logs while the container is changing.
        }
    }

    @Override
    public void render(RenderScreenEvent event) {
        if (!inTerminal || windowId == -1) return;

        int screenWidth = event.context.getScaledWindowWidth();
        int screenHeight = event.context.getScaledWindowHeight();
        float scale = ModuleManager.terminalManager != null ? ModuleManager.terminalManager.getOverlayScale() : 1.0f;

        int width = (int)(9 * 18 * scale);
        int height = (int)(windowSize / 9 * 18 * scale);

        int offsetX = screenWidth / 2 - width / 2 + (int) Math.round((ModuleManager.terminalManager != null ? ModuleManager.terminalManager.getOverlayOffsetX() : 0) * scale);
        int offsetY = screenHeight / 2 - height / 2 + (int) Math.round((ModuleManager.terminalManager != null ? ModuleManager.terminalManager.getOverlayOffsetY() : 0) * scale);

        // Draw background
        event.context.fill(offsetX - 2, offsetY - 2, offsetX + width + 4, offsetY + height + 4, 0xFF000000);

        if (correctColumn >= 0) {
            // Draw column highlight
            int colX = offsetX + (int)((correctColumn + 1) * 18 * scale);
            int colY = offsetY + (int)(18 * scale);
            int colWidth = (int)(16 * scale);
            int colHeight = (int)(70 * scale);
            event.context.fill(colX, colY, colX + colWidth, colY + colHeight, 0x6600FF00);
        }

        // Draw title
        String title = "§8[§bFault Terminal§8] §aMelody";
        int titleY = offsetY - (int) Math.round(10 * scale);
        TerminalRenderUtils.drawText(event.context, title, offsetX, titleY, 0xFFFFFF);

        int buttonSlot = melodyButtonRow >= 0 ? melodyButtonRow * 9 + 16 : -1;
        int currentSlot = (melodyButtonRow >= 0 && melodyCurrentColumn >= 0) ? melodyButtonRow * 9 + 10 + melodyCurrentColumn : -1;

        for (int i = 0; i < windowSize; i++) {
            int slotX = offsetX + (int)((i % 9) * 18 * scale);
            int slotY = offsetY + (int)((i / 9) * 18 * scale);
            int boxW = (int)(16 * scale);
            int boxH = (int)(16 * scale);

            SlotData slotData = i < slots.size() ? slots.get(i) : null;
            Integer baseColor = getSlotBaseColor(slotData);
            if (baseColor != null) {
                event.context.fill(slotX, slotY, slotX + boxW, slotY + boxH, baseColor);
            }

            if (i == buttonSlot) {
                event.context.fill(slotX, slotY, slotX + boxW, slotY + boxH, 0xAA00FF00);
            } else {
                boolean isMelodyButton = false;
                for (int melodyButton : MELODY_BUTTONS) {
                    if (i == melodyButton) {
                        isMelodyButton = true;
                        break;
                    }
                }
                if (isMelodyButton) {
                    event.context.fill(slotX, slotY, slotX + boxW, slotY + boxH, 0xAAFF0000);
                } else if (i == currentSlot) {
                    event.context.fill(slotX, slotY, slotX + boxW, slotY + boxH, 0xAAFFFF00);
                }
            }
        }
    }
}
