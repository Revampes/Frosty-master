package com.revampes.AfterTimeFault.modules.impl.render;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import com.revampes.AfterTimeFault.events.impl.Render3DEvent;
import com.revampes.AfterTimeFault.modules.Module;
import com.revampes.AfterTimeFault.modules.ModuleManager;
import com.revampes.AfterTimeFault.modules.impl.other.AntiBot;
import com.revampes.AfterTimeFault.settings.impl.ButtonSetting;
import com.revampes.AfterTimeFault.utility.RenderUtils;
import com.revampes.AfterTimeFault.utility.Utils;

import java.awt.*;

public class PlayerESP extends Module {

    private ButtonSetting expand, fill;

    private static double RANGE;

    public PlayerESP() {
        super("PlayerESP", category.Render);

        this.registerSetting(expand = new ButtonSetting("Expand", true));
        this.registerSetting(fill = new ButtonSetting("Fill", false));
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }

        RANGE = mc.options.getViewDistance().getValue() * 16;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || AntiBot.isBot(player)) continue;

            if (mc.player.squaredDistanceTo(player) > RANGE * RANGE) continue;

            renderPlayerESP(event.getMatrix(), player, event.getDelta());
        }
    }

    private void renderPlayerESP(MatrixStack matrices, PlayerEntity player, float partialTicks) {
        double x = MathHelper.lerp(partialTicks, player.lastRenderX, player.getX());
        double y = MathHelper.lerp(partialTicks, player.lastRenderY, player.getY());
        double z = MathHelper.lerp(partialTicks, player.lastRenderZ, player.getZ());

        Box box = player.getBoundingBox().offset(x - player.getX(), y - player.getY(), z - player.getZ());
        if (expand.isToggled()) {
            box = box.expand(0.1, 0.1, 0.1);
        }

        int colorValue = Utils.getColorFromEntity(player);
        Color color = colorValue != -1 ?
                new Color(colorValue) :
                new Color(255, 255, 255, 150);

        if (fill.isToggled()) {
            RenderUtils.drawBoxFilled(matrices, box, new Color(color.getRed(), color.getGreen(), color.getBlue(), 50));
        }

        RenderUtils.drawBox(matrices, box, color, 2.0);
    }
}
