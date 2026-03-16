package com.revampes.Fault.modules.impl.render.blockanimation;

import meteordevelopment.orbit.EventHandler;
import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.utility.BlockAnimationUtils;
import com.revampes.Fault.utility.Utils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;

@Environment(EnvType.CLIENT)
public class BlockAnimation extends Module {
    public BlockAnimation() {
        super("BlockAnimation", category.Render);
    }

    private static boolean isSwinging = false;
    private static Hand swingHand = Hand.MAIN_HAND;
    private static int swingTime = 0;
    private static final int SWING_DURATION = 6;

    @EventHandler
    public void onPreUpdate(PreUpdateEvent event) {
        if (!Utils.nullCheck()) return;
        updateSwing();
    }

    public static void startSwing(Hand hand) {
        if (!ModuleManager.blockAnimation.isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (!Utils.nullCheck()) return;
        if (!BlockAnimationUtils.isPlayerBlockingWithSword(mc.player)) return;

        isSwinging = true;
        swingHand = hand;
        swingTime = 0;
    }

    public static void updateSwing() {
        if (!isSwinging) return;
        swingTime++;
        if (swingTime >= SWING_DURATION) {
            isSwinging = false;
            swingTime = 0;
        }
    }

    public static float getSwingProgress(float partialTicks) {
        if (!isSwinging) return 0.0f;
        return (float)(swingTime + partialTicks) / SWING_DURATION;
    }

    public static boolean isSwinging() {
        return isSwinging;
    }

    public static Hand getSwingHand() {
        return swingHand;
    }
}
