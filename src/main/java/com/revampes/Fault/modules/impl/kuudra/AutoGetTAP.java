package com.revampes.Fault.modules.impl.kuudra;

import com.aftertime.ratallofyou.utils.HotbarSwapUtils;
import com.revampes.Fault.events.impl.ReceivePacketEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.KuudraUtils;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;

import java.util.Locale;
import java.util.regex.Pattern;

public class AutoGetTAP extends Module {

    private static final Pattern RUN_START_REGEX = Pattern.compile("^\\[NPC\\] Elle: (Okay adventurers, I will go and fish up Kuudra!|Head over to the main platform, I will join you when I get a bite!)$");
    private static final String TAP_ID = "TOXIC_ARROW_POISON";
    private static final String TAP_NAME_TOKEN = "toxic arrow poison";
    private static final String GFS_TAP_ID = "toxic_arrow_poison";
    private static final long RUN_START_DEDUP_MS = 15000L;

    private final SliderSetting tapNumber = new SliderSetting("No. of TAPs", "", 32, 1, 64, 1);

    private long lastRunStartHandledAt = 0L;

    public AutoGetTAP() {
        super("Auto Get TAP", category.Kuudra);
        this.registerSetting(tapNumber);
    }

    @Override
    public String getDesc() {
        return "Automatically does gfs for TAPs when the Kuudra run starts.";
    }

    @EventHandler
    private void onReceivePacket(ReceivePacketEvent event) {
        if (!KuudraUtils.isInKuudra() || mc.player == null || mc.player.networkHandler == null) return;

        Packet<?> packet = event.getPacket();
        if (!(packet instanceof GameMessageS2CPacket chatPacket)) return;

        String text = chatPacket.content().getString().replaceAll("(?i)[\\u00A7&][0-9A-FK-OR]", "");
        if (!RUN_START_REGEX.matcher(text).matches()) return;

        long now = System.currentTimeMillis();
        if (now - lastRunStartHandledAt < RUN_START_DEDUP_MS) return;

        int targetTapCount = Math.max(1, (int) tapNumber.getInput());
        int currentTapCount = countTapInInventory();
        int missingTapCount = targetTapCount - currentTapCount;
        if (missingTapCount <= 0) return;

        mc.player.networkHandler.sendChatCommand("gfs " + GFS_TAP_ID + " " + missingTapCount);
        lastRunStartHandledAt = now;
    }

    private int countTapInInventory() {
        if (mc.player == null) {
            return 0;
        }

        int total = 0;
        for (int slot = 0; slot < mc.player.getInventory().size(); slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (isTapStack(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private boolean isTapStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        String skyblockId = HotbarSwapUtils.getSkyblockID(stack);
        if (isTapId(skyblockId)) {
            return true;
        }

        String parsedFromStackString = Utils.getCustomDataIId(stack.toString());
        if (isTapId(parsedFromStackString)) {
            return true;
        }

        String parsedFromComponents = Utils.getCustomDataIId(stack.getComponents().toString());
        if (isTapId(parsedFromComponents)) {
            return true;
        }

        String cleanName = Utils.stripColor(stack.getName().getString()).toLowerCase(Locale.ROOT);
        return cleanName.contains(TAP_NAME_TOKEN);
    }

    private boolean isTapId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return normalizeSkyblockId(id).equals(TAP_ID);
    }

    private String normalizeSkyblockId(String id) {
        if (id == null) {
            return "";
        }
        return id.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }
}
