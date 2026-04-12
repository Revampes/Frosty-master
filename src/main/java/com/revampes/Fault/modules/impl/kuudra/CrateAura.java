package com.revampes.Fault.modules.impl.kuudra;

import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.events.impl.ReceivePacketEvent;
import com.revampes.Fault.mixin.accessor.InGameHudAccessor;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.utility.KuudraUtils;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.GiantEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class CrateAura extends Module {

    private static final double PICKUP_RADIUS = 3.5;
    private static final double PICKUP_RADIUS_SQ = PICKUP_RADIUS * PICKUP_RADIUS;
    private static final double TARGET_NEAR_SPOT_RADIUS_SQ = 16.0;
    private static final double PICKUP_VERTICAL_TOLERANCE = 12.0;
    private static final double TARGET_VERTICAL_TOLERANCE = 10.0;
    private static final int INTERACT_COOLDOWN_TICKS = 20;
    private static final String[] CRATE_MESSAGES = new String[] {
        "Not again!",
        "Head over to the main platform, I will join you when I get a bite!"
    };
    private static final String END_MESSAGE = "Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!";
    private static final String ALREADY_PICKING_UP_MESSAGE = "You are already currently picking up some supplies!";
    private static final int ALREADY_PICKING_UP_COOLDOWN_TICKS = 40;

    private final ButtonSetting enableCrateAura = new ButtonSetting("Enable Crate Aura", true);
    private final ButtonSetting showDebug = new ButtonSetting("Debug Messages", false);

    private int pickingUpCooldown = 0;
    private boolean cratesSpawned = false;

    public CrateAura() {
        super("Crate Aura", category.Kuudra);
        this.registerSetting(enableCrateAura);
        this.registerSetting(showDebug);
    }

    @Override
    public String getDesc() {
        return "Automatically picks up Kuudra supplies around you.";
    }

    @Override
    public void onDisable() {
        cratesSpawned = false;
        pickingUpCooldown = 0;
        super.onDisable();
    }

    @EventHandler
    private void onReceivePacket(ReceivePacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (!(packet instanceof GameMessageS2CPacket chatPacket)) return;

        String text = chatPacket.content().getString().replaceAll("(?i)[§&][0-9A-FK-OR]", "");

        if (text.contains(ALREADY_PICKING_UP_MESSAGE)) {
            pickingUpCooldown = Math.max(pickingUpCooldown, ALREADY_PICKING_UP_COOLDOWN_TICKS);
            if (showDebug.isToggled()) {
                Utils.addChatMessage("§b[CrateAura] §eAlready picking up supplies. Pausing interactions.");
            }
            return;
        }

        for (String line : CRATE_MESSAGES) {
            if (text.contains(line)) {
                cratesSpawned = true;
                return;
            }
        }

        if (text.contains(END_MESSAGE)) {
            cratesSpawned = false;
        }
    }

    @EventHandler
    private void onPreUpdate(PreUpdateEvent event) {
        if (!enableCrateAura.isToggled()) return;

        if (mc.world == null || mc.player == null) {
            cratesSpawned = false;
            pickingUpCooldown = 0;
            return;
        }

        if (!KuudraUtils.isInKuudra()) {
            cratesSpawned = false;
            pickingUpCooldown = 0;
            return;
        }

        if (KuudraUtils.phase != 1) return;
        if (!cratesSpawned && showDebug.isToggled()) {
            Utils.addChatMessage("§b[CrateAura] §7Chat trigger missing; using phase-based fallback.");
        }

        if (hasBlockingTitle()) {
            if (showDebug.isToggled()) {
                Utils.addChatMessage("§b[CrateAura] §7Blocking title detected (don't move). Pausing interactions.");
            }
            return;
        }

        if (pickingUpCooldown > 0) {
            pickingUpCooldown--;
            return;
        }

        Entity target = findClosestCrateTargetInRange();
        if (target == null) return;

        if (interactWith(target)) {
            pickingUpCooldown = INTERACT_COOLDOWN_TICKS;
            if (showDebug.isToggled()) {
                Utils.addChatMessage("§b[CrateAura] §aInteracted with crate target: " + target.getType().toString());
            }
        }
    }

    private Entity findClosestCrateTargetInRange() {
        if (mc.player == null || mc.world == null) return null;

        Vec3d closestSpot = null;
        double closestSpotDistSq = Double.MAX_VALUE;

        for (GiantEntity giant : KuudraUtils.giantZombies) {
            Vec3d spot = getPickupSpot(giant);
            double distSq = squaredHorizontalDistance(mc.player.getX(), mc.player.getZ(), spot.x, spot.z);
            double verticalDelta = Math.abs(mc.player.getY() - spot.y);

            if (distSq <= PICKUP_RADIUS_SQ && verticalDelta <= PICKUP_VERTICAL_TOLERANCE && distSq < closestSpotDistSq) {
                closestSpotDistSq = distSq;
                closestSpot = spot;
            }
        }

        if (closestSpot == null) return null;

        List<Entity> targets = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || entity instanceof GiantEntity) continue;
            if (getTargetPriority(entity) == Integer.MAX_VALUE) continue;

            if (squaredHorizontalDistance(entity.getX(), entity.getZ(), closestSpot.x, closestSpot.z) > TARGET_NEAR_SPOT_RADIUS_SQ) continue;
            if (Math.abs(entity.getY() - closestSpot.y) > TARGET_VERTICAL_TOLERANCE) continue;

            if (squaredHorizontalDistance(entity.getX(), entity.getZ(), mc.player.getX(), mc.player.getZ()) > PICKUP_RADIUS_SQ) continue;
            if (Math.abs(entity.getY() - mc.player.getY()) > PICKUP_VERTICAL_TOLERANCE) continue;

            targets.add(entity);
        }

        if (targets.isEmpty()) return null;

        final Vec3d targetSpot = closestSpot;
        targets.sort(Comparator
            .comparingInt(this::getTargetPriority)
            .thenComparingDouble(entity -> squaredHorizontalDistance(entity.getX(), entity.getZ(), mc.player.getX(), mc.player.getZ()))
            .thenComparingDouble(entity -> squaredHorizontalDistance(entity.getX(), entity.getZ(), targetSpot.x, targetSpot.z)));

        if (showDebug.isToggled()) {
            Entity best = targets.get(0);
            Utils.addChatMessage("§b[CrateAura] §7Best target=" + best.getType() + " priority=" + getTargetPriority(best));
        }

        return targets.get(0);
    }

    private Vec3d getPickupSpot(GiantEntity giant) {
        double rad = Math.toRadians(giant.getYaw() + 130.0);
        double expectedX = giant.getX() + (3.7 * Math.cos(rad));
        double expectedZ = giant.getZ() + (3.7 * Math.sin(rad));
        return new Vec3d(expectedX, 73.0, expectedZ);
    }

    private double squaredHorizontalDistance(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        return dx * dx + dz * dz;
    }

    private int getTargetPriority(Entity entity) {
        if (entity instanceof ZombieEntity zombie) {
            return zombie.getEquippedStack(EquipmentSlot.HEAD).isEmpty() ? 0 : Integer.MAX_VALUE;
        }

        if (entity instanceof ArmorStandEntity stand) {
            boolean hasEquipment = false;
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (!stand.getEquippedStack(slot).isEmpty()) {
                    hasEquipment = true;
                    break;
                }
            }

            if (hasEquipment && !stand.isMarker()) {
                return 1;
            }

            if (!stand.isMarker() && !stand.isInvisible()) {
                return 2;
            }

            if (hasEquipment) {
                return 3;
            }

            if (stand.isInvisible() || stand.isMarker()) {
                return 4;
            }
        }

        return Integer.MAX_VALUE;
    }

    private boolean interactWith(Entity entity) {
        if (mc.getNetworkHandler() == null || mc.player == null) {
            return false;
        }

        Vec3d hitPos = entity.getBoundingBox().getCenter();
        Vec3d localHit = hitPos.subtract(entity.getX(), entity.getY(), entity.getZ());

        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.interactAt(entity, mc.player.isSneaking(), Hand.MAIN_HAND, localHit));
        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.interact(entity, mc.player.isSneaking(), Hand.MAIN_HAND));
        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    private boolean hasBlockingTitle() {
        if (mc.inGameHud == null) return false;

        InGameHudAccessor hudAccessor = (InGameHudAccessor) mc.inGameHud;
        if (hudAccessor.getTitleRemainTicks() <= 0) return false;
        if (hudAccessor.getTitle() == null) return false;

        String titleText = Utils.stripColor(hudAccessor.getTitle().getString()).trim();
        if (titleText.isEmpty()) return false;

        String normalized = titleText
            .replace('\u2019', '\'')
            .toLowerCase(Locale.ROOT);

        return normalized.contains("don't move") || normalized.contains("dont move");
    }
}
