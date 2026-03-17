package com.revampes.Fault.modules.impl.kuudra;

import com.revampes.Fault.events.impl.ReceivePacketEvent;
import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.utility.Utils;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.utility.KuudraUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.GiantEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.util.Hand;
import com.revampes.Fault.mixin.accessor.InGameHudAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class CrateAura extends Module {

    private final ButtonSetting enableCrateAura = new ButtonSetting("Enable Crate Aura", true);
    private final ButtonSetting showDebug = new ButtonSetting("Debug Messages", false);

    private int pickingUpCooldown = 0;
    private int tickCounter = 0;

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
        pickingUpCooldown = 0;
        super.onDisable();
    }

    @EventHandler
    private void onReceivePacket(ReceivePacketEvent event) {
        // Chat packet logic removed. We rely on KuudraUtils.phase now.
    }

    @EventHandler
    private void onPreUpdate(PreUpdateEvent event) {
        if (!enableCrateAura.isToggled() || !KuudraUtils.isInKuudra() || KuudraUtils.phase != 1) return;
        if (mc.world == null || mc.player == null || mc.inGameHud == null) return;

        // Check if there is an active title on screen. If so, stop sending packets
        InGameHudAccessor hudAccessor = (InGameHudAccessor) mc.inGameHud;
        if (hudAccessor.getTitleRemainTicks() > 0 && hudAccessor.getTitle() != null) {
            String titleText = hudAccessor.getTitle().getString();
            // Optional: you can check if it specifically says nothing or something else,
            // but the request is "detect if title in client side is not empty"
            if (!titleText.trim().isEmpty()) {
                if (showDebug.isToggled() && tickCounter % 20 == 0) {
                    Utils.addChatMessage("§b[CrateAura] §cTitle detected ('" + titleText + "'). Pausing interacts.");
                }
                return;
            }
        }

        tickCounter++;

        if (pickingUpCooldown > 0) {
            pickingUpCooldown--;
            return;
        }

        boolean interacted = false;

        List<Entity> interactTargets = new ArrayList<>();

        // 1. Locate the closest active supply crate (GiantEntity) to the player
        GiantEntity closestSupply = null;
        double closestDist = Double.MAX_VALUE;
        
        for (GiantEntity giant : KuudraUtils.giantZombies) {
            // Find the mathematical drop-spot logic that SupplyHelper uses
            double rad = Math.toRadians(giant.getYaw() + 130);
            double expectedX = giant.getX() + (3.7 * Math.cos(rad));
            double expectedZ = giant.getZ() + (3.7 * Math.sin(rad));
            double posY = 73.0; 
            
            double distX = mc.player.getX() - expectedX;
            double distY = mc.player.getY() - posY;
            double distZ = mc.player.getZ() - expectedZ;
            
            // Distance from player to the *actual* interaction waypoint
            double distanceToSpot = Math.sqrt(distX * distX + distY * distY + distZ * distZ);
            
            // If the player is standing within 5.5 blocks of this supply spot
            if (distanceToSpot < 5.5 || mc.player.distanceTo(giant) < 8.0) {
                if (distanceToSpot < closestDist) {
                    closestDist = distanceToSpot;
                    closestSupply = giant;
                }
            }
        }
        
        // 2. If a valid supply is truly nearby, THEN AND ONLY THEN scan for interactable parts
        if (closestSupply != null) {
            double rad = Math.toRadians(closestSupply.getYaw() + 130);
            double expectedX = closestSupply.getX() + (3.7 * Math.cos(rad));
            double expectedZ = closestSupply.getZ() + (3.7 * Math.sin(rad));
            Vec3d spotPos = new Vec3d(expectedX, 73.0, expectedZ);

            // Do NOT add the GiantEntity. The giant is just a marker, not the interactable supply!
            
            for (Entity e : mc.world.getEntities()) {
                // Must be physically right on top of the supply waypoint coordinate
                if (e.squaredDistanceTo(spotPos) > 30.0) continue; 
                
                if (e instanceof ArmorStandEntity stand) {
                    boolean hasSupply = false;
                    for (EquipmentSlot slot : EquipmentSlot.values()) {
                        if (!stand.getEquippedStack(slot).isEmpty()) hasSupply = true;
                    }
                    if (hasSupply || stand.isInvisible() || stand.isMarker()) {
                        interactTargets.add(stand);
                    }
                } else if (e instanceof ZombieEntity z && !(e instanceof GiantEntity)) {
                    if (z.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
                        interactTargets.add(z);
                    }
                }
            }
        }

        if (!interactTargets.isEmpty()) {
            // Sort by distance to ensure we try the closest valid crate interactable
            interactTargets.sort((e1, e2) -> Double.compare(e1.distanceTo(mc.player), e2.distanceTo(mc.player)));
            
            // We only ever want to click ONE component per cooldown cycle
            Entity target = interactTargets.get(0);

            if (showDebug.isToggled()) {
                Utils.addChatMessage("§b[CrateAura] §aClicking 1 valid crate target (filtered from " + interactTargets.size() + ")");
            }

            interactWith(target);

            // Randomize cooldown to roughly 4-5 seconds (80-100 ticks) so we don't spam while the item is already being picked up
            pickingUpCooldown = 80 + (int)(Math.random() * 21);
        }
    }

    private void interactWith(Entity entity) {
        if (mc.getNetworkHandler() != null && mc.player != null) {
            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.interact(entity, mc.player.isSneaking(), Hand.MAIN_HAND));
            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.interactAt(entity, mc.player.isSneaking(), Hand.MAIN_HAND, new Vec3d(entity.getX(), entity.getY(), entity.getZ())));
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }
}
