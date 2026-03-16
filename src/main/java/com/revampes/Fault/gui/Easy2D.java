package com.revampes.Fault.gui;

import com.revampes.Fault.mixin.AccessFont;
import net.minecraft.client.font.TextHandler;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.Collection;

public class Easy2D {
    public enum Alignment {
        LEFT, CENTER, RIGHT;
        public float calculate(float start, float end, float length) {
            if (this == LEFT) return start;
            if (this == RIGHT) return end - length;
            return start + (end - start - length) * 0.5F;
        }
    }

    public static class VanillaText {
        public String text;
        public Alignment align;
        public int color;
        public VanillaText(String text, Alignment align, int color) {
            this.text = text;
            this.align = align;
            this.color = color;
        }
    }

    private static DrawContext context;

    public static void configure(DrawContext newContext) {
        context = newContext;
    }

    public static void cleanup() {
        context = null;
    }

    public static void drawRoundRect(float left, float top, float right, float bottom,
                                     float radius, float shadow, int color, int shadowColor) {
        if (!(left < right && top < bottom)) { 
            return;
        }
        context.fill((int)left, (int)top, (int)right, (int)bottom, color);
    }

    public static final int TEXT_DEFAULT_COLOR = -1;

    public static void drawScreenText(TextRenderer font, String text, float x, float y, int color, boolean shadow) {
        Matrix3x2fStack pose = context.getMatrices();
        pose.pushMatrix();
        pose.translate(x, y);
        context.drawText(font, text, 0, 0, color, shadow);
        pose.popMatrix();
    }

    public static void drawScreenTextCentered(TextRenderer font, String text, float left, float top, float right, float bottom, int color, boolean shadow) {
        drawScreenText(font, text,
                (left + right - font.getWidth(text)) * 0.5F,
                (top + bottom - font.fontHeight) * 0.5F,
                color, shadow);
    }

    public static void drawScreenTextAligned(TextRenderer font, String text, float left, float top, float right, float bottom, int color, boolean shadow, Alignment alignment) {
        drawScreenText(font, text,
                alignment.calculate(left, right, font.getWidth(text)),
                alignment.calculate(top, bottom, font.fontHeight),
                color, shadow);
    }

    public static void drawScreenTextsCentered(TextRenderer font, float x, float y, int color, boolean shadow, Collection<String> lines) {
        if (lines.isEmpty()) return;
        float startY = y - font.fontHeight * lines.size() * 0.5F;
        Matrix3x2fStack pose = context.getMatrices();
        pose.pushMatrix();
        int i = 0;
        for (String line : lines) {
            pose.pushMatrix();
            pose.translate(x - font.getWidth(line) * 0.5F, startY + font.fontHeight * i++);
            context.drawText(font, line, 0, 0, color, shadow);
            pose.popMatrix();
        }
        pose.popMatrix();
    }

    public static void drawScreenTextElements(TextRenderer font, float startX, float endX, float centerY, boolean shadow, Collection<VanillaText> elements) {
        if (elements.isEmpty()) return;
        float startY = centerY - font.fontHeight * elements.size() * 0.5F;
        Matrix3x2fStack pose = context.getMatrices();
        pose.pushMatrix();
        int i = 0;
        for (VanillaText element : elements) {
            pose.pushMatrix();
            pose.translate(element.align.calculate(startX, endX, font.getWidth(element.text)), startY + font.fontHeight * i++);
            context.drawText(font, element.text, 0, 0, element.color, shadow);
            pose.popMatrix();
        }
        pose.popMatrix();
    }

    public static void drawItem(ItemStack itemStack, float x, float y) {
        Matrix3x2fStack pose = context.getMatrices();
        pose.pushMatrix();
        pose.translate(x, y);
        context.drawItemWithoutEntity(itemStack, 0, 0);
        pose.popMatrix();
    }
}
