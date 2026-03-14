package com.revampes.AfterTimeFault.gui.container;

import com.revampes.AfterTimeFault.gui.island.ContainerLevel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;

public class RagnarockTimerContainer implements IContainer {
    public static final RagnarockTimerContainer INSTANCE = new RagnarockTimerContainer();

    public float gainedStrength;
    public long castStartTimestamp = 0;
    public long buffStartTimestamp = 0;
    public boolean showCooldown = false;

    public final long CAST_DURATION = 3000;
    public final long BUFF_DURATION = 10000;
    public final long COOLDOWN_DURATION = 20000;

    @Override
    public boolean isActive() {
        long current = Util.getMeasuringTimeMs();
        boolean casting = (current - castStartTimestamp < CAST_DURATION) && buffStartTimestamp == 0 && castStartTimestamp != 0;
        boolean buffing = (current - buffStartTimestamp < BUFF_DURATION) && buffStartTimestamp != 0;
        boolean cooling = showCooldown && (current - castStartTimestamp < COOLDOWN_DURATION) && castStartTimestamp != 0;
        return casting || buffing || cooling;
    }

    @Override
    public int getLevel() {
        return ContainerLevel.COMMON;
    }

    @Override
    public void prepareRender(float scale) {
    }

    @Override
    public float estimateHeight() {
        return 32F + MinecraftClient.getInstance().textRenderer.fontHeight * 2;
    }

    @Override
    public float estimateWidth() {
        return 150F;
    }

    @Override
    public void render(DrawContext context, float xCenter, float yTop, float rightUnused, float bottomUnused, float scale) {
        long current = Util.getMeasuringTimeMs();
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int padding = 6;
        int barHeight = 2;

        boolean isCasting = (current - castStartTimestamp < CAST_DURATION) && buffStartTimestamp == 0 && castStartTimestamp != 0;
        boolean isBuffing = (current - buffStartTimestamp < BUFF_DURATION) && buffStartTimestamp != 0;
        boolean isCooling = showCooldown && (current - castStartTimestamp < COOLDOWN_DURATION) && castStartTimestamp != 0;

        int currentY = (int) yTop;

        // Draw Casting or Buffing Box
        if (isCasting || isBuffing) {
            String text = "";
            float progress = 0f;
            int barColor = 0;

            if (isCasting) {
                float remain = 3.0f - ((float)(current - castStartTimestamp) / 1000.0f);
                if (remain < 0) remain = 0;
                text = String.format("Casting Ragnarok... %.1fs", remain);
                progress = 1.0f - ((float)(current - castStartTimestamp) / (float)CAST_DURATION);
                barColor = 0xFFFFAA00; // Orange
            } else {
                float remainBuff = 10.0f - ((float)(current - buffStartTimestamp) / 1000.0f);
                if (remainBuff < 0) remainBuff = 0;
                text = String.format("Ragnarok: +%.0f Strength (%.1fs)", gainedStrength, remainBuff);
                progress = 1.0f - ((float)(current - buffStartTimestamp) / (float)BUFF_DURATION);
                barColor = 0xFFFF5555; // Red
            }

            int textWidth = font.getWidth(text);
            int rectWidth = textWidth + padding * 2;
            int rectHeight = font.fontHeight + padding * 2;

            int startX = (int) (xCenter - rectWidth / 2.0f);
            
            context.fill(startX, currentY, startX + rectWidth, currentY + rectHeight, 0x80000000); 
            
            int barWidth = (int) (rectWidth * progress);
            context.fill(startX, currentY + rectHeight - barHeight, startX + barWidth, currentY + rectHeight, barColor);
            
            context.drawTextWithShadow(font, text, startX + padding, currentY + padding, 0xFFFFFFFF);
            
            currentY += rectHeight + 4; // Shift down for next box
        }

        // Draw Cooldown Box
        if (isCooling) {
            float remain = 20.0f - ((float)(current - castStartTimestamp) / 1000.0f);
            if (remain < 0) remain = 0;
            String text = "Ragnarok CD: " + String.format("%.1fs", remain);

            int textWidth = font.getWidth(text);
            int rectWidth = textWidth + padding * 2;
            int rectHeight = font.fontHeight + padding * 2;

            int startX = (int) (xCenter - rectWidth / 2.0f);
            
            context.fill(startX, currentY, startX + rectWidth, currentY + rectHeight, 0x80000000); 

            float progress = 1.0f - ((float)(current - castStartTimestamp) / (float)COOLDOWN_DURATION);
            int barWidth = (int) (rectWidth * progress);
            context.fill(startX, currentY + rectHeight - barHeight, startX + barWidth, currentY + rectHeight, 0xFF5555FF); // Blue for cooldown
            
            context.drawTextWithShadow(font, text, startX + padding, currentY + padding, 0xFFFFFFFF);
        }
    }
}