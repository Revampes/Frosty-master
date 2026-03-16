package com.revampes.Fault.gui.container;

import com.revampes.Fault.gui.island.ContainerLevel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;

public class AutoPetNotificationContainer implements IContainer { 
    public static final AutoPetNotificationContainer INSTANCE = new AutoPetNotificationContainer();

    public String warningText;
    public long lastTriggeredTimestamp = 0;
    public final long DURATION = 3000;

    @Override 
    public boolean isActive() {
        return Util.getMeasuringTimeMs() - lastTriggeredTimestamp < DURATION;
    }

    @Override 
    public int getLevel() {
        return ContainerLevel.WARNING;
    }

    @Override
    public void prepareRender(float scale) {
    }

    @Override
    public float estimateHeight() {
        return 16F + MinecraftClient.getInstance().textRenderer.fontHeight;
    }

    @Override
    public float estimateWidth() {
        if (warningText == null) return 20F;
        return 20F + MinecraftClient.getInstance().textRenderer.getWidth(warningText);
    }

    @Override
    public void render(DrawContext context, float xCenter, float yTop, float rightUnused, float bottomUnused, float scale) {
        if (warningText == null) return;
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        
        long timeElapsed = Util.getMeasuringTimeMs() - lastTriggeredTimestamp;
        if (timeElapsed > DURATION) return;

        int padding = 6;
        int textWidth = font.getWidth(warningText);
        int rectWidth = textWidth + padding * 2;
        int rectHeight = font.fontHeight + padding * 2;
        int barHeight = 2; // the bottom line

        int startX = (int) (xCenter - rectWidth / 2.0f);
        int startY = (int) yTop;
        
        // Background black box
        context.fill(startX, startY, startX + rectWidth, startY + rectHeight, 0x80000000); 
        
        // Dropping progress bar at the bottom
        int barWidth = (int) (rectWidth * (1.0f - (float) timeElapsed / DURATION));
        context.fill(startX, startY + rectHeight - barHeight, startX + barWidth, startY + rectHeight, 0xFFFFAA00); // Gold/Orange bar
        
        // Text
        int color = 0xFFFFFFFF; // DEFAULT TEXT COLOR
        context.drawTextWithShadow(font, warningText, startX + padding, startY + padding, color);
    }
}