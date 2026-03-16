package com.revampes.Fault.modules.impl.kuudra;

import com.revampes.Fault.events.impl.ReceivePacketEvent;
import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.ColorSetting;
import com.revampes.Fault.utility.KuudraUtils;
import com.revampes.Fault.utility.Utils;
import com.revampes.Fault.utility.RenderUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.mob.GiantEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SupplyHelper extends Module {

    private final ButtonSetting suppliesWaypoints = new ButtonSetting("Supplies Waypoints", true);
    private final ColorSetting supplyWaypointColor = new ColorSetting("Supply Waypoint Color", new Color(255, 255, 85));
    private final ButtonSetting supplyDropWaypoints = new ButtonSetting("Supply Drop Waypoints", true);
    private final ButtonSetting sendSupplyTime = new ButtonSetting("Send Supply Time", true);
    private final ButtonSetting renderArea = new ButtonSetting("Render Area", true);

    private static final Pattern SUPPLY_PICK_UP_REGEX = Pattern.compile("(?:\\[[^\\]]*\\])? ?(\\w{1,16}) recovered one of Elle's supplies! \\((\\d)/(\\d)\\)");
    private static final Pattern RUN_START_REGEX = Pattern.compile("^\\[NPC\\] Elle: (Okay adventurers, I will go and fish up Kuudra!|Head over to the main platform, I will join you when I get a bite!)$");
    
    private long startRun = 0L;

    public SupplyHelper() {
        super("Supply Helper", category.Kuudra);
        this.registerSetting(suppliesWaypoints);
        this.registerSetting(supplyWaypointColor);
        this.registerSetting(supplyDropWaypoints);
        this.registerSetting(sendSupplyTime);
        this.registerSetting(renderArea);
    }

    @Override
    public String getDesc() {
        return "Provides visual aid for supply drops in Kuudra.";
    }

    @EventHandler
    private void onReceivePacket(ReceivePacketEvent event) {
        if (!KuudraUtils.isInKuudra() || !sendSupplyTime.isToggled()) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof GameMessageS2CPacket chatPacket) {
            String text = chatPacket.content().getString().replaceAll("(?i)[§&][0-9A-FK-OR]", "");

            if (RUN_START_REGEX.matcher(text).matches()) {
                startRun = System.currentTimeMillis();
            }

            Matcher supplyMatcher = SUPPLY_PICK_UP_REGEX.matcher(text);
            if (supplyMatcher.matches()) {
                if (KuudraUtils.phase != 1) return;
                
                String name = supplyMatcher.group(1);
                String current = supplyMatcher.group(2);
                String total = supplyMatcher.group(3);
                
                long elapsed = System.currentTimeMillis() - startRun;
                String timeStr = String.format("%.2fs", elapsed / 1000.0);
                
                Utils.addChatMessage("§6" + name + " §arecovered a supply in " + timeStr + "! §r§8(" + current + "/" + total + ")");
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!KuudraUtils.isInKuudra() || KuudraUtils.phase != 1) return;

        if (suppliesWaypoints.isToggled()) {
            for (GiantEntity giant : KuudraUtils.giantZombies) {
                double rad = Math.toRadians(giant.getYaw() + 130);
                int expectedX = (int) (giant.getX() + (3.7 * Math.cos(rad)));
                int expectedZ = (int) (giant.getZ() + (3.7 * Math.sin(rad)));
                
                BlockPos targetPos = new BlockPos(expectedX, 73, expectedZ);
                
                RenderUtils.drawCustomBeacon(event.getMatrix(), "§ePick Up!", targetPos, supplyWaypointColor.getColor());
            }
        }
    }
}