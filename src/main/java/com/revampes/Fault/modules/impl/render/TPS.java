package com.revampes.Fault.modules.impl.render;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import com.revampes.Fault.events.impl.ReceivePacketEvent;
import com.revampes.Fault.events.impl.Render2DEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.SelectSetting;
import com.revampes.Fault.utility.MathUtils;
import com.revampes.Fault.utility.Theme;
import com.revampes.Fault.utility.Utils;

import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;

public class TPS extends Module {

    private SelectSetting color;

    private String[] colors = new String[] {"Rainbow", "Cherry", "Cotton candy", "Flare", "Flower", "Gold", "Grayscale", "Royal", "Sky", "Vine"};

    private final ArrayDeque<Float> tpsResult = new ArrayDeque<>(20);
    private long time;
    private long tickTime;
    private float tps, SCALE;
    private int x, y, strColor;
    public TPS() {
        super("TPS", category.Render);

        this.registerSetting(color = new SelectSetting("Color", 0, colors));
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }
        double autoScale = Math.max(1, Math.floor(mc.getWindow().getWidth() / 640.0));
        SCALE = (float) (mc.options.getGuiScale().getValue() > 0 ?
                mc.options.getGuiScale().getValue() : autoScale);

        Matrix3x2fStack matrices = event.drawContext.getMatrices();

        matrices.pushMatrix();
        x = 5;
        y = mc.getWindow().getScaledHeight() - 12;
        event.drawContext.drawText(mc.textRenderer, "TPS: " + Math.round(getTPS()), x, y, strColor, true);
        matrices.popMatrix();
    }

    @Override
    public void onUpdate() {
        strColor = getCurrentColor();
    }

    public float getTPS() {
        return round2(tps);
    }

    public float getTPS2() {
        return round2(20.0f * ((float) tickTime / 1000f));
    }

    public float getTPSFactor() {
        return (float) tickTime / 1000f;
    }

    public static float round2(double value) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.floatValue();
    }

    @EventHandler
    public void onPacketReceive(ReceivePacketEvent event) {
        if (event.getPacket() instanceof WorldTimeUpdateS2CPacket) {
            if (time != 0L) {
                tickTime = System.currentTimeMillis() - time;

                if (tpsResult.size() > 20)
                    tpsResult.poll();

                tpsResult.add(20.0f * (1000.0f / (float) (tickTime)));

                float average = 0.0f;

                for (Float value : tpsResult) average += MathUtils.clamp(value, 0f, 20f);

                tps = average / (float) tpsResult.size();
            }
            time = System.currentTimeMillis();
        }
    }

    private int getCurrentColor() {
        int selectedIndex = (int) color.getValue();
        if (selectedIndex == 0) {
            return Theme.getChroma(1, 0);
        }
        Theme theme = Theme.values()[selectedIndex];
        return theme.getAnimatedColor(0, 255, 0.0002);
    }
}
