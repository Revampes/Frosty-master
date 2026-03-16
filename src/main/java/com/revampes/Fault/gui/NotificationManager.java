package com.revampes.Fault.gui;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;
import com.revampes.Fault.Revampes;
import com.revampes.Fault.events.impl.Render2DEvent;
import com.revampes.Fault.utility.Utils;

public class NotificationManager {
    public static final NotificationManager INSTANCE = new NotificationManager();
    
    public static class Notification {
        public String id;
        public String baseText;
        public long startTimestamp;
        public long duration;
        public float xPosPercent;
        public float yPosPercent;
        public boolean showTime;

        public Notification(String id, String baseText, long durationMs, float x, float y, boolean showTime) {
            this.id = id;
            this.baseText = baseText;
            this.duration = Math.max(1, durationMs);
            this.startTimestamp = Util.getMeasuringTimeMs();
            this.xPosPercent = x;
            this.yPosPercent = y;
            this.showTime = showTime;
        }
    }

    private final List<Notification> notifications = new CopyOnWriteArrayList<>();

    public NotificationManager() {
        Revampes.EVENT_BUS.subscribe(this);
    }
    
    public void show(String id, String text, long durationMs, float xPercent, float yPercent, boolean showTime) {
        for (Notification n : notifications) {
            if (n.id != null && n.id.equals(id)) {
                n.baseText = text;
                n.duration = durationMs;
                n.startTimestamp = Util.getMeasuringTimeMs();
                n.xPosPercent = xPercent;
                n.yPosPercent = yPercent;
                n.showTime = showTime;
                return;
            }
        }
        notifications.add(new Notification(id, text, durationMs, xPercent, yPercent, showTime));
    }

    public void show(String title, long durationMs, float xPercent, float yPercent) {
        show(title, title, durationMs, xPercent, yPercent, false);
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        long currentTime = Util.getMeasuringTimeMs();
        notifications.removeIf(n -> currentTime - n.startTimestamp >= n.duration);

        if (notifications.isEmpty()) return;

        Utils.scaledProjection();
        
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer font = mc.textRenderer;
        DrawContext context = event.drawContext;

        int index = 0;
        for (Notification n : notifications) {
            long timeElapsed = currentTime - n.startTimestamp;
            float progress = (float) timeElapsed / n.duration;

            // Smooth expanding appearance via Scale Ease Out
            float scale = 1.0f;
            long transition = 250;
            
            if (timeElapsed < transition) {
                float t = (float) timeElapsed / transition;
                scale = (float) (1.0 - Math.pow(1.0 - t, 3)); 
            } else if (timeElapsed > n.duration - transition) {
                float t = (float) (n.duration - timeElapsed) / transition;
                scale = (float) (1.0 - Math.pow(1.0 - t, 3)); 
            }

            String displayString = n.baseText;
            if (n.showTime) {
                long timeLeftMs = n.duration - timeElapsed;
                displayString = n.baseText + " (" + String.format("%.1f", timeLeftMs / 1000.0f) + "s)";
            }

            int padding = 8;
            int textWidth = font.getWidth(displayString);
            
            float rectWidth = (textWidth + padding * 2) * scale;
            float rectHeight = (font.fontHeight + padding * 2) * scale;

            float xCenter = (float) ((mc.getWindow().getScaledWidth() * n.xPosPercent) / 100.0);
            float yCenter = (float) ((mc.getWindow().getScaledHeight() * n.yPosPercent) / 100.0);

            float halfW = rectWidth / 2.0f;
            float halfH = rectHeight / 2.0f;

            // Apply stacking offset
            float stackOffset = index * (rectHeight + 4);
            
            // Origin coordinates based on center
            float startX = xCenter - halfW;
            float startY = yCenter - halfH + stackOffset;

            // Draw basic Drop Shadow with rounded edges (lower opacity)
            drawRoundedRect(context, startX + 3, startY + 3, rectWidth, rectHeight, 4 * scale, 0x40000000);

            // Background Black Box
            drawRoundedRect(context, startX, startY, rectWidth, rectHeight, 4 * scale, 0x90000000);

            // Shrinking Progress bar at the bottom
            float barHeight = 2 * scale;
            float barWidth = rectWidth * (1.0f - progress);
            float barStartY = startY + rectHeight - barHeight;
            
            // Draw standard fill for bar inside bounds
            context.fill((int)startX, (int)barStartY, (int)(startX + barWidth), (int)(barStartY + barHeight), 0xFFFFAA00);

            // Draw Text
            if (scale > 0.8) { 
                float alphaScale = (scale - 0.8f) * 5.0f; // 0 to 1
                int alpha = (int)(255 * alphaScale);
                int argb = (alpha << 24) | 0xFFFFFF;
                context.drawTextWithShadow(font, displayString, (int)(xCenter - textWidth / 2.0f), (int)(startY + padding), argb);
            }
            
            index++;
        }

        Utils.unscaledProjection();
    }
    
    private void drawRoundedRect(DrawContext context, float x, float y, float width, float height, float radius, int color) {
        int ix = (int)x;
        int iy = (int)y;
        int iw = (int)width;
        int ih = (int)height;
        int ir = (int)radius;
        if (ir < 1) ir = 1;
        
        // Main block
        context.fill(ix + ir, iy, ix + iw - ir, iy + ih, color);
        // Left
        context.fill(ix, iy + ir, ix + ir, iy + ih - ir, color);
        // Right
        context.fill(ix + iw - ir, iy + ir, ix + iw, iy + ih - ir, color);
        
        // Corners
        context.fill(ix + 1, iy + 1, ix + ir, iy + ir, color); // TL
        context.fill(ix + iw - ir, iy + 1, ix + iw - 1, iy + ir, color); // TR
        context.fill(ix + 1, iy + ih - ir, ix + ir, iy + ih - 1, color); // BL
        context.fill(ix + iw - ir, iy + ih - ir, ix + iw - 1, iy + ih - 1, color); // BR
    }
}
