package com.revampes.Fault.modules.impl.render;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.AxolotlEntity;
import net.minecraft.util.math.Box;
import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.utility.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import com.revampes.Fault.utility.Utils;

import java.awt.Color;

public class AxolotlESP extends Module {

    public AxolotlESP() {
        super("AxolotlESP", category.Foraging);
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof AxolotlEntity && isInRange(entity)) {
                renderAxolotlESP(event.getMatrix(), (AxolotlEntity) entity);
            }
        }
    }

    private boolean isInRange(Entity entity) {
        double RANGE = mc.options.getViewDistance().getValue() * 16;
        return mc.player.squaredDistanceTo(entity) <= RANGE * RANGE;
    }

    private void renderAxolotlESP(MatrixStack matrices, AxolotlEntity axolotl) {
        if (!axolotl.getVariant().equals(AxolotlEntity.Variant.LUCY)) {
            return;
        }
        RenderUtils.outlineEntity(matrices, axolotl, Color.PINK, 1.0f);
    }
}
