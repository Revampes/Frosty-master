package com.revampes.Fault.modules.impl.kuudra;

import com.revampes.Fault.events.impl.ReceivePacketEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.utility.KuudraUtils;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.mob.GiantEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.util.math.BlockPos;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Priority extends Module {

    private final ButtonSetting showCratePriority = new ButtonSetting("Show Crate Priority", false);
    private final ButtonSetting advanced = new ButtonSetting("Advanced Mode", false);

    private static final Pattern PARTY_REGEX = Pattern.compile("^Party > (\\[[^\\]]*\\])? ?(\\w{1,16}): No ?(Triangle|X|Equals|Slash|xCannon|Square|Shop)!$");
    private static final Pattern PRE_REGEX = Pattern.compile("^\\[NPC\\] Elle: Head over to the main platform, I will join you when I get a bite!$");
    private static final Pattern START_REGEX = Pattern.compile("^\\[NPC\\] Elle: Not again!$");

    private Supply preSpot = Supply.None;
    public Supply missing = Supply.None;

    public Priority() {
        super("Priority", category.Kuudra);
        this.registerSetting(showCratePriority);
        this.registerSetting(advanced);
    }

    @Override
    public String getDesc() {
        return "Alerts the party if a pre spot is missing.";
    }

    @EventHandler
    private void onReceivePacket(ReceivePacketEvent event) {
        if (!KuudraUtils.isInKuudra()) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof GameMessageS2CPacket chatPacket) {
            String text = chatPacket.content().getString().replaceAll("(?i)[§&][0-9A-FK-OR]", "");

            if (PRE_REGEX.matcher(text).matches()) {
                if (mc.player == null) return;
                BlockPos playerLocation = mc.player.getBlockPos();
                
                if (Math.sqrt(Supply.Triangle.getPickUpSpot().getSquaredDistance(playerLocation)) < 15.0) preSpot = Supply.Triangle;
                else if (Math.sqrt(Supply.X.getPickUpSpot().getSquaredDistance(playerLocation)) < 30.0) preSpot = Supply.X;
                else if (Math.sqrt(Supply.Equals.getPickUpSpot().getSquaredDistance(playerLocation)) < 15.0) preSpot = Supply.Equals;
                else if (Math.sqrt(Supply.Slash.getPickUpSpot().getSquaredDistance(playerLocation)) < 15.0) preSpot = Supply.Slash;
                else preSpot = Supply.None;

                String msg = preSpot == Supply.None ? "§cDidn't register your pre-spot because you didn't get there in time." : "Pre-spot: " + preSpot.name();
                Utils.addChatMessage(msg);
            } else if (START_REGEX.matcher(text).matches()) {
                if (preSpot == Supply.None) return;
                
                boolean second = false;
                boolean pre = false;
                String msg = "";
                
                for (GiantEntity supply : KuudraUtils.giantZombies) {
                    BlockPos supplyLoc = new BlockPos((int) supply.getX(), 76, (int) supply.getZ());
                    
                    if (Math.sqrt(preSpot.getPickUpSpot().getSquaredDistance(supplyLoc)) < 18.0) pre = true;
                    
                    if (preSpot == Supply.Triangle && Math.sqrt(Supply.Shop.getPickUpSpot().getSquaredDistance(supplyLoc)) < 18.0) second = true;
                    if (preSpot == Supply.X && Math.sqrt(Supply.xCannon.getPickUpSpot().getSquaredDistance(supplyLoc)) < 16.0) second = true;
                    if (preSpot == Supply.Slash && Math.sqrt(Supply.Square.getPickUpSpot().getSquaredDistance(supplyLoc)) < 20.0) second = true;
                }
                
                if (second && pre) return;
                
                if (!pre && preSpot != Supply.None) {
                    msg = "No " + preSpot.name() + "!";
                } else if (!second) {
                    if (preSpot == Supply.Triangle) msg = "No Shop!";
                    else if (preSpot == Supply.X) msg = "No xCannon!";
                    else if (preSpot == Supply.Slash) msg = "No Square!";
                    else return;
                }
                
                if (!msg.isEmpty()) {
                    if (mc.player != null) mc.player.networkHandler.sendChatCommand("pc " + msg);
                }
            } else {
                Matcher matcher = PARTY_REGEX.matcher(text);
                if (matcher.matches()) {
                    String supplyName = matcher.group(3);
                    if (supplyName != null) {
                        try {
                            missing = Supply.valueOf(supplyName);
                            if (!showCratePriority.isToggled()) return;
                            
                            String priorityStr = getCratePriority(missing);
                            if (!priorityStr.isEmpty()) {
                                Utils.addChatMessage("Crate Priority: " + priorityStr);
                            }
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        }
    }

    private String getCratePriority(Supply missing) {
        if (missing == Supply.Shop) {
            if (preSpot == Supply.Triangle || preSpot == Supply.X) return "Go X Cannon";
            if (preSpot == Supply.Equals || preSpot == Supply.Slash) return "Go Square, place on Shop";
        } else if (missing == Supply.Triangle) {
            if (preSpot == Supply.Triangle) return advanced.isToggled() ? "Pull Square and X Cannon. Next: collect Shop" : "Pull Square. Next: collect Shop";
            if (preSpot == Supply.X) return "Go X Cannon";
            if (preSpot == Supply.Equals) return advanced.isToggled() ? "Go Shop" : "Go X Cannon";
            if (preSpot == Supply.Slash) return "Go Square, place on Triangle";
        } else if (missing == Supply.Equals) {
            if (preSpot == Supply.Triangle) return advanced.isToggled() ? "Go Shop" : "Go X Cannon";
            if (preSpot == Supply.X) return "Go X Cannon";
            if (preSpot == Supply.Equals) return advanced.isToggled() ? "Pull Square and X Cannon. Next: collect Shop" : "Pull Square. Next: collect Shop";
            if (preSpot == Supply.Slash) return "Go Square, place on Equals";
        } else if (missing == Supply.Slash) {
            if (preSpot == Supply.Triangle) return "Go Square, place on Slash";
            if (preSpot == Supply.X) return "Go X Cannon";
            if (preSpot == Supply.Equals) return advanced.isToggled() ? "Go Shop" : "Go X Cannon";
            if (preSpot == Supply.Slash) return advanced.isToggled() ? "Pull Square and X Cannon. Next: collect Shop" : "Pull Square. Next: collect Shop";
        } else if (missing == Supply.Square) {
            if (preSpot == Supply.Triangle || preSpot == Supply.Equals) return "Go Shop";
            if (preSpot == Supply.X || preSpot == Supply.Slash) return "Go X Cannon";
        } else if (missing == Supply.xCannon) {
            if (preSpot == Supply.Triangle || preSpot == Supply.Equals) return "Go Shop";
            if (preSpot == Supply.Slash || preSpot == Supply.X) return "Go Square, place on X Cannon";
        } else if (missing == Supply.X) {
            if (preSpot == Supply.Triangle) return "Go X Cannon";
            if (preSpot == Supply.X) return advanced.isToggled() ? "Pull Square and X Cannon. Next: collect Shop" : "Pull Square. Next: collect Shop";
            if (preSpot == Supply.Equals) return advanced.isToggled() ? "Go Shop" : "Go X Cannon";
            if (preSpot == Supply.Slash) return "Go Square, place on X";
        }
        return "";
    }

    public void reset() {
        preSpot = Supply.None;
        missing = Supply.None;
    }
}