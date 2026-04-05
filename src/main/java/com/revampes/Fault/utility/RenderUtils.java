package com.revampes.Fault.utility;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static com.revampes.Fault.Revampes.mc;

public class RenderUtils {
    public static final Matrix4f projection = new Matrix4f();

    public static void rect(MatrixStack stack, float x1, float y1, float x2, float y2, int color) {
        rectFilled(stack, x1, y1, x2, y2, color);
    }

    public static void rect(MatrixStack stack, float x1, float y1, float x2, float y2, int color, float width) {
        drawHorizontalLine(stack, x1, x2, y1, color, width);
        drawVerticalLine(stack, x2, y1, y2, color, width);
        drawHorizontalLine(stack, x1, x2, y2, color, width);
        drawVerticalLine(stack, x1, y1, y2, color, width);
    }

    public static void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);

        context.fill(x, y + height - 1, x + width, y + height, color);

        context.fill(x, y, x + 1, y + height, color);

        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    protected static void drawHorizontalLine(MatrixStack matrices, float x1, float x2, float y, int color) {
        if (x2 < x1) {
            float i = x1;
            x1 = x2;
            x2 = i;
        }

        rectFilled(matrices, x1, y, x2 + 1, y + 1, color);
    }

    protected static void drawVerticalLine(MatrixStack matrices, float x, float y1, float y2, int color) {
        if (y2 < y1) {
            float i = y1;
            y1 = y2;
            y2 = i;
        }

        rectFilled(matrices, x, y1 + 1, x + 1, y2, color);
    }

    protected static void drawHorizontalLine(MatrixStack matrices, float x1, float x2, float y, int color, float width) {
        if (x2 < x1) {
            float i = x1;
            x1 = x2;
            x2 = i;
        }

        rectFilled(matrices, x1, y, x2 + width, y + width, color);
    }

    protected static void drawVerticalLine(MatrixStack matrices, float x, float y1, float y2, int color, float width) {
        if (y2 < y1) {
            float i = y1;
            y1 = y2;
            y2 = i;
        }

        rectFilled(matrices, x, y1 + width, x + width, y2, color);
    }

    public static void rectFilled(MatrixStack matrix, float x1, float y1, float x2, float y2, int color) {
        float i;
        if (x1 < x2) {
            i = x1;
            x1 = x2;
            x2 = i;
        }

        if (y1 < y2) {
            i = y1;
            y1 = y2;
            y2 = i;
        }

        float f = (float) (color >> 24 & 255) / 255.0F;
        float g = (float) (color >> 16 & 255) / 255.0F;
        float h = (float) (color >> 8 & 255) / 255.0F;
        float j = (float) (color & 255) / 255.0F;

        BufferBuilder bufferBuilder = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x1, y2, 0.0F).color(g, h, j, f);
        bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x2, y2, 0.0F).color(g, h, j, f);
        bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x2, y1, 0.0F).color(g, h, j, f);
        bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x1, y1, 0.0F).color(g, h, j, f);

        Layers.getGlobalQuads().draw(bufferBuilder.end());
    }

    // 3d
    public static void drawBoxFilled(MatrixStack stack, Box box, Color c) {
        beginThroughWallRender();
        try {
        float minX = (float) (box.minX - mc.getEntityRenderDispatcher().camera.getCameraPos().x);
        float minY = (float) (box.minY - mc.getEntityRenderDispatcher().camera.getCameraPos().y);
        float minZ = (float) (box.minZ - mc.getEntityRenderDispatcher().camera.getCameraPos().z);
        float maxX = (float) (box.maxX - mc.getEntityRenderDispatcher().camera.getCameraPos().x);
        float maxY = (float) (box.maxY - mc.getEntityRenderDispatcher().camera.getCameraPos().y);
        float maxZ = (float) (box.maxZ - mc.getEntityRenderDispatcher().camera.getCameraPos().z);

        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vertexConsumer = immediate.getBuffer(RenderLayers.debugFilledBox());
        MatrixStack.Entry entry = stack.peek();

        vertexConsumer.vertex(entry, minX, minY, minZ).color(c.getRGB());
        vertexConsumer.vertex(entry, maxX, minY, minZ).color(c.getRGB());
        vertexConsumer.vertex(entry, maxX, minY, maxZ).color(c.getRGB());
        vertexConsumer.vertex(entry, minX, minY, maxZ).color(c.getRGB());

        vertexConsumer.vertex(entry, minX, maxY, minZ).color(c.getRGB());
        vertexConsumer.vertex(entry, minX, maxY, maxZ).color(c.getRGB());
        vertexConsumer.vertex(entry, maxX, maxY, maxZ).color(c.getRGB());
        vertexConsumer.vertex(entry, maxX, maxY, minZ).color(c.getRGB());

        vertexConsumer.vertex(entry, minX, minY, minZ).color(c.getRGB());
        vertexConsumer.vertex(entry, minX, maxY, minZ).color(c.getRGB());
        vertexConsumer.vertex(entry, maxX, maxY, minZ).color(c.getRGB());
        vertexConsumer.vertex(entry, maxX, minY, minZ).color(c.getRGB());

        vertexConsumer.vertex(entry, maxX, minY, minZ).color(c.getRGB());
        vertexConsumer.vertex(entry, maxX, maxY, minZ).color(c.getRGB());
        vertexConsumer.vertex(entry, maxX, maxY, maxZ).color(c.getRGB());
        vertexConsumer.vertex(entry, maxX, minY, maxZ).color(c.getRGB());

        vertexConsumer.vertex(entry, minX, minY, maxZ).color(c.getRGB());
        vertexConsumer.vertex(entry, maxX, minY, maxZ).color(c.getRGB());
        vertexConsumer.vertex(entry, maxX, maxY, maxZ).color(c.getRGB());
        vertexConsumer.vertex(entry, minX, maxY, maxZ).color(c.getRGB());

        vertexConsumer.vertex(entry, minX, minY, minZ).color(c.getRGB());
        vertexConsumer.vertex(entry, minX, minY, maxZ).color(c.getRGB());
        vertexConsumer.vertex(entry, minX, maxY, maxZ).color(c.getRGB());
        vertexConsumer.vertex(entry, minX, maxY, minZ).color(c.getRGB());

        immediate.draw();
        } finally {
            endThroughWallRender();
        }
    }

    public static void drawBoxFilled(MatrixStack stack, Vec3d vec, Color c) {
        drawBoxFilled(stack, Box.from(vec), c);
    }

    public static void drawBoxFilled(MatrixStack stack, BlockPos bp, Color c) {
        drawBoxFilled(stack, new Box(bp), c);
    }

    public static void drawBox(MatrixStack stack, Box box, Color c, double lineWidth) {
        beginThroughWallRender();
        try {
        float minX = (float) (box.minX - mc.getEntityRenderDispatcher().camera.getCameraPos().x);
        float minY = (float) (box.minY - mc.getEntityRenderDispatcher().camera.getCameraPos().y);
        float minZ = (float) (box.minZ - mc.getEntityRenderDispatcher().camera.getCameraPos().z);
        float maxX = (float) (box.maxX - mc.getEntityRenderDispatcher().camera.getCameraPos().x);
        float maxY = (float) (box.maxY - mc.getEntityRenderDispatcher().camera.getCameraPos().y);
        float maxZ = (float) (box.maxZ - mc.getEntityRenderDispatcher().camera.getCameraPos().z);

        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vertexConsumer = immediate.getBuffer(RenderLayers.linesTranslucent());
        MatrixStack.Entry entry = stack.peek();
        
        float r = c.getRed() / 255f;
        float g = c.getGreen() / 255f;
        float b = c.getBlue() / 255f;
        float a = c.getAlpha() / 255f;
        
        // Draw box outline - 12 edges
        // Bottom face
        drawLine(vertexConsumer, entry, minX, minY, minZ, maxX, minY, minZ, r, g, b, a, (float) lineWidth);
        drawLine(vertexConsumer, entry, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a, (float) lineWidth);
        drawLine(vertexConsumer, entry, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a, (float) lineWidth);
        drawLine(vertexConsumer, entry, minX, minY, maxZ, minX, minY, minZ, r, g, b, a, (float) lineWidth);
        
        // Top face
        drawLine(vertexConsumer, entry, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a, (float) lineWidth);
        drawLine(vertexConsumer, entry, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a, (float) lineWidth);
        drawLine(vertexConsumer, entry, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a, (float) lineWidth);
        drawLine(vertexConsumer, entry, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a, (float) lineWidth);
        
        // Vertical edges
        drawLine(vertexConsumer, entry, minX, minY, minZ, minX, maxY, minZ, r, g, b, a, (float) lineWidth);
        drawLine(vertexConsumer, entry, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a, (float) lineWidth);
        drawLine(vertexConsumer, entry, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a, (float) lineWidth);
        drawLine(vertexConsumer, entry, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a, (float) lineWidth);

        immediate.draw();
        } finally {
            endThroughWallRender();
        }
    }
    
    private static void drawLine(VertexConsumer builder, MatrixStack.Entry entry, 
            float x1, float y1, float z1, float x2, float y2, float z2, 
            float r, float g, float b, float a, float lineWidth) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 0) {
            dx /= length;
            dy /= length;
            dz /= length;
        }
        builder.vertex(entry, x1, y1, z1).color(r, g, b, a).normal(entry, dx, dy, dz).lineWidth(lineWidth);
        builder.vertex(entry, x2, y2, z2).color(r, g, b, a).normal(entry, dx, dy, dz).lineWidth(lineWidth);
    }

    public static void drawBox(MatrixStack stack, Vec3d vec, Color c, double lineWidth) {
        drawBox(stack, Box.from(vec), c, lineWidth);
    }

    public static void drawBox(MatrixStack stack, BlockPos bp, Color c, double lineWidth) {
        drawBox(stack, new Box(bp), c, lineWidth);
    }

    public static void drawLine(MatrixStack stack, Vec3d start, Vec3d end, Color c, double lineWidth) {
        beginThroughWallRender();
        try {
        float minX = (float) (start.x - mc.getEntityRenderDispatcher().camera.getCameraPos().x);
        float minY = (float) (start.y - mc.getEntityRenderDispatcher().camera.getCameraPos().y);
        float minZ = (float) (start.z - mc.getEntityRenderDispatcher().camera.getCameraPos().z);
        float maxX = (float) (end.x - mc.getEntityRenderDispatcher().camera.getCameraPos().x);
        float maxY = (float) (end.y - mc.getEntityRenderDispatcher().camera.getCameraPos().y);
        float maxZ = (float) (end.z - mc.getEntityRenderDispatcher().camera.getCameraPos().z);

        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer bufferBuilder = immediate.getBuffer(RenderLayers.linesTranslucent());

        float dx = maxX - minX;
        float dy = maxY - minY;
        float dz = maxZ - minZ;
        float dist = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= dist;
        dy /= dist;
        dz /= dist;

        MatrixStack.Entry entry = stack.peek();

        bufferBuilder.vertex(entry, minX, minY, minZ)
            .color(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f)
            .normal(entry, dx, dy, dz)
            .lineWidth((float) lineWidth);

        bufferBuilder.vertex(entry, maxX, maxY, maxZ)
            .color(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f)
            .normal(entry, dx, dy, dz)
            .lineWidth((float) lineWidth);

        immediate.draw();
        } finally {
            endThroughWallRender();
        }
    }

    private static void beginThroughWallRender() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    private static void endThroughWallRender() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    public static void drawHighlight(MatrixStack stack, Box box, Color fillColor, Color outlineColor, boolean throughWalls, String mode) {
        if (throughWalls) {
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        }

        if ("Filled".equals(mode) || "Filled Outline".equals(mode)) {
            drawBoxFilled(stack, box, fillColor);
        }
        if ("Outline".equals(mode) || "Filled Outline".equals(mode)) {
            drawBox(stack, box, outlineColor, 2.0);
        }

        if (throughWalls) {
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        }
    }

    public static void outlineEntity(MatrixStack matrices, Entity entity, Color color, float lineWidth, float partialTicks) {
        Box box = entity.getBoundingBox();

        double offsetX = MathHelper.lerp(partialTicks, entity.lastRenderX, entity.getX()) - entity.getX();
        double offsetY = MathHelper.lerp(partialTicks, entity.lastRenderY, entity.getY()) - entity.getY();
        double offsetZ = MathHelper.lerp(partialTicks, entity.lastRenderZ, entity.getZ()) - entity.getZ();

        Box offsetBox = box.offset(offsetX, offsetY, offsetZ);

        drawBox(matrices, offsetBox, color, lineWidth);
    }

    public static void outlineEntity(MatrixStack matrices, Entity entity, Color color, float lineWidth) {
        outlineEntity(matrices, entity, color, lineWidth, 1.0f);
    }

    public static void drawBlockFilled(MatrixStack stack, BlockPos pos, Color color, float alpha) {
        Box box = new Box(pos);
        Color filledColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(alpha * 255));
        drawBoxFilled(stack, box, filledColor);
    }

    public static void drawBlockOutline(MatrixStack stack, BlockPos pos, Color color, float lineWidth) {
        Box box = new Box(pos);
        drawBox(stack, box, color, lineWidth);
    }

    public static void drawFullBlock(MatrixStack stack, BlockPos pos, Color color, float lineWidth, float fillAlpha) {
        drawBlockFilled(stack, pos, color, fillAlpha);
        drawBlockOutline(stack, pos, color, lineWidth);
    }

    public static void drawCustomBeacon(MatrixStack stack, String text, BlockPos pos, Color color) {
        drawBox(stack, pos, color, 2.0);
        drawBoxFilled(stack, pos, new Color(color.getRed(), color.getGreen(), color.getBlue(), 100));
        
        // Draw a tall beacon beam
        Box beamBox = new Box(pos.getX() + 0.3, pos.getY(), pos.getZ() + 0.3, 
                              pos.getX() + 0.7, pos.getY() + 300, pos.getZ() + 0.7);
        drawBoxFilled(stack, beamBox, new Color(color.getRed(), color.getGreen(), color.getBlue(), 50));
        
        // Ensure text renders slightly above the pos
        draw3DText(stack, text, new Vec3d(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5), 1.0f);
    }

    public static void draw3DText(MatrixStack stack, String text, Vec3d pos, float scale) {
        draw3DText(stack, text, pos, scale, 0xFFFFFFFF);
    }

    public static void draw3DText(MatrixStack stack, String text, Vec3d pos, float scale, int color) {
        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null || text == null || text.isEmpty()) return;
        
        double x = pos.x - camera.getCameraPos().x;
        double y = pos.y - camera.getCameraPos().y;
        double z = pos.z - camera.getCameraPos().z;

        stack.push();
        beginThroughWallRender();
        try {
            stack.translate(x, y, z);

            if (mc.player != null) {
                float yaw = mc.player.getYaw();
                float pitch = mc.player.getPitch();
                stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
                stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
            } else {
                stack.multiply(camera.getRotation());
            }

            stack.scale(-0.025f * scale, -0.025f * scale, 0.025f * scale);

            Matrix4f positionMatrix = stack.peek().getPositionMatrix();
            TextRenderer textRenderer = mc.textRenderer;

            float textWidth = (float) (-textRenderer.getWidth(text) / 2);
            VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

            textRenderer.draw(
                text,
                textWidth,
                0f,
                color,
                false,
                positionMatrix,
                immediate,
                TextRenderer.TextLayerType.SEE_THROUGH,
                0,
                LightmapTextureManager.MAX_LIGHT_COORDINATE
            );
            immediate.draw();
        } finally {
            endThroughWallRender();
            stack.pop();
        }
    }

    public static void draw2DLine(DrawContext context, float x1, float y1, float x2, float y2, int color, int thickness) {
        if ((color >>> 24) == 0) {
            color |= 0xFF000000;
        }

        float dx = x2 - x1;
        float dy = y2 - y1;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        float half = Math.max(2.0f, thickness) / 2.0f;

        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float x = x1 + dx * t;
            float y = y1 + dy * t;

            int minX = Math.round(x - half);
            int minY = Math.round(y - half);
            int maxX = Math.round(x + half);
            int maxY = Math.round(y + half);

            context.fill(Pipelines.GLOBAL_QUADS_PIPELINE, minX, minY, maxX + 1, maxY + 1, color);
        }
    }
}
