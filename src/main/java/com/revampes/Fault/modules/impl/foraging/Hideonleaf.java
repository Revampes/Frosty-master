package com.revampes.Fault.modules.impl.hunting;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerRotationS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.interfaces.ICameraOverriddenEntity;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.ColorSetting;
import com.revampes.Fault.settings.impl.SelectSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.RenderUtils;
import com.revampes.Fault.utility.RotationUtils;
import com.revampes.Fault.utility.Rotations;
import com.revampes.Fault.utility.Utils;

import java.awt.*;
import java.util.*;
import java.util.List;

public class Hideonleaf extends Module {

    private ButtonSetting esp, hit, silent, drawLine;
    private SliderSetting offset, range, lineWidth;
    private ColorSetting lineColor;

    public Hideonleaf() {
        super("Shulkers", category.Foraging);

        this.registerSetting(esp = new ButtonSetting("ESP", true));
        this.registerSetting(drawLine = new ButtonSetting("Draw Line", true));
        this.registerSetting(lineWidth = new SliderSetting("Line Width", 2, 1, 10, 0.5));
        this.registerSetting(lineColor = new ColorSetting("Line Color", new Color(0, 255, 0, 204)));
        this.registerSetting(hit = new ButtonSetting("Attack", true));
        this.registerSetting(silent = new ButtonSetting("Silent", true));
        this.registerSetting(offset = new SliderSetting("Height offset", "x", 1.2, 0.8, 1.6, 0.1));
        this.registerSetting(range = new SliderSetting("Range", 2, 10, 2, 12, 0.5));
    }

    @Override
    public String getDesc() {
        return "Shulkers on Galatea";
    }

    @Override
    public void guiUpdate() {
        this.silent.setVisibilityCondition(() -> hit.isToggled());
        this.offset.setVisibilityCondition(() -> hit.isToggled());
        this.range.setVisibilityCondition(() -> hit.isToggled());
        this.drawLine.setVisibilityCondition(() -> esp.isToggled());
        this.lineWidth.setVisibilityCondition(() -> esp.isToggled() && drawLine.isToggled());
        this.lineColor.setVisibilityCondition(() -> esp.isToggled() && drawLine.isToggled());
    }


    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!Utils.nullCheck() || !esp.isToggled()) {
            return;
        }

        Map<String, String> location = Utils.getCurrentLocation();
        if (!Objects.equals(location.get("Area"), "Galatea")) {
            return;
        }


        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ShulkerEntity && isInRange(entity) && isGreenShulker((ShulkerEntity) entity)) {
                renderShulkerESP(event, (ShulkerEntity) entity);
            }
        }
    }

    @EventHandler
    public void onPreUpdate(PreUpdateEvent event) {
        if (!Utils.nullCheck() || !hit.isToggled()) {
            return;
        }

        Map<String, String> location = Utils.getCurrentLocation();
        if (!Objects.equals(location.get("Area"), "Galatea")) {
            return;
        }


        List<ShulkerBulletEntity> bullets = new ArrayList<>();
        List<ShulkerEntity> shulkers = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ShulkerBulletEntity) {
                if (mc.player.distanceTo(entity) <= range.getInputMax()) {
                    bullets.add((ShulkerBulletEntity) entity);
                }
            } else if (entity instanceof ShulkerEntity && isGreenShulker((ShulkerEntity) entity)) {
                if (mc.player.distanceTo(entity) >= range.getInputMin()
                        && mc.player.distanceTo(entity) <= range.getInputMax()) {
                    shulkers.add((ShulkerEntity) entity);
                }
            }
        }


        ShulkerBulletEntity nearestBullet = bullets.stream()
                .min(Comparator.comparingDouble(b -> mc.player.distanceTo(b)))
                .orElse(null);

        ShulkerEntity nearestShulker = shulkers.stream()
                .min(Comparator.comparingDouble(s -> mc.player.distanceTo(s)))
                .orElse(null);

        if (nearestBullet != null && nearestShulker != null) {

            Vec3d shulkerPos = nearestShulker.getEntityPos().add(0, nearestShulker.getHeight() * offset.getInput(), 0);

            float[] angles = RotationUtils.getYawPitchTo(mc.player.getEyePos(), shulkerPos);

            if (!silent.isToggled()) {
                RotationUtils.aimByPos(shulkerPos);
            } else {
                Rotations.setRotate(this, angles[0], angles[1], 5);
            }

            if (mc.player.getStackInHand(Hand.MAIN_HAND).getCustomName() != null && Utils.getFirstLiteral(mc.player.getStackInHand(Hand.MAIN_HAND).getCustomName().toString()).contains("Fishing Net")) {
                rightClick();
            } else {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        } else {
            if (Rotations.rotating) {
                Rotations.cancelRotate(this);
            }
        }
    }

    private boolean isInRange(Entity entity) {
        double RANGE = mc.options.getViewDistance().getValue() * 16;
        return mc.player.squaredDistanceTo(entity) <= RANGE * RANGE;
    }

    private void rightClick() {
        if (mc.interactionManager == null) {
            return;
        }
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
    }

    private boolean isGreenShulker(ShulkerEntity shulker) {
        try {
            return shulker.getColor() != null && shulker.getColor().name().equalsIgnoreCase("green");
        } catch (Exception e) {
            return false;
        }
    }

    private void renderShulkerESP(Render3DEvent event, ShulkerEntity shulker) {
        RenderUtils.outlineEntity(event.getMatrix(), shulker, Color.GREEN, 1.0f);
        if (drawLine.isToggled()) {
            Vec3d crosshair = mc.player.getCameraPosVec(event.getDelta()).add(mc.player.getRotationVec(event.getDelta()).multiply(2.0));
            Vec3d center = shulker.getBoundingBox().getCenter();
            RenderUtils.drawLine(event.getMatrix(), crosshair, center, lineColor.getColor(), lineWidth.getInput());
        }
    }
}
