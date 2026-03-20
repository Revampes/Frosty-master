package com.revampes.Fault.utility.terminals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;

import java.awt.*;

public class TerminalRenderUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static int ensureOpaque(int color) {
        return (color >>> 24) == 0 ? (0xFF000000 | color) : color;
    }

    public static void drawSlotHighlight(DrawContext context, int slotIndex, float scale, int offsetX, int offsetY, int color) {
        int slotX = (slotIndex % 9) * 18;
        int slotY = (slotIndex / 9) * 18;

        int x = offsetX + (int)(slotX * scale);
        int y = offsetY + (int)(slotY * scale);
        int width = (int)(16 * scale);
        int height = (int)(16 * scale);

        context.fill(x, y, x + width, y + height, color);
    }

    public static void drawScreenRect(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y2, color);
    }

    public static void drawText(DrawContext context, String text, int x, int y, int color) {
        context.drawTextWithShadow(mc.textRenderer, text, x, y, ensureOpaque(color));
    }

    public static void drawCenteredText(DrawContext context, String text, int x, int y, int color) {
        int width = mc.textRenderer.getWidth(text);
        context.drawTextWithShadow(mc.textRenderer, text, x - width / 2, y, ensureOpaque(color));
    }

    public static float getScale(int screenWidth, int screenHeight, int gridWidth, int gridHeight, float baseScale) {
        return baseScale;
    }

    public static int getGridOffsetX(int screenWidth, int gridWidth, float scale, int offset) {
        return (int)(screenWidth / 2 - (gridWidth / 2) * scale + offset * scale);
    }

    public static int getGridOffsetY(int screenHeight, int gridHeight, float scale, int offset) {
        return (int)(screenHeight / 2 - (gridHeight / 2) * scale + offset * scale);
    }

    public static boolean isValidSlot(int slotIndex, int windowSize) {
        return slotIndex >= 0 && slotIndex < windowSize;
    }

    public static int[] getAllowedSlots(String terminalType) {
        return switch (terminalType) {
            case "colors", "startswith" -> new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
            case "numbers" -> new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
            case "redgreen" -> new int[]{11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33};
            case "rubix" -> new int[]{12, 13, 14, 21, 22, 23, 30, 31, 32};
            default -> new int[]{};
        };
    }

    public static Color getARGB(int color) {
        return new Color(color, true);
    }

    /**
     * Calculate which slot was clicked based on mouse position and terminal overlay position
     * @return slot index (0-80) or -1 if not clicked on terminal overlay
     */
    public static int getClickedSlot(int mouseX, int mouseY, int screenWidth, int screenHeight, 
                                     int windowSize, float scale, int offsetX, int offsetY) {
        int width = (int)(9 * 18 * scale);
        int height = (int)(windowSize / 9 * 18 * scale);
        
        // Calculate overlay bounds
        int overlayX = screenWidth / 2 - width / 2 + offsetX;
        int overlayY = screenHeight / 2 - height / 2 + offsetY;
        
        // Check if click is within overlay
        if (mouseX < overlayX || mouseX >= overlayX + width || 
            mouseY < overlayY || mouseY >= overlayY + height) {
            return -1;
        }
        
        // Calculate which slot was clicked
        int relX = mouseX - overlayX;
        int relY = mouseY - overlayY;
        int slotX = (int)(relX / (18 * scale));
        int slotY = (int)(relY / (18 * scale));
        
        if (slotX < 0 || slotX >= 9 || slotY < 0 || slotY >= (windowSize / 9)) {
            return -1;
        }
        
        return slotX + slotY * 9;
    }
}
