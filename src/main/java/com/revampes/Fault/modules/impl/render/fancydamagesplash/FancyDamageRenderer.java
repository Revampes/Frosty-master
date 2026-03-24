package com.revampes.Fault.modules.impl.render.fancydamagesplash;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import com.revampes.Fault.utility.ColorGradientUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FancyDamageRenderer {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final Random RANDOM = new Random();
    private static final List<DamageNumber> DAMAGE_NUMBERS = new ArrayList<>();

    private static final Pattern DAMAGE_TEXT_PATTERN = Pattern.compile("([✧✯]?)([\\d,]+)[✧✯]?([❤+⚔☄♞]?)");
    private static final Pattern COMPACT_SUFFIX_PATTERN = Pattern.compile(".*[kKmMbBtTqQ]$");

    private static final long ANIMATION_DURATION_MS = 1200L;
    private static final float BASE_SCALE = 0.0325f;
    private static final float PHASE1_RATIO = 0.4f;
    private static final float PHASE2_FLOAT_UP = 0.18f;
    private static final float PHASE2_DISPLAY_RATIO = 0.25f;

    private static final int[] DAMAGE_PALETTE = new int[]{
            0xFFFFFF, 0xFFB347, 0xFFD166, 0x6EEB83, 0x76D6FF, 0xE19BFF
    };

    private FancyDamageRenderer() {
    }

    public static void addDamageNumber(double damage, Vec3d targetPos, Text text, int color) {
        if (MC.player == null || MC.world == null) {
            return;
        }

        Vec3d randomized = targetPos.add(
                (RANDOM.nextFloat() - 0.5f) * 1.8f,
                (RANDOM.nextFloat() - 0.2f) * 1.4f,
                (RANDOM.nextFloat() - 0.5f) * 1.8f
        );

        DAMAGE_NUMBERS.add(new DamageNumber(damage, randomized, text, color, System.currentTimeMillis()));
    }

    public static void render(MatrixStack matrices, float tickDelta) {
        if (MC.player == null || MC.world == null || MC.getEntityRenderDispatcher().camera == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Iterator<DamageNumber> iterator = DAMAGE_NUMBERS.iterator();
        while (iterator.hasNext()) {
            DamageNumber dn = iterator.next();
            if (now - dn.startTime > ANIMATION_DURATION_MS) {
                iterator.remove();
                continue;
            }
            renderDamageNumber(matrices, dn, now);
        }
    }

    public static void clear() {
        DAMAGE_NUMBERS.clear();
    }

    public static int generatePaletteColor(double damage) {
        if (damage >= 1_000_000) {
            return 0xFFB347;
        }
        return DAMAGE_PALETTE[RANDOM.nextInt(DAMAGE_PALETTE.length)];
    }

    public static Text createCompactDamageText(Text originalText, long damageValue, boolean keepSymbol, boolean criticalGradient) {
        String raw = originalText.getString();
        if (hasCompactSuffix(raw)) {
            return originalText;
        }

        Matcher matcher = DAMAGE_TEXT_PATTERN.matcher(raw);
        if (!matcher.matches()) {
            return originalText;
        }

        boolean isCritical = !matcher.group(1).isEmpty();
        String prefix = keepSymbol ? matcher.group(1) : "";
        String suffix = keepSymbol ? matcher.group(3) : "";

        String compact = damageValue < 1000 ? String.valueOf(damageValue) : CompactDamageNumber.formatDamage(damageValue, 4);
        String display = prefix + compact + prefix;

        if (isCritical && criticalGradient) {
            return gradientText(display, 0xFFE27A, 0xFFEED4).append(Text.literal(suffix).setStyle(originalText.getStyle()));
        }

        int color = extractColor(originalText);
        return Text.literal(display + suffix).setStyle(Style.EMPTY.withColor(color));
    }

    private static void renderDamageNumber(MatrixStack matrices, DamageNumber dn, long now) {
        float progress = (float) (now - dn.startTime) / ANIMATION_DURATION_MS;
        progress = MathHelper.clamp(progress, 0.0f, 1.0f);

        Vec3d currentPos;
        float alpha;
        float scaleAnimation;

        if (progress < PHASE1_RATIO) {
            float phase1Progress = progress / PHASE1_RATIO;
            currentPos = dn.targetPos;

            if (phase1Progress < 0.3f) {
                scaleAnimation = 0.5f + phase1Progress / 0.3f;
            } else if (phase1Progress < 0.6f) {
                scaleAnimation = 1.5f - (phase1Progress - 0.3f) / 0.3f * 0.5f;
            } else {
                scaleAnimation = 1.0f;
            }
            alpha = 1.0f;
        } else {
            float phase2Progress = (progress - PHASE1_RATIO) / (1.0f - PHASE1_RATIO);
            float easedFloat = 1.0f - (1.0f - phase2Progress) * (1.0f - phase2Progress);
            currentPos = dn.targetPos.add(0.0, easedFloat * PHASE2_FLOAT_UP, 0.0);
            scaleAnimation = 1.0f;

            if (phase2Progress < PHASE2_DISPLAY_RATIO) {
                alpha = 1.0f;
            } else {
                float fadeProgress = (phase2Progress - PHASE2_DISPLAY_RATIO) / (1.0f - PHASE2_DISPLAY_RATIO);
                float acceleratedFade = Math.min(1.0f, fadeProgress * 2.0f);
                alpha = 1.0f - acceleratedFade * acceleratedFade;
            }
        }

        Vec3d cameraPos = MC.getEntityRenderDispatcher().camera.getCameraPos();
        double x = currentPos.x - cameraPos.x;
        double y = currentPos.y - cameraPos.y;
        double z = currentPos.z - cameraPos.z;

        float damageScale = computeDamageScale(dn.damage);
        float finalScale = BASE_SCALE * scaleAnimation * damageScale;

        matrices.push();
        matrices.translate(x, y, z);

        float yaw = MC.player.getYaw();
        float pitch = MC.player.getPitch();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        matrices.scale(-finalScale, -finalScale, finalScale);

        int alphaBits = ((int) (alpha * 255.0f) << 24) & 0xFF000000;
        int finalColor = (dn.color & 0x00FFFFFF) | alphaBits;

        Text renderText = dn.text != null ? dn.text : Text.literal(String.valueOf((long) dn.damage));
        TextRenderer textRenderer = MC.textRenderer;
        int width = textRenderer.getWidth(renderText);

        VertexConsumerProvider.Immediate immediate = MC.getBufferBuilders().getEntityVertexConsumers();
        textRenderer.draw(
                renderText,
                -width / 2.0f,
                0,
                finalColor,
                false,
                matrices.peek().getPositionMatrix(),
                immediate,
                TextRenderer.TextLayerType.SEE_THROUGH,
                0,
                LightmapTextureManager.MAX_LIGHT_COORDINATE
        );
        immediate.draw();

        matrices.pop();
    }

    private static float computeDamageScale(double damage) {
        if (damage < 1_000) return 0.65f;
        if (damage < 10_000) return 0.70f;
        if (damage < 100_000) return 0.72f;
        if (damage < 500_000) return 0.78f;
        if (damage < 900_000) return 0.88f;
        if (damage < 1_200_000) {
            float progress = (float) (damage - 900_000) / 300_000f;
            return 0.88f + progress * 0.27f;
        }
        return 1.15f;
    }

    private static boolean hasCompactSuffix(String text) {
        if (text == null || text.isEmpty()) return false;
        String cleaned = text.replaceAll("[^\\d.,kKmMbBtTqQ]", "");
        return COMPACT_SUFFIX_PATTERN.matcher(cleaned).find();
    }

    private static int extractColor(Text text) {
        if (text.getStyle().getColor() != null) {
            return text.getStyle().getColor().getRgb();
        }
        for (Text sibling : text.getSiblings()) {
            if (sibling.getStyle().getColor() != null) {
                return sibling.getStyle().getColor().getRgb();
            }
        }
        return 0xFFFFFF;
    }

    private static MutableText gradientText(String content, int startColor, int endColor) {
        if (content.isEmpty()) {
            return Text.empty();
        }

        MutableText result = Text.empty();
        int length = content.length();
        for (int i = 0; i < length; i++) {
            float ratio = (length == 1) ? 0f : (float) i / (length - 1f);
            int color = ColorGradientUtils.blendColors(startColor, endColor, ratio);
            result.append(Text.literal(content.substring(i, i + 1)).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
        }

        return result;
    }

    private record DamageNumber(double damage, Vec3d targetPos, Text text, int color, long startTime) {}
}