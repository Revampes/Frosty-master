package com.revampes.Fault.utility;

import com.revampes.Fault.Revampes;
import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.events.impl.ReceivePacketEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.GiantEntity;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KuudraUtils {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static final Map<String, Long> freshers = new ConcurrentHashMap<>();
    public static final List<GiantEntity> giantZombies = new CopyOnWriteArrayList<>();
    public static MagmaCubeEntity kuudraEntity = null;
    public static int phase = 0;

    public static final List<ArmorStandEntity> buildingPiles = new CopyOnWriteArrayList<>();
    public static int playersBuildingAmount = 0;
    public static int buildDonePercentage = 0;

    public static int kuudraTier = 0;
    public static boolean inKuudra = false;

    private static final Pattern OWN_FRESH_REGEX = Pattern.compile("^Your Fresh Tools Perk bonus doubles your building speed for the next 10 seconds!$");
    private static final Pattern BUILD_REGEX = Pattern.compile("Building Progress (\\d+)% \\((\\d+) Players Helping\\)");
    private static final Pattern PARTY_FRESH_REGEX = Pattern.compile("^Party > (\\[[^\\]]*\\])? ?(\\w{1,16}): FRESH$");
    private static final Pattern TIER_REGEX = Pattern.compile("Kuudra's Hollow \\(T(\\d)\\)$");
    private static final Pattern PROGRESS_REGEX = Pattern.compile("PROGRESS: (\\d+)%");

    private static boolean registered = false;
    private static int tickCounter = 0;

    public static void init() {
        if (!registered) {
            Revampes.EVENT_BUS.subscribe(KuudraUtils.class);
            registered = true;
        }
    }

    public static boolean isInKuudra() {
        return inKuudra;
    }

    @EventHandler
    private static void onUpdate(PreUpdateEvent event) {
        String area = LocationUtils.getCurrentArea();
        inKuudra = area != null && area.contains("Kuudra");

        if (!inKuudra) {
            reset();
            return;
        }

        tickCounter++;
        if (tickCounter >= 10) {
            tickCounter = 0;
            processEntities();
        }

        long currentTime = System.currentTimeMillis();
        freshers.entrySet().removeIf(entry -> currentTime - entry.getValue() > 10000);
    }

    private static void processEntities() {
        if (mc.world == null) return;
        
        giantZombies.clear();
        buildingPiles.clear();
        kuudraEntity = null;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof GiantEntity giant) {
                if (giant.getMainHandStack().getName().getString().endsWith("Head")) {
                    giantZombies.add(giant);
                }
            } else if (entity instanceof MagmaCubeEntity magmaCube) {
                if (magmaCube.getSize() == 30 && magmaCube.getAttributeBaseValue(EntityAttributes.MAX_HEALTH) == 100000.0) {
                    kuudraEntity = magmaCube;
                }
            } else if (entity instanceof ArmorStandEntity armorStand) {
                String name = armorStand.hasCustomName() ? armorStand.getCustomName().getString() : "";
                
                Matcher progressMatcher = PROGRESS_REGEX.matcher(name);
                if (progressMatcher.matches()) {
                    buildingPiles.add(armorStand);
                }

                if (phase == 2) {
                    Matcher m = BUILD_REGEX.matcher(name);
                    if (m.find()) {
                        try {
                            buildDonePercentage = Integer.parseInt(m.group(1));
                            playersBuildingAmount = Integer.parseInt(m.group(2));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
    }

    @EventHandler
    private static void onReceivePacket(ReceivePacketEvent event) {
        if (!isInKuudra()) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof GameMessageS2CPacket chatPacket) {
            String text = chatPacket.content().getString().replaceAll("(?i)[§&][0-9A-FK-OR]", "");
            
            switch (text) {
                case "[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!":
                case "[NPC] Elle: Head over to the main platform, I will join you when I get a bite!":
                    phase = 1;
                    break;
                case "[NPC] Elle: OMG! Great work collecting my supplies!":
                    phase = 2;
                    break;
                case "[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!":
                    phase = 3;
                    break;
                case "[NPC] Elle: POW! SURELY THAT'S IT! I don't think he has any more in him!":
                    phase = 4;
                    break;
            }

            Matcher partyMatcher = PARTY_FRESH_REGEX.matcher(text);
            if (partyMatcher.find()) {
                String playerName = partyMatcher.group(2);
                if (playerName != null) {
                    freshers.put(playerName, System.currentTimeMillis());
                }
            }

            Matcher ownMatcher = OWN_FRESH_REGEX.matcher(text);
            if (ownMatcher.find()) {
                String name = mc.player != null ? mc.player.getName().getString() : "self";
                freshers.put(name, System.currentTimeMillis());
            }
        } else if (packet instanceof TeamS2CPacket teamPacket) {
            TeamS2CPacket.SerializableTeam team = teamPacket.getTeam().orElse(null);
            if (team != null) {
                String prefix = team.getPrefix().getString();
                String suffix = team.getSuffix().getString();
                String text = (prefix + suffix).replaceAll("(?i)[§&][0-9A-FK-OR]", "");
                
                Matcher tierMatcher = TIER_REGEX.matcher(text);
                if (tierMatcher.find()) {
                    try {
                        kuudraTier = Integer.parseInt(tierMatcher.group(1));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
    }

    public static void reset() {
        playersBuildingAmount = 0;
        buildDonePercentage = 0;
        buildingPiles.clear();
        giantZombies.clear();
        kuudraEntity = null;
        freshers.clear();
        kuudraTier = 0;
        phase = 0;
        if (com.revampes.Fault.modules.ModuleManager.priority != null) {
            com.revampes.Fault.modules.ModuleManager.priority.reset();
        }
    }
}